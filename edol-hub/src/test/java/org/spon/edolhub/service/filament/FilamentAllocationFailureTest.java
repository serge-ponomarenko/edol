package org.spon.edolhub.service.filament;

import org.junit.jupiter.api.Test;
import org.spon.edol.model.PrinterState;
import org.spon.edolhub.model.entity.PrintJob;
import org.spon.edolhub.repository.FilamentRepository;
import org.spon.edolhub.repository.MaterialTypeRepository;
import org.spon.edolhub.repository.VendorRepository;
import org.spon.edolhub.service.TenantContext;
import org.spon.edolhub.service.spool.PrintAllocationPreviewService;
import org.spon.edolhub.service.spool.PrintAllocationSnapshotService;
import org.spon.edolhub.service.spool.SpoolAllocationService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FilamentAllocationFailureTest {
    @Test
    void unknownTenantMaterialFailsAllocationBeforePreviewCreation() {
        UUID tenantId = UUID.randomUUID();
        TenantContext tenant = mock(TenantContext.class);
        when(tenant.getCurrentTenantId()).thenReturn(tenantId);
        FilamentRepository filaments = mock(FilamentRepository.class);
        MaterialTypeRepository materials = mock(MaterialTypeRepository.class);
        VendorRepository vendors = mock(VendorRepository.class);
        FilamentService catalog = new FilamentService(filaments, materials, vendors, tenant);
        FilamentMatchingService matching = new FilamentMatchingService(catalog);
        SpoolAllocationService spools = mock(SpoolAllocationService.class);
        PrintAllocationPreviewService previews = mock(PrintAllocationPreviewService.class);
        PrintAllocationSnapshotService snapshot = new PrintAllocationSnapshotService(matching, spools, previews);
        PrinterState state = new PrinterState();
        state.setFilaments(List.of(new org.spon.edol.model.Filament(
                5, "profile", "PLA", "#123456", "Vendor", "Vendor PLA", 1.0, 2.0, true, false, -1)));

        assertThatThrownBy(() -> snapshot.createSnapshot(mock(PrintJob.class), state))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot detect material");
        verify(materials).findByTenantIdAndNameIgnoreCase(tenantId, "PLA");
        verifyNoInteractions(spools, previews, vendors);
    }
}
