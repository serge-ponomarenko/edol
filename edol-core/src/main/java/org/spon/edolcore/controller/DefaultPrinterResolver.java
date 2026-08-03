package org.spon.edolcore.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.printer.management.PrinterManagementService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultPrinterResolver {

    private final PrinterManagementService printerManagementService;

    public UUID resolve() {
        return printerManagementService
                .getDefaultPrinter()
                .getId();
    }
}