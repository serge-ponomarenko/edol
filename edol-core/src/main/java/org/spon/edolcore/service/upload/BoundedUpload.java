package org.spon.edolcore.service.upload;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class BoundedUpload {

    private BoundedUpload() {
    }

    public static long copy(InputStream input, OutputStream output, long limit) throws IOException {
        byte[] buffer = new byte[8192];
        long written = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count > limit - written) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Upload size limit exceeded");
            }
            output.write(buffer, 0, count);
            written += count;
        }
        return written;
    }
}
