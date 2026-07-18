package org.spon.edolcore.service.printer.ftps;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CurlFtpsClient {

    private final CurlExecutor executor;

    public List<String> listFiles(FtpsConnection connection) throws IOException, InterruptedException {
        CurlResult result = executor.execute(List.of(
                "curl",
                "--silent",
                "--show-error",
                "--fail",
                "--insecure",
                "--ssl-reqd",
                "--user", connection.username() + ":" + connection.password(),
                "--list-only",
                "ftps://" + connection.host() + ":" + connection.port() + "/"
        ));

        if (result.exitCode() != 0) {
            throw new IOException(result.stderr());
        }

        return result.stdout()
                .lines()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    public void download(FtpsConnection connection,
                         String remoteFile,
                         String localFile) throws IOException, InterruptedException {
        CurlResult result = executor.execute(List.of(
                "curl",
                "--silent",
                "--show-error",
                "--fail",
                "--insecure",
                "--ssl-reqd",
                "--user", connection.username() + ":" + connection.password(),
                "--output", localFile,
                buildUrl(connection, remoteFile)
        ));

        if (result.exitCode() != 0) {
            throw new IOException(result.stderr());
        }
    }

    private String buildUrl(FtpsConnection connection, String remoteFile) {
        String path = Arrays.stream(remoteFile.split("/"))
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8)
                        .replace("+", "%20"))
                .collect(Collectors.joining("/"));

        return "ftps://" + connection.host() + ":" + connection.port() + "/" + path;
    }

}
