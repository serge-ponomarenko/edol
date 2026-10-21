package org.spon.edolcore.service.upload;

import org.spon.edolcore.config.UploadProperties;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class UploadFixtures {
    private UploadFixtures() {
    }

    public static UploadProperties limits() {
        return new UploadProperties(100_000, 8192, 4096, 16384, 100, 1000);
    }

    public static byte[] model(Map<String, byte[]> metadata) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", "<Types/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put("_rels/.rels", "<Relationships/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.putAll(metadata);
        return zip(entries);
    }

    public static byte[] zip(Map<String, byte[]> entries) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    public static byte[] jpeg() throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "JPEG", bytes);
        image.flush();
        return bytes.toByteArray();
    }

    public static void directoryLink(Path link, Path target) throws IOException, InterruptedException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException exception) {
            if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) {
                throw exception;
            }
            // A junction exercises Windows reparse-point containment without requiring symlink privileges.
            Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), target.toString())
                    .redirectErrorStream(true).start();
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Isolated link fixture creation timed out", exception);
            }
            try (var output = process.getInputStream()) {
                output.transferTo(java.io.OutputStream.nullOutputStream());
            }
            if (process.exitValue() != 0) {
                throw new IOException("Cannot create an isolated link fixture", exception);
            }
        }
    }
}
