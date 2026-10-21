package org.spon.edolcore.service.model.metadata;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.spon.edolcore.config.UploadProperties;
import org.spon.edolcore.service.upload.ModelArchiveValidator;
import org.spon.edolcore.service.upload.ModelFileStorage;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModelMetadataService {

    private final ModelFileStorage files;
    private final ModelArchiveValidator archiveValidator;
    private final UploadProperties properties;

    public Path extractMetadata(Path model3mf, Path outputDir) throws Exception {
        return extract(model3mf, outputDir, List.of(
                "Metadata/project_settings.config", "Metadata/slice_info.config"));
    }

    public void extractModelImage(Path model3mf, Path outputDir, int plateIndex) throws Exception {
        if (plateIndex < 0) {
            throw new IllegalArgumentException("Plate index must not be negative");
        }
        extract(model3mf, outputDir, List.of("Metadata/project_settings.config",
                "Metadata/plate_" + plateIndex + ".png", "Metadata/plate_" + plateIndex + ".json",
                "Metadata/top_" + plateIndex + ".png", "Metadata/slice_info.config"));
    }

    private Path extract(Path model, Path outputDir, List<String> parts) throws Exception {
        Path source = files.safeTarget(model.getParent(), model.getFileName().toString());
        List<StagedPart> staged = new ArrayList<>();
        try (ZipFile zip = new ZipFile(source.toFile())) {
            archiveValidator.validate(zip);
            Path directory = files.safeDirectory(outputDir);
            long total = 0;
            for (String name : parts) {
                ZipEntry entry = zip.getEntry(name);
                if (entry == null) {
                    log.error("ZIP Entry not found: {}", name);
                    continue;
                }
                Path target = files.safeTarget(directory, Path.of(name).getFileName().toString());
                Path temporary = Files.createTempFile(directory, ".edol-metadata-", ".tmp");
                staged.add(new StagedPart(temporary, target));
                try (var output = Files.newOutputStream(temporary, LinkOption.NOFOLLOW_LINKS)) {
                    total += archiveValidator.copyEntry(zip, entry, output,
                            Math.min(properties.metadataEntryMaxBytes(), properties.metadataTotalMaxBytes() - total));
                }
            }
            for (StagedPart part : staged) {
                files.safeTarget(part.target().getParent(), part.target().getFileName().toString());
            }
            for (StagedPart part : staged) {
                files.promote(part.temporary(), part.target());
            }
            return directory;
        } catch (ZipException exception) {
            throw ModelArchiveValidator.invalidArchive();
        } finally {
            for (StagedPart part : staged) {
                Files.deleteIfExists(part.temporary());
            }
        }
    }

    private record StagedPart(Path temporary, Path target) {
    }
}
