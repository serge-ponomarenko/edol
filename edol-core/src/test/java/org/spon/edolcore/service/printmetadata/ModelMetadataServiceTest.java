package org.spon.edolcore.service.printmetadata;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ModelMetadataService")
class ModelMetadataServiceTest {

    private final ModelMetadataService service = new ModelMetadataService();

    private static Path testZipPath;
    private static final String TEST_ZIP = "src/test/resources/test_model.3mf";

    @BeforeAll
    static void createTestZip() throws Exception {
        testZipPath = Path.of(TEST_ZIP);

        try (OutputStream fos = new FileOutputStream(testZipPath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            ZipEntry entry1 = new ZipEntry("Metadata/project_settings.config");
            zos.putNextEntry(entry1);
            zos.write("{\"key\": \"value\"}".getBytes());
            zos.closeEntry();

            ZipEntry entry2 = new ZipEntry("Metadata/slice_info.config");
            zos.putNextEntry(entry2);
            zos.write("<config><plate><metadata key=\"index\" value=\"1\"/></plate></config>".getBytes());
            zos.closeEntry();

            ZipEntry entry3 = new ZipEntry("Metadata/plate_1.png");
            zos.putNextEntry(entry3);
            zos.write("fake-png-data".getBytes());
            zos.closeEntry();

            ZipEntry entry4 = new ZipEntry("Metadata/top_1.png");
            zos.putNextEntry(entry4);
            zos.write("fake-top-png-data".getBytes());
            zos.closeEntry();

            ZipEntry entry5 = new ZipEntry("Metadata/plate_1.json");
            zos.putNextEntry(entry5);
            zos.write("{}".getBytes());
            zos.closeEntry();
        }
    }

    @AfterAll
    static void cleanupTestZip() throws Exception {
        Files.deleteIfExists(testZipPath);
    }

    @Nested
    @DisplayName("extractMetadata")
    class ExtractMetadata {

        @Test
        @DisplayName("extracts project_settings.config and slice_info.config from ZIP")
        void extractsMetadataFiles(@TempDir Path tempDir) throws Exception {
            Path output = service.extractMetadata(testZipPath, tempDir);

            assertThat(output).isEqualTo(tempDir);
            assertThat(Files.exists(tempDir.resolve("project_settings.config"))).isTrue();
            assertThat(Files.exists(tempDir.resolve("slice_info.config"))).isTrue();
        }

        @Test
        @DisplayName("creates output directory if it does not exist")
        void createsOutputDir(@TempDir Path tempDir) throws Exception {
            Path nestedDir = tempDir.resolve("sub/dir");
            Path output = service.extractMetadata(testZipPath, nestedDir);

            assertThat(Files.exists(output)).isTrue();
            assertThat(Files.exists(output.resolve("project_settings.config"))).isTrue();
        }
    }

    @Nested
    @DisplayName("extractModelImage")
    class ExtractModelImage {

        @Test
        @DisplayName("extracts config and image files for given plate index")
        void extractsModelImageFiles(@TempDir Path tempDir) throws Exception {
            service.extractModelImage(testZipPath, tempDir, 1);

            assertThat(Files.exists(tempDir.resolve("project_settings.config"))).isTrue();
            assertThat(Files.exists(tempDir.resolve("slice_info.config"))).isTrue();
            assertThat(Files.exists(tempDir.resolve("plate_1.png"))).isTrue();
            assertThat(Files.exists(tempDir.resolve("top_1.png"))).isTrue();
            assertThat(Files.exists(tempDir.resolve("plate_1.json"))).isTrue();
        }
    }
}