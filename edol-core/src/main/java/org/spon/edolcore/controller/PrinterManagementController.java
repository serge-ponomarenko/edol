package org.spon.edolcore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.controller.dto.printer.*;
import org.spon.edolcore.service.printer.PrinterManagementService;
import org.spon.edolcore.service.printer.management.PrinterDecommissionService;
import org.spon.edolcore.service.printer.management.PrinterProvisioningService;
import org.spon.edolcore.service.printer.management.PrinterUpdateService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/printers")
@RequiredArgsConstructor
@Slf4j
public class PrinterManagementController {

    private final PrinterManagementService printerManagementService;
    private final PrinterMapper printerMapper;
    private final PrinterProvisioningService printerProvisioningService;
    private final PrinterUpdateService printerUpdateService;
    private final PrinterDecommissionService printerDecommissionService;

    @GetMapping
    public List<PrinterDto> getPrinters() {
        return printerManagementService.getPrinters()
                .stream()
                .map(printerMapper::toDto)
                .toList();
    }

    @GetMapping("/{printerId}")
    public PrinterDto getPrinter(@PathVariable UUID printerId) {
        return printerMapper.toDto(
                printerManagementService.getPrinter(printerId)
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrinterDto createPrinter(
            @RequestBody CreatePrinterRequest request) {
        return printerMapper.toDto(
                printerProvisioningService.createPrinter(request)
        );
    }

    @PatchMapping("/{printerId}")
    public PrinterDto updatePrinter(
            @PathVariable UUID printerId,
            @RequestBody UpdatePrinterRequest request) {
        return printerMapper.toDto(
                printerUpdateService.updatePrinter(printerId, request)
        );
    }

    @DeleteMapping("/{printerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePrinter(
            @PathVariable UUID printerId) {
        printerDecommissionService.decommissionPrinter(printerId);
    }

    @GetMapping("/{printerId}/connection")
    public PrinterConnectionDto getConnection(@PathVariable UUID printerId) {
        return printerMapper.toDto(
                printerManagementService.getConnection(printerId)
        );
    }

    @PatchMapping("/{printerId}/connection")
    public PrinterConnectionDto updateConnection(
            @PathVariable UUID printerId,
            @RequestBody UpdatePrinterConnectionRequest request) {
        return printerMapper.toDto(
                printerUpdateService.updateConnection(printerId, request)
        );
    }

}
