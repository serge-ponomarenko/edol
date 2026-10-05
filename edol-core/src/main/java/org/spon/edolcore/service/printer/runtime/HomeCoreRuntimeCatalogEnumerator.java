package org.spon.edolcore.service.printer.runtime;

import org.spon.edolcore.persistence.printer.PrinterRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "home")
class HomeCoreRuntimeCatalogEnumerator implements CoreRuntimeCatalogEnumerator {

    private final PrinterRepository printerRepository;

    HomeCoreRuntimeCatalogEnumerator(PrinterRepository printerRepository) {
        this.printerRepository = printerRepository;
    }

    @Override
    public List<CoreRuntimeCatalogEntry> enabledPrinters() {
        return printerRepository.findByEnabledTrue().stream()
                .map(printer -> new CoreRuntimeCatalogEntry(printer.getId(), printer.getTenantId()))
                .toList();
    }
}
