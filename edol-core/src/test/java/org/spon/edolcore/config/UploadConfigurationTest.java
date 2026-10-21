package org.spon.edolcore.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class UploadConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(UploadConfiguration.class);

    @Test
    void suppliesTheApprovedDefaultsWithoutModeOrSecurityPrerequisites() {
        runner.run(context -> {
            var properties = context.getBean(UploadProperties.class);
            assertThat(properties.modelMaxBytes()).isEqualTo(256L * 1024 * 1024);
            assertThat(properties.snapshotMaxBytes()).isEqualTo(8 * 1024 * 1024);
            assertThat(properties.metadataEntryMaxBytes()).isEqualTo(16L * 1024 * 1024);
            assertThat(properties.metadataTotalMaxBytes()).isEqualTo(64L * 1024 * 1024);
        });
    }

    @Test
    void bindsCustomLimitsAndRejectsInvalidConfiguration() {
        runner.withPropertyValues("edol-core.upload.model-max-bytes=1024")
                .run(context -> assertThat(context.getBean(UploadProperties.class).modelMaxBytes()).isEqualTo(1024));
        runner.withPropertyValues("edol-core.upload.snapshot-max-bytes=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("edol-core.upload.metadata-total-max-bytes=1")
                .run(context -> assertThat(context).hasFailed());
    }
}
