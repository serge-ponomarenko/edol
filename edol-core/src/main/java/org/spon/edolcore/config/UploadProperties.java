package org.spon.edolcore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("edol-core.upload")
public record UploadProperties(
        @DefaultValue("268435456") long modelMaxBytes,
        @DefaultValue("8388608") int snapshotMaxBytes,
        @DefaultValue("16777216") long metadataEntryMaxBytes,
        @DefaultValue("67108864") long metadataTotalMaxBytes,
        @DefaultValue("10000") int archiveMaxEntries,
        @DefaultValue("40000000") long snapshotMaxPixels
) {
    public UploadProperties {
        if (modelMaxBytes <= 0 || snapshotMaxBytes <= 0 || snapshotMaxBytes > Integer.MAX_VALUE - 8
                || metadataEntryMaxBytes <= 0 || metadataTotalMaxBytes < metadataEntryMaxBytes
                || archiveMaxEntries <= 0 || snapshotMaxPixels <= 0) {
            throw new IllegalArgumentException("Upload limits must be positive and metadata total must cover one entry");
        }
    }
}
