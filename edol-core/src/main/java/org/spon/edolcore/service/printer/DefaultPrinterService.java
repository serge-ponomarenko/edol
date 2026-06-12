package org.spon.edolcore.service.printer;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultPrinterService implements PrinterService {

    private final PrinterRepository printerRepository;

    @Override
    public Printer getDefaultPrinter() {
        return printerRepository
                .findFirstByOrderByDisplayIdAsc()
                .orElseThrow(() ->
                        new IllegalStateException("No printers configured"));
    }

    @Override
    public Printer getPrinter(UUID printerId) {
        return printerRepository.findById(printerId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Printer not found: " + printerId
                        ));
    }
}