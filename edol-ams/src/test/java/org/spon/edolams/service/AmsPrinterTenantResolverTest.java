package org.spon.edolams.service;

import org.junit.jupiter.api.Test;
import org.spon.edol.deployment.DeploymentMode;
import org.spon.edolams.config.AmsPrinterTenantProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmsPrinterTenantResolverTest {

    @Test
    void opensOnlyTheMappedTenantForAPrinter() {
        UUID printerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        AmsTenantContext context = new AmsTenantContext();
        AmsPrinterTenantResolver resolver = resolver(context, printerId, tenantId);

        resolver.withPrinter(printerId, () -> assertThat(context.currentTenantId()).isEqualTo(tenantId));

        AtomicBoolean downstreamWorkRan = new AtomicBoolean();

        UUID unknownPrinter = UUID.randomUUID();
        assertThatThrownBy(() -> resolver.withPrinter(unknownPrinter, () -> {
            downstreamWorkRan.set(true);
        }))
                .isInstanceOf(UnmappedAmsPrinterException.class);
        assertThat(downstreamWorkRan).isFalse();
    }

    @Test
    void rejectsEnvelopeWhoseTenantDoesNotOwnTheMappedPrinter() {
        UUID printerId = UUID.randomUUID();
        AmsPrinterTenantResolver resolver = resolver(new AmsTenantContext(), printerId, UUID.randomUUID());

        UUID foreignTenant = UUID.randomUUID();
        assertThatThrownBy(() -> resolver.openForEnvelope(printerId, foreignTenant))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startsWithNoPrintersAndRejectsAllUnknownPrintersAndEnvelopes() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(AmsPrinterTenantResolver.class, () -> new AmsPrinterTenantResolver(
                DeploymentMode.SECURE_MULTI_TENANT,
                new AmsPrinterTenantProperties(List.of()),
                new AmsTenantContext()
        ));

        context.refresh();
        AmsPrinterTenantResolver resolver = context.getBean(AmsPrinterTenantResolver.class);
        AtomicBoolean workRan = new AtomicBoolean();
        UUID unknownPrinter = UUID.randomUUID();
        UUID unknownTenant = UUID.randomUUID();
        assertThatThrownBy(() -> resolver.withPrinter(unknownPrinter, () -> workRan.set(true)))
                .isInstanceOf(UnmappedAmsPrinterException.class);
        assertThatThrownBy(() -> resolver.openForEnvelope(unknownPrinter, unknownTenant))
                .isInstanceOf(UnmappedAmsPrinterException.class);
        assertThat(workRan).isFalse();
        context.close();
    }

    private AmsPrinterTenantResolver resolver(AmsTenantContext context, UUID printerId, UUID tenantId) {
        AmsPrinterTenantResolver resolver = new AmsPrinterTenantResolver(
                DeploymentMode.SECURE_MULTI_TENANT,
                new AmsPrinterTenantProperties(List.of(new AmsPrinterTenantProperties.PrinterTenant(printerId, tenantId))),
                context
        );
        resolver.validateMappings();
        return resolver;
    }
}
