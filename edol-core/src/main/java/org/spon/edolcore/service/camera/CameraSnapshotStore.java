package org.spon.edolcore.service.camera;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.CameraSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

@Service
@Slf4j
public class CameraSnapshotStore {

    @Getter
    private volatile CameraSnapshot latest;
    private volatile Path latestSnapshotFile;

    private final LinkedList<CameraSnapshot> history = new LinkedList<>();

    private static final int MAX_HISTORY = 50;

    @Value("${camera.snapshot-dir}")
    private String snapshotDir;

    @Value("${camera.store-snapshots}")
    private boolean storeSnapshots;

    private Path snapshotPath;

    @Setter
    private volatile String currentSessionId = "default";

    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @PostConstruct
    public void init() throws IOException {
        snapshotPath = Paths.get(snapshotDir);

        if (!Files.exists(snapshotPath)) {
            Files.createDirectories(snapshotPath);
        }
    }

    public synchronized void store(byte[] image) {
        CameraSnapshot snap = new CameraSnapshot(image);

        latest = snap;

        history.addFirst(snap);

        if (history.size() > MAX_HISTORY) {
            history.removeLast();
        }

        if (!"default".equals(currentSessionId)) {      // do not store IDLE snapshots
            saveToDisk(snap);
        }
    }

    private void saveToDisk(CameraSnapshot snap) {
        try {
            Path file = snapshotPath.resolve("latest.jpg");
            if (storeSnapshots) {
                String fileName = FILE_TIME.format(
                        snap.getTimestamp().atZone(ZoneId.systemDefault())
                ) + ".jpg";

                Path jobDir = snapshotPath.resolve(currentSessionId);
                Files.createDirectories(jobDir);

                file = jobDir.resolve(fileName);
            }

            Files.write(file, snap.getImage(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);

            latestSnapshotFile = file;

        } catch (Exception e) {
            log.error("Failed to save snapshot: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 3600000)
    public void cleanup() throws IOException {
        if (!storeSnapshots) {
            return;
        }
        long cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000;

        Files.list(snapshotPath).forEach(jobDir -> {
            try {

                if (!Files.isDirectory(jobDir)) return;

                long lastModified = Files.getLastModifiedTime(jobDir).toMillis();

                if (lastModified < cutoff) {
                    deleteDirectory(jobDir);
                    log.info("Deleted old snapshot folder: {}", jobDir);
                }

            } catch (Exception e) {
                log.warn("Cleanup failed for {}", jobDir, e);
            }
        });
    }

    private void deleteDirectory(Path dir) throws IOException {
        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public File getLatestSnapshotFile() {
        if (latestSnapshotFile == null)
            return null;
        return latestSnapshotFile.toFile();
    }

    public List<CameraSnapshot> getHistory() {
        return history;
    }
}
