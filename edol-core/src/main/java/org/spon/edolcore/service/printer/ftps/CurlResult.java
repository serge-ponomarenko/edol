package org.spon.edolcore.service.printer.ftps;

public record CurlResult(
        int exitCode,
        String stdout,
        String stderr) {
}
