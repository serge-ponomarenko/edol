package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.Printer;
import org.spon.edolhub.repository.PrinterRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrinterAccessService {

    private final PrinterRepository printerRepository;
    private final TenantContext tenantContext;

    public List<Printer> getPrinters() {
        return printerRepository.findAllByTenantIdOrderByDisplayId(tenantContext.getCurrentTenantId());
    }

    public Printer getPrinter(UUID printerId) {
        return printerRepository.findByIdAndTenantId(printerId, tenantContext.getCurrentTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Printer is not available to the current tenant"));
    }

    public Printer getDefaultPrinter() {
        return getPrinters().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No printers are configured in EDOL Hub"));
    }
}
