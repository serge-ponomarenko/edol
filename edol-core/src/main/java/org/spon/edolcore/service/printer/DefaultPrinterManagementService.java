package org.spon.edolcore.service.printer;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.persistence.printer.PrinterRepository;
import org.spon.edolcore.service.printer.management.exception.PrinterNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultPrinterManagementService implements PrinterManagementService {

    private final PrinterRepository printerRepository;
    private final PrinterConnectionConfigurationRepository configurationRepository;

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
                        new PrinterNotFoundException(
                                "Printer not found: " + printerId
                        ));
    }

    @Override
    public PrinterConnectionConfiguration getConnection(UUID printerId) {
        return configurationRepository.findByPrinterId(printerId)
                .orElseThrow(() ->
                        new PrinterNotFoundException(
                                "Printer not found: " + printerId
                        ));
    }

    @Override
    public List<Printer> getEnabledPrinters() {
        return printerRepository.findByEnabledTrue();
    }

    @Override
    public List<Printer> getPrinters() {
        return printerRepository.findAll();
    }

}