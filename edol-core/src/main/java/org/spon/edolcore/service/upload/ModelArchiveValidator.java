package org.spon.edolcore.service.upload;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.config.UploadProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.CheckedInputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

@Component
@RequiredArgsConstructor
public class ModelArchiveValidator {

    private static final String CONTENT_TYPES = "[Content_Types].xml";
    private static final String RELATIONSHIPS = "_rels/.rels";
    private static final Pattern METADATA = Pattern.compile(
            "Metadata/(?:(?:project_settings|slice_info)\\.config|(?:plate|top)_\\d{1,10}\\.(?:png|json))");
    private final UploadProperties properties;

    public void validate(ZipFile zip) throws IOException {
        if (zip.size() > properties.archiveMaxEntries()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Archive entry limit exceeded");
        }
        if (zip.getEntry(CONTENT_TYPES) == null || zip.getEntry(RELATIONSHIPS) == null) {
            throw invalidArchive();
        }
        Set<String> names = new HashSet<>();
        long metadataBytes = 0;
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            validateName(entry.getName());
            if (!names.add(entry.getName().toLowerCase(Locale.ROOT))) {
                throw invalidArchive();
            }
            if (!entry.isDirectory() && (METADATA.matcher(entry.getName()).matches()
                    || CONTENT_TYPES.equals(entry.getName()) || RELATIONSHIPS.equals(entry.getName()))) {
                long remaining = properties.metadataTotalMaxBytes() - metadataBytes;
                metadataBytes += copyEntry(zip, entry, OutputStream.nullOutputStream(),
                        Math.min(properties.metadataEntryMaxBytes(), remaining));
            }
        }
    }

    public long copyEntry(ZipFile zip, ZipEntry entry, OutputStream output, long limit) throws IOException {
        if (entry.getSize() > limit) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Metadata size limit exceeded");
        }
        try (var input = new CheckedInputStream(zip.getInputStream(entry), new CRC32())) {
            long bytes = BoundedUpload.copy(input, output, limit);
            if (bytes != entry.getSize() || input.getChecksum().getValue() != entry.getCrc()) {
                throw invalidArchive();
            }
            return bytes;
        } catch (ZipException exception) {
            throw invalidArchive();
        }
    }

    private void validateName(String name) {
        if (name.isEmpty() || name.length() > 1024 || name.startsWith("/")
                || name.contains("\\") || name.contains(":") || name.chars().anyMatch(Character::isISOControl)) {
            throw invalidArchive();
        }
        for (String segment : name.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw invalidArchive();
            }
        }
    }

    public static ResponseStatusException invalidArchive() {
        return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid 3MF package");
    }
}
