package org.spon.edolhub.service.spool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.entity.FilamentSpool;
import org.spon.edolhub.repository.FilamentSpoolRepository;
import org.spon.edolhub.service.TenantContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilamentSpoolServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private FilamentSpoolRepository filamentSpoolRepository;

    @Mock
    private TenantContext tenantContext;

    @InjectMocks
    private FilamentSpoolService service;

    @Test
    void fetchesTheTemplateGraphWithinTheCurrentTenant() {
        List<FilamentSpool.FilamentSpoolStatus> statuses = List.of(FilamentSpool.FilamentSpoolStatus.ACTIVE);
        List<FilamentSpool> expected = List.of(new FilamentSpool());
        when(tenantContext.getCurrentTenantId()).thenReturn(TENANT_ID);
        when(filamentSpoolRepository.findAllByTenantIdAndFiltersWithDetails(
                TENANT_ID, "Vendor", "PETG", statuses
        )).thenReturn(expected);

        List<FilamentSpool> actual = service.findFiltered("Vendor", "PETG", statuses);

        assertThat(actual).isSameAs(expected);
        verify(filamentSpoolRepository).findAllByTenantIdAndFiltersWithDetails(
                TENANT_ID, "Vendor", "PETG", statuses
        );
    }

    @Test
    void treatsBlankFiltersAsNoFilter() {
        List<FilamentSpool.FilamentSpoolStatus> statuses = List.of(FilamentSpool.FilamentSpoolStatus.SEALED);
        when(tenantContext.getCurrentTenantId()).thenReturn(TENANT_ID);
        when(filamentSpoolRepository.findAllByTenantIdAndFiltersWithDetails(
                TENANT_ID, null, null, statuses
        )).thenReturn(List.of());

        service.findFiltered(" ", "", statuses);

        verify(filamentSpoolRepository).findAllByTenantIdAndFiltersWithDetails(
                TENANT_ID, null, null, statuses
        );
    }
}
