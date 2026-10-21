package org.spon.edolcore.service.upload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spon.edolcore.config.UploadProperties;
import org.spon.edolcore.service.model.metadata.ModelMetadataService;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelFileStorageTest {
    @TempDir
    Path temporary;
    private final UUID printer = UUID.randomUUID();

    @Test
    void acceptsPackageAndAtomicallyReplacesOnlyItsPrinterFile() throws Exception {
        var storage = storage(UploadFixtures.limits());
        byte[] body = UploadFixtures.model(Map.of("Metadata/project_settings.config", "{}".getBytes()));
        var first = storage.store(printer, "part.gcode.3mf", new ByteArrayInputStream(body));
        var second = storage.store(printer, "part.gcode.3mf", new ByteArrayInputStream(body));
        assertThat(first.path()).isEqualTo(second.path());
        assertThat(Files.readAllBytes(second.path())).isEqualTo(body);
        assertThat(second.bytes()).isEqualTo(body.length);
        assertNoStagingFiles(second.path().getParent());
    }

    @Test
    void rejectsPortableTraversalAbsolutePathsAndWindowsSpecialNamesBeforeCreatingFiles() {
        var storage = storage(UploadFixtures.limits());
        for (String name : new String[]{"../escape.3mf", "..\\escape.3mf", "/escape.3mf", "C:\\escape.3mf",
                "part.3mf:stream", "CON.3mf", "a\n.3mf", "part.3mf ", "nested/file.3mf"}) {
            assertThatThrownBy(() -> storage.store(printer, name, InputStream.nullInputStream()))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(400));
        }
        assertThat(temporary.resolve("models")).doesNotExist();
    }

    @Test
    void rejectsNonPackagesAndOversizedStreamsWithoutReplacingTheExistingModel() throws Exception {
        var limits = new UploadProperties(512, 8192, 4096, 16384, 100, 1000);
        var storage = storage(limits);
        Path directory = Files.createDirectories(temporary.resolve("models").resolve(printer.toString()));
        Path existing = Files.writeString(directory.resolve("part.3mf"), "previous-model");
        assertThatThrownBy(() -> storage.store(printer, "part.3mf", new ByteArrayInputStream(new byte[513])))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(413));
        for (byte[] body : new byte[][]{"not a ZIP".getBytes(), UploadFixtures.zip(Map.of("other.txt", new byte[0]))}) {
            assertThatThrownBy(() -> storage.store(printer, "part.3mf", new ByteArrayInputStream(body)))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(415));
        }
        assertThat(Files.readString(existing)).isEqualTo("previous-model");
        assertNoStagingFiles(directory);
    }

    @Test
    void removesItsPartialStagingFileWhenTheInputFails() throws Exception {
        var storage = storage(UploadFixtures.limits());
        InputStream failing = new InputStream() {
            private int calls;
            @Override
            public int read() throws IOException {
                if (calls++ < 10) {
                    return 1;
                }
                throw new IOException("Synthetic stream failure");
            }
        };
        assertThatThrownBy(() -> storage.store(printer, "part.3mf", failing)).isInstanceOf(IOException.class);
        Path directory = temporary.resolve("models").resolve(printer.toString());
        assertThat(directory.resolve("part.3mf")).doesNotExist();
        assertNoStagingFiles(directory);
    }

    @Test
    void rejectsLinkedPrinterAndMetadataDirectoriesWithoutWritingOutsideTheRoot() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("models"));
        Path outside = Files.createDirectories(temporary.resolve("outside"));
        UploadFixtures.directoryLink(root.resolve(printer.toString()), outside);
        var storage = storage(UploadFixtures.limits());
        assertThatThrownBy(() -> storage.store(printer, "part.3mf", InputStream.nullInputStream()))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(outside.resolve("part.3mf")).doesNotExist();
        UUID otherPrinter = UUID.randomUUID();
        Path directory = Files.createDirectories(root.resolve(otherPrinter.toString()));
        UploadFixtures.directoryLink(directory.resolve("metadata"), outside);
        assertThatThrownBy(() -> storage.safeDirectory(directory.resolve("metadata")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void boundsActualMetadataInflationAndTotalBeforeReplacingExistingMetadata() throws Exception {
        var limits = new UploadProperties(100_000, 8192, 512, 512, 100, 1000);
        var validator = new ModelArchiveValidator(limits);
        var storage = storage(limits);
        Path directory = Files.createDirectories(temporary.resolve("models").resolve(printer.toString()));
        Path output = Files.createDirectories(directory.resolve("metadata"));
        Path previous = Files.writeString(output.resolve("project_settings.config"), "previous-config");
        var metadata = new ModelMetadataService(storage, validator, limits);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("Metadata/project_settings.config", new byte[300]);
        entries.put("Metadata/slice_info.config", new byte[300]);
        Path archive = Files.write(directory.resolve("part.3mf"), UploadFixtures.model(entries));
        assertThatThrownBy(() -> metadata.extractMetadata(archive, output))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(413));
        assertThat(Files.readString(previous)).isEqualTo("previous-config");
        assertThat(output.resolve("slice_info.config")).doesNotExist();
        assertNoStagingFiles(output);
        entries.put("Metadata/project_settings.config", new byte[513]);
        Files.write(archive, UploadFixtures.model(entries));
        assertThatThrownBy(() -> metadata.extractMetadata(archive, output)).isInstanceOf(ResponseStatusException.class);
        assertThat(Files.readString(previous)).isEqualTo("previous-config");
    }

    @Test
    void extractsOnlyAllowedMetadataAndDoesNotExtractArchiveTraversal() throws Exception {
        var limits = UploadFixtures.limits();
        var storage = storage(limits);
        var model = storage.store(printer, "part.3mf", new ByteArrayInputStream(UploadFixtures.model(Map.of(
                "Metadata/project_settings.config", "{}".getBytes(),
                "Metadata/slice_info.config", "<config/>".getBytes(), "unrelated.txt", "ignore".getBytes()))));
        var metadata = new ModelMetadataService(storage, new ModelArchiveValidator(limits), limits);
        Path output = model.path().getParent().resolve("metadata");
        metadata.extractMetadata(model.path(), output);
        assertThat(Files.readString(output.resolve("project_settings.config"))).isEqualTo("{}");
        assertThat(output.resolve("unrelated.txt")).doesNotExist();
        byte[] traversal = UploadFixtures.model(Map.of("../escaped.txt", new byte[0]));
        assertThatThrownBy(() -> storage.store(printer, "part.3mf", new ByteArrayInputStream(traversal)))
                .isInstanceOf(ResponseStatusException.class);
        assertNoStagingFiles(output);
    }

    @Test
    void rejectsExcessiveEntryCountAndCaseCollidingNames() throws Exception {
        var limits = new UploadProperties(100_000, 8192, 4096, 16384, 2, 1000);
        var storage = storage(limits);
        byte[] excessive = UploadFixtures.model(Map.of("other.txt", new byte[0]));
        assertThatThrownBy(() -> storage.store(printer, "part.3mf", new ByteArrayInputStream(excessive)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(413));
        byte[] colliding = UploadFixtures.model(Map.of("_RELS/.RELS", new byte[0]));
        assertThatThrownBy(() -> storage(UploadFixtures.limits()).store(printer, "part.3mf", new ByteArrayInputStream(colliding)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void refusesRootLinksAndLinksIntoAnotherPrinterEvenInsideTheStorageRoot() throws Exception {
        Path actual = Files.createDirectories(temporary.resolve("actual-models"));
        UploadFixtures.directoryLink(temporary.resolve("models"), actual);
        assertThatThrownBy(() -> storage(UploadFixtures.limits()).safeDirectory(temporary.resolve("models")))
                .isInstanceOf(ResponseStatusException.class);
        Path secondRoot = Files.createDirectories(temporary.resolve("second-models"));
        Path otherPrinter = Files.createDirectory(secondRoot.resolve(UUID.randomUUID().toString()));
        Path link = secondRoot.resolve(printer.toString());
        UploadFixtures.directoryLink(link, otherPrinter);
        var limits = UploadFixtures.limits();
        var storage = new ModelFileStorage(limits, new ModelArchiveValidator(limits), secondRoot);
        assertThatThrownBy(() -> storage.store(printer, "part.3mf", InputStream.nullInputStream()))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(otherPrinter.resolve("part.3mf")).doesNotExist();
    }

    @Test
    void failsClosedWhenAtomicPromotionIsUnavailable() throws Exception {
        var limits = UploadFixtures.limits();
        var storage = new ModelFileStorage(limits, new ModelArchiveValidator(limits), temporary.resolve("models")) {
            @Override
            public void promote(Path staged, Path target) throws IOException {
                throw new java.nio.file.AtomicMoveNotSupportedException(staged.toString(), target.toString(), "Synthetic test");
            }
        };
        Path directory = Files.createDirectories(temporary.resolve("models").resolve(printer.toString()));
        Path previous = Files.writeString(directory.resolve("part.3mf"), "previous-model");
        byte[] body = UploadFixtures.model(Map.of());
        assertThatThrownBy(() -> storage.store(printer, "part.3mf", new ByteArrayInputStream(body)))
                .isInstanceOf(java.nio.file.AtomicMoveNotSupportedException.class);
        assertThat(Files.readString(previous)).isEqualTo("previous-model");
        assertNoStagingFiles(directory);
    }

    @Test
    void rejectsCorruptMetadataCrcBeforeReplacingTheExistingFile() throws Exception {
        var storage = storage(UploadFixtures.limits());
        Path directory = Files.createDirectories(temporary.resolve("models").resolve(printer.toString()));
        Path previous = Files.writeString(directory.resolve("part.3mf"), "previous-model");
        byte[] body = UploadFixtures.model(Map.of());
        for (int offset = 0; offset < body.length - 20; offset++) {
            if (body[offset] == 0x50 && body[offset + 1] == 0x4b && body[offset + 2] == 1 && body[offset + 3] == 2) {
                body[offset + 16] ^= 1; // Corrupt the first central-directory CRC while retaining valid ZIP structure.
                break;
            }
        }
        assertThatThrownBy(() -> storage.store(printer, "part.3mf", new ByteArrayInputStream(body)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(415));
        assertThat(Files.readString(previous)).isEqualTo("previous-model");
        assertNoStagingFiles(directory);
    }

    private ModelFileStorage storage(UploadProperties limits) {
        return new ModelFileStorage(limits, new ModelArchiveValidator(limits), temporary.resolve("models"));
    }

    private void assertNoStagingFiles(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            assertThat(files.map(path -> path.getFileName().toString())).noneMatch(name -> name.startsWith(".edol-"));
        }
    }
}
