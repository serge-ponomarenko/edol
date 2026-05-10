package org.spon.edolcore.service.printmetadata;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class ModelMetadataService {

    public Path extractMetadata(Path model3mf, Path outputDir) throws Exception {
        Files.createDirectories(outputDir);

        try (ZipFile zip = new ZipFile(model3mf.toFile())) {
            extract(zip, "Metadata/project_settings.config", outputDir);
            extract(zip, "Metadata/slice_info.config", outputDir);
        }

        return outputDir;
    }

    public void extractModelImage(Path model3mf, Path outputDir, int plateIndex) throws Exception {
        Files.createDirectories(outputDir);

        try (ZipFile zip = new ZipFile(model3mf.toFile())) {

            extract(zip, "Metadata/project_settings.config", outputDir);
            extract(zip, "Metadata/plate_" + plateIndex + ".png", outputDir);
            extract(zip, "Metadata/plate_" + plateIndex + ".json", outputDir);
            extract(zip, "Metadata/top_" + plateIndex + ".png", outputDir);
            extract(zip, "Metadata/slice_info.config", outputDir);

        }
    }

    private void extract(ZipFile zip, String entryName, Path outputDir) throws Exception {
        ZipEntry entry = zip.getEntry(entryName);

        if (entry == null) {
            System.out.println("Entry not found: " + entryName);
            return;
        }

        Path outputFile = outputDir.resolve(
                Path.of(entryName).getFileName()
        );

        try (InputStream in = zip.getInputStream(entry)) {
            Files.copy(in, outputFile, StandardCopyOption.REPLACE_EXISTING);
        }

    }
}
