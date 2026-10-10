package org.spon.edolcore.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoreLegacyCompatibilityFilterTest {

    @Test
    void exposesZeroBeforeUseAndCountsAnAcceptedLegacyRequest() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        org.springframework.beans.factory.ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistryProvider = mock();
        when(meterRegistryProvider.getIfAvailable()).thenReturn(meterRegistry);
        CoreLegacyCompatibilityFilter filter = new CoreLegacyCompatibilityFilter(
                new CoreLegacyCompatibilityProperties(true, UUID.randomUUID(), List.of("127.0.0.1/32")),
                new CoreTenantContext(),
                mock(DataSource.class),
                meterRegistryProvider
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/printers/state");
        request.setRemoteAddr("127.0.0.1");

        assertThat(meterRegistry.find("edol.core.legacy_compatibility.requests").counter().count()).isZero();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(meterRegistry.find("edol.core.legacy_compatibility.requests").counter().count()).isEqualTo(1);
    }
}
