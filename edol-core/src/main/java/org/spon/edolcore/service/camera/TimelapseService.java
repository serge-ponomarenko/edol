package org.spon.edolcore.service.camera;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@Slf4j
public class TimelapseService {

    @Value("${camera.snapshot-dir}")
    private String snapshotDir;

    @Value("${camera.store-snapshots}")
    private boolean storeSnapshots;

    public File generate(String jobId) throws Exception {
        if (!storeSnapshots) {
            return null;
        }
        Path dir = Paths.get(snapshotDir, jobId);

        String output = dir.resolve("job_" + jobId + ".mp4").toString();

        log.info("Timelapse dir: {}", dir.toAbsolutePath());
        log.info("Output file: {}", output);

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
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        );

        String line;
        while ((line = reader.readLine()) != null) {
            log.info("[FFMPEG] {}", line);
        }

        int exit = process.waitFor();
        if (exit != 0) {
            throw new RuntimeException("FFmpeg failed");
        }

        return new File(output);
    }
}
