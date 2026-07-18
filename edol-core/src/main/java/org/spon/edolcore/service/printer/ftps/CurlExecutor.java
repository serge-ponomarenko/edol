package org.spon.edolcore.service.printer.ftps;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Slf4j
public class CurlExecutor {

    public CurlResult execute(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(false)
                .start();

        String stdout;
        try (InputStream is = process.getInputStream()) {
            stdout = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        String stderr;
        try (InputStream is = process.getErrorStream()) {
            stderr = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        int exit = process.waitFor();

        log.info("curl exit={}", exit);
        return new CurlResult(exit, stdout, stderr);
    }
}
