package org.spon.edolcore.service.timelapse;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.spon.edolcore.exception.TimelapseGenerationException;
import org.spon.edolcore.service.LogContextFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Slf4j
public class TimelapseService {

    private final LogContextFactory logContextFactory;
    @Value("${camera.snapshot-dir}")
    private String snapshotDir;

    @Value("${camera.store-snapshots}")
    private boolean storeSnapshots;

    public TimelapseService(LogContextFactory logContextFactory) {
        this.logContextFactory = logContextFactory;
    }

    public File generate(UUID printerId, String sessionId) {
        if (!storeSnapshots) {
            return null;
        }
        Path dir = Paths.get(snapshotDir, printerId.toString(), sessionId);

        String output = dir.resolve("job_" + sessionId + ".mp4").toString();

        logContextFactory
                .session(
                        log.atInfo(),
                        printerId,
                        sessionId
                )
                .log(
                        "Timelapse dir: {}. Output file: {}",
                        dir.toAbsolutePath(),
                        output
                );

        ProcessBuilder pb = getProcessBuilder(dir, output);

        try {
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[FFMPEG] {}", line);
                }
            }

            int exit = process.waitFor();
            if (exit != 0) {
                throw new TimelapseGenerationException(sessionId);
            }

            return new File(output);
        } catch (IOException e) {
            throw new TimelapseGenerationException(sessionId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TimelapseGenerationException(sessionId, e);
        }
    }

    private static @NonNull ProcessBuilder getProcessBuilder(Path dir, String output) {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-loglevel", "error",
                "-threads", "2",
                "-framerate", "10",
                "-pattern_type", "glob",
                "-i", dir.resolve("*.jpg").toAbsolutePath().toString(),
                "-vf", "scale=1280:-2",
                "-c:v", "libx264",
                "-crf", "26",
                "-preset", "fast",
                "-pix_fmt", "yuv420p",
                "-movflags", "+faststart",
                output
        );

        pb.redirectErrorStream(true);
        return pb;
    }
}
