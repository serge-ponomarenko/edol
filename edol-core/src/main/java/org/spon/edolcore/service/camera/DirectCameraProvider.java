package org.spon.edolcore.service.camera;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.util.SslUtil;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class DirectCameraProvider implements CameraProvider {

    private static final int PORT = 6000;

    private final PrinterConnectionConfigurationRepository configurationRepository;

    @Override
    public byte[] capture(UUID printerId)
            throws NoSuchAlgorithmException, KeyManagementException, IOException {
        SSLSocketFactory factory = SslUtil.createTrustAllSocketFactory();

        PrinterConnectionConfiguration cfg =
                configuration(printerId);

        try (SSLSocket socket =
                     (SSLSocket) factory.createSocket(
                             cfg.getMqttHost(),
                             PORT
                     )) {
            socket.setSoTimeout(5000); // 5 seconds read timeout to prevent hang
            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm(null);
            socket.setSSLParameters(sslParameters);

            socket.startHandshake();

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            sendAuth(
                    out,
                    cfg.getAccessCode()
            );

            return readFrame(in);
        }
    }

    @Override
    public boolean supports(UUID printerId) {
        return true;
    }

    private void sendAuth(OutputStream out, String accessCode) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(80);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        buf.putInt(0x40);      // payload size
        buf.putInt(0x3000);    // type
        buf.putInt(0);         // flags
        buf.putInt(0);         // reserved

        writeFixedString(buf, "bblp", 32);
        writeFixedString(
                buf,
                accessCode,
                32
        );

        out.write(buf.array());
        out.flush();
    }

    private byte[] readFrame(InputStream in) throws IOException {
        byte[] header = readFully(in, 16);

        ByteBuffer headerBuf =
                ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);

        int payloadSize = headerBuf.getInt();

        return readFully(in, payloadSize);
    }

    private byte[] readFully(InputStream in, int size) throws IOException {
        byte[] data = new byte[size];
        int offset = 0;

        while (offset < size) {

            int read = in.read(data, offset, size - offset);

            if (read == -1)
                throw new EOFException("Stream closed");

            offset += read;
        }

        return data;
    }

    private void writeFixedString(ByteBuffer buf, String value, int size) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);

        int len = Math.min(bytes.length, size);

        buf.put(bytes, 0, len);

        for (int i = len; i < size; i++) {
            buf.put((byte) 0);
        }
    }

    private PrinterConnectionConfiguration configuration(
            UUID printerId
    ) {
        return configurationRepository
                .findByPrinterId(printerId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Configuration not found for printer "
                                        + printerId
                        )
                );
    }
}
