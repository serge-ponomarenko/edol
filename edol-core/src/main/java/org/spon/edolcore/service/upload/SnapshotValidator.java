package org.spon.edolcore.service.upload;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.config.UploadProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Component
@RequiredArgsConstructor
public class SnapshotValidator {

    private final UploadProperties properties;

    public byte[] read(InputStream body) throws IOException {
        var bytes = new ByteArrayOutputStream();
        BoundedUpload.copy(body, bytes, properties.snapshotMaxBytes());
        byte[] image = bytes.toByteArray();
        if (image.length < 4 || image[0] != (byte) 0xff || image[1] != (byte) 0xd8
                || image[image.length - 2] != (byte) 0xff || image[image.length - 1] != (byte) 0xd9) {
            throw invalidJpeg();
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(image))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidJpeg();
            }
            ImageReader reader = readers.next();
            try {
                validateImage(reader, input);
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidJpeg();
        }
        return image;
    }

    private void validateImage(ImageReader reader, MemoryCacheImageInputStream input) throws IOException {
        reader.setInput(input, true, true);
        reader.addIIOReadWarningListener((source, warning) -> {
            throw invalidJpeg();
        });
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (!"JPEG".equalsIgnoreCase(reader.getFormatName()) || width <= 0 || height <= 0
                || (long) width * height > properties.snapshotMaxPixels()) {
            throw invalidJpeg();
        }
        BufferedImage decoded = reader.read(0);
        if (decoded == null) {
            throw invalidJpeg();
        }
        decoded.flush();
    }

    private ResponseStatusException invalidJpeg() {
        return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid or oversized JPEG image dimensions");
    }
}
