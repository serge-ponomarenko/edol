package org.spon.edolcore.service.printer.ftps;

public record FtpsConnection(
        String host,
        int port,
        String username,
        String password
) {}
