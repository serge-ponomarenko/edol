package org.spon.edolcore.service;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.exception.FtpsTransferException;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.springframework.stereotype.Service;

import java.io.*;
import java.text.Normalizer;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Setter
public class FtpsService {

    private final PrinterConnectionConfigurationRepository configurationRepository;
    private final PrinterStateService printerStateService;
    private final LogContextFactory logContextFactory;

    public void download(
            UUID printerId,
            String requestedFile,
            String localFile
    ) {
        int maxAttempts = 5;
        long delayMs = 5000;

        Exception lastError = null;

        PrinterState state = printerStateService.getState(printerId);

        PrinterConnectionConfiguration configuration =
                configurationRepository
                        .findByPrinterId(printerId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Printer configuration not found: " + printerId
                                )
                        );

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            FTPSClient ftps = null;

            try {
                ftps = getFtpsClient(configuration);

                logContextFactory
                        .session(
                                log.atInfo(),
                                printerId,
                                state.getSessionId()
                        )
                        .log(
                                "Downloading '{}'. Attempt: {}",
                                requestedFile,
                                attempt
                        );

                downloadModel(ftps, requestedFile, localFile, configuration.getModelDirectory());

                logContextFactory
                        .session(
                                log.atInfo(),
                                printerId,
                                state.getSessionId()
                        )
                        .log(
                                "Model downloaded"
                        );

                safeLogout(ftps);
                safeDisconnect(ftps);

                return;

            } catch (IOException | FtpsTransferException e) {

                lastError = e;

                logContextFactory
                        .session(
                                log.atWarn(),
                                printerId,
                                state.getSessionId()
                        )
                        .log(
                                "FTPS attempt {} failed: {}", attempt, e.getMessage()
                        );

                safeDisconnect(ftps);

                try {
                    Thread.sleep(delayMs * attempt);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new FtpsTransferException("FTPS download interrupted", interruptedException);
                }
            }
        }

        throw new FtpsTransferException("Download failed after retries", lastError);
    }

    private FTPSClient getFtpsClient(PrinterConnectionConfiguration configuration) throws IOException {
        FTPSClient ftps = new FTPSClient(true);

        ftps.setControlEncoding("UTF-8");

        ftps.setConnectTimeout(5000);
        ftps.setDataTimeout(Duration.ofSeconds(30));

        // keep alive
        ftps.setControlKeepAliveTimeout(Duration.ofSeconds(10));
        ftps.setControlKeepAliveReplyTimeout(Duration.ofSeconds(5));

        ftps.setStrictReplyParsing(false);
        ftps.setRemoteVerificationEnabled(false);

        ftps.connect(configuration.getFtpHost(), configuration.getFtpPort());

        if (!ftps.login("bblp", configuration.getAccessCode()))
            throw new FtpsTransferException("FTP login failed");

        ftps.setSoTimeout(30000);

        ftps.execPBSZ(0);
        ftps.execPROT("P");

        return ftps;
    }

    private void downloadModel(
            FTPSClient ftps,
            String requestedFile,
            String localFile,
            String modelDirectory
    ) throws IOException {
        ftps.setFileType(FTP.BINARY_FILE_TYPE);
        ftps.enterLocalPassiveMode();
        ftps.changeWorkingDirectory(modelDirectory);

        String remoteFile = resolveRemoteFile(ftps, requestedFile);

        try (OutputStream output = new FileOutputStream(localFile)) {
            boolean success = ftps.retrieveFile(remoteFile, output);
            if (!success) {
                throw new FtpsTransferException("Download failed: " + remoteFile);
            }
        }

        try (InputStream in = new FileInputStream(localFile)) {
            byte[] header = new byte[2];
            if (in.read(header) != 2 || header[0] != 'P' || header[1] != 'K') {
                throw new FtpsTransferException("Invalid ZIP file: " + remoteFile);
            }
        }
    }

    private String resolveRemoteFile(FTPSClient ftps, String requested) throws IOException {
        FTPFile[] files = ftps.listFiles();

        if (files == null || files.length == 0) {
            throw new FtpsTransferException("No files found on printer");
        }

        String normalizedRequested = normalizeName(requested);

        for (FTPFile file : files) {
            String name = file.getName();

            if (normalizeName(name).equalsIgnoreCase(normalizedRequested)) {
                if (file.getSize() == 0) {
                    throw new FtpsTransferException("Remote file is empty");
                }
                return name;
            }

        }

        throw new FtpsTransferException("No matching model file found on printer");
    }

    private String normalizeName(String name) {
        if (name == null)
            return "";
        return Normalizer.normalize(name, Normalizer.Form.NFKC);
    }

    private void safeLogout(FTPSClient ftps) {
        if (ftps == null) {
            return;
        }
        try {
            ftps.logout();
        } catch (IOException ignored) {
            //Nothing
        }
    }

    private void safeDisconnect(FTPSClient ftps) {
        if (ftps == null || !ftps.isConnected()) {
            return;
        }
        try {
            ftps.disconnect();
        } catch (IOException ignored) {
            //Nothing
        }
    }
}
