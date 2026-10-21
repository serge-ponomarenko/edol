package org.spon.edolcore.service.upload;

import org.spon.edolcore.config.UploadProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

@Service
public class ModelFileStorage {

    private static final Pattern RESERVED_NAME = Pattern.compile("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]");
    private final UploadProperties properties;
    private final ModelArchiveValidator archiveValidator;
    private final Path root;

    @Autowired
    public ModelFileStorage(UploadProperties properties, ModelArchiveValidator archiveValidator) {
        this(properties, archiveValidator, Path.of("models"));
    }

    public ModelFileStorage(UploadProperties properties, ModelArchiveValidator archiveValidator, Path root) {
        this.properties = properties;
        this.archiveValidator = archiveValidator;
        this.root = root.toAbsolutePath().normalize();
    }

    public StoredModel store(UUID printerId, String fileName, InputStream body) throws IOException {
        validateModelName(fileName);
        Path directory = safeDirectory(root.resolve(printerId.toString()));
        Path target = safeTarget(directory, fileName);
        Path staged = Files.createTempFile(directory, ".edol-upload-", ".tmp");
        try {
            long size;
            try (var output = Files.newOutputStream(staged, LinkOption.NOFOLLOW_LINKS)) {
                size = BoundedUpload.copy(body, output, properties.modelMaxBytes());
            }
            try (var zip = new ZipFile(staged.toFile())) {
                archiveValidator.validate(zip);
            } catch (ZipException exception) {
                throw ModelArchiveValidator.invalidArchive();
            }
            promote(staged, target);
            return new StoredModel(target, size);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    public Path safeDirectory(Path requested) throws IOException {
        Path normalized = requested.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw unsafePath();
        }
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            checkDirectory(root);
        } else {
            Files.createDirectories(root);
            checkDirectory(root);
        }
        Path anchor = root.toRealPath();
        Path current = root;
        for (Path segment : root.relativize(normalized)) {
            if (segment.toString().isEmpty()) {
                continue;
            }
            current = current.resolve(segment);
            try {
                Files.createDirectory(current);
            } catch (FileAlreadyExistsException exception) {
                // Existing directories must pass the same no-link checks as new ones.
            }
            checkDirectory(current);
            if (!current.toRealPath().startsWith(anchor)) {
                throw unsafePath();
            }
        }
        return current;
    }

    public Path safeTarget(Path directory, String fileName) throws IOException {
        Path parent = safeDirectory(directory);
        Path target = parent.resolve(fileName).normalize();
        if (!parent.equals(target.getParent())) {
            throw unsafePath();
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw unsafePath();
        }
        return target;
    }

    public void promote(Path staged, Path target) throws IOException {
        safeTarget(target.getParent(), target.getFileName().toString());
        Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private void checkDirectory(Path path) throws IOException {
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()) {
            throw unsafePath();
        }
        Path parent = path.getParent();
        if (parent != null && !path.toRealPath().equals(parent.toRealPath().resolve(path.getFileName()))) {
            throw unsafePath();
        }
    }

    private void validateModelName(String name) {
        if (name == null || name.isBlank() || name.length() > 200 || name.startsWith(".")
                || name.endsWith(" ") || name.endsWith(".") || name.chars().anyMatch(Character::isISOControl)
                || name.chars().anyMatch(character -> "\\/:\"<>|?*".indexOf(character) >= 0)
                || RESERVED_NAME.matcher(name.split("\\.", 2)[0].strip().toUpperCase(Locale.ROOT)).matches()) {
            throw unsafePath();
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".3mf")) {
            throw ModelArchiveValidator.invalidArchive();
        }
    }

    private ResponseStatusException unsafePath() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsafe model path");
    }

    public record StoredModel(Path path, long bytes) {
    }
}
