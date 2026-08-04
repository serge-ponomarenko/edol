package org.spon.edolcore.service.camera;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.CameraSnapshot;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.printer.runtime.CameraRuntimeState;
import org.spon.edolcore.service.printer.runtime.PrinterRuntimeQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CameraSnapshotStore {

    private final PrinterRuntimeQueryService runtimeQueryService;

    private static final int MAX_HISTORY = 50;
    private final LogContextFactory logContextFactory;

    @Value("${camera.snapshot-dir}")
    private String snapshotDir;

    @Value("${camera.store-snapshots}")
    private boolean storeSnapshots;

    private Path snapshotPath;

    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @PostConstruct
    public void init() throws IOException {
        snapshotPath = Paths.get(snapshotDir);

        if (!Files.exists(snapshotPath)) {
            Files.createDirectories(snapshotPath);
        }
    }

    public void store(
            UUID printerId,
            byte[] image
    ) {
        CameraSnapshot snap = new CameraSnapshot(image);
        CameraRuntimeState stateRuntime = runtime(printerId);

        synchronized (stateRuntime) {
            stateRuntime.setLatest(snap);
            stateRuntime.getHistory().addFirst(snap);

            if (stateRuntime.getHistory().size() > MAX_HISTORY) {
                stateRuntime.getHistory().removeLast();
            }
        }

        if (!"default".equals(getCurrentSessionId(printerId))) {      // do not store IDLE snapshots
            saveToDisk(
                    printerId,
                    snap
            );
        }
    }

    private void saveToDisk(
            UUID printerId,
            CameraSnapshot snap
    ) {
        try {
            Path printerDir = snapshotPath.resolve(printerId.toString());

            Files.createDirectories(printerDir);

            Path file = printerDir.resolve("latest.jpg");
            if (storeSnapshots) {
                String fileName = FILE_TIME.format(
                        snap.getTimestamp().atZone(ZoneId.systemDefault())
                ) + ".jpg";

                Path jobDir =
                        snapshotPath
                                .resolve(printerId.toString())
                                .resolve(
                                        getCurrentSessionId(printerId)
                                );

                Files.createDirectories(jobDir);

                file = jobDir.resolve(fileName);
            }

            Files.write(file, snap.getImage(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);

            runtime(printerId).setLatestSnapshotFile(file);

        } catch (IOException e) {
            logContextFactory
                    .printer(
                            log.atError(),
                            printerId
                    )
                    .log(
                            "Failed to save snapshot", e
                    );
        }
    }

    @Scheduled(fixedDelay = 3600000)
    public void cleanup() throws IOException {
        if (!storeSnapshots) {
            return;
        }
        long cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000;

        try (var printerDirs = Files.list(snapshotPath)) {
            printerDirs.forEach(printerDir -> {
                try {
                    if (!Files.isDirectory(printerDir))
                        return;

                    try (var sessionDirs = Files.list(printerDir)) {
                        sessionDirs.forEach(sessionDir -> {
                            try {
                                if (!Files.isDirectory(sessionDir))
                                    return;

                                long lastModified =
                                        Files.getLastModifiedTime(sessionDir).toMillis();

                                if (lastModified < cutoff) {
                                    deleteDirectory(sessionDir);

                                    logContextFactory
                                            .system(
                                                    log.atInfo()
                                            )
                                            .log(
                                                    "Deleted old snapshot folder: {}",
                                                    sessionDir
                                            );

                                }
                            } catch (IOException e) {
                                logContextFactory
                                        .system(
                                                log.atWarn()
                                        )
                                        .log(
                                                "Cleanup failed for session directory {}",
                                                sessionDir,
                                                e
                                        );
                            }
                        });
                    }
                } catch (IOException e) {
                    logContextFactory
                            .system(
                                    log.atWarn()
                            )
                            .log(
                                    "Cleanup failed for printer directory {}",
                                    printerDir,
                                    e
                            );
                }
            });
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public File getLatestSnapshotFile(
            UUID printerId
    ) {
        if (runtime(printerId).getLatestSnapshotFile() == null)
            return null;
        return runtime(printerId).getLatestSnapshotFile().toFile();
    }

    public List<CameraSnapshot> getHistory(UUID printerId) {
        return runtime(printerId).getHistory();
    }

    private CameraRuntimeState runtime(UUID printerId) {
        return runtimeQueryService
                .getRuntime(printerId)
                .getContext()
                .getCameraRuntimeState();
    }

    public CameraSnapshot getLatest(UUID printerId) {
        return runtime(printerId).getLatest();
    }

    public void setCurrentSessionId(UUID printerId, String sessionId) {
        runtime(printerId).setCurrentSessionId(sessionId);
    }

    public String getCurrentSessionId(UUID printerId) {
        return runtime(printerId).getCurrentSessionId();
    }
}
