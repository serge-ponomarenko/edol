package org.spon.edolcore.service.mqtt;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.persistence.printer.PrinterRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "home")
class HomeCoreEventTenantResolver implements CoreEventTenantResolver {

    private final PrinterRepository printerRepository;

    @Override
    public Optional<UUID> tenantIdForPrinter(UUID printerId) {
        return printerRepository.findById(printerId).map(org.spon.edolcore.persistence.printer.Printer::getTenantId);
    }
}
