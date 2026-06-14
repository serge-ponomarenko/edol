package org.spon.edolcore.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.printer.PrinterService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultPrinterResolver {

    private final PrinterService printerService;

    public UUID resolve() {
        return printerService
                .getDefaultPrinter()
                .getId();
    }
}