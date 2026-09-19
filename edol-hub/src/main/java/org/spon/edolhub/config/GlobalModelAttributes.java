package org.spon.edolhub.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.Printer;
import org.spon.edolhub.service.PrinterAccessService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.UUID;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private final PrinterAccessService printerAccessService;

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ModelAttribute("printerId")
    public UUID printerId(HttpServletRequest request) {
        String[] parts = request.getRequestURI().split("/");
        for (int index = 0; index < parts.length - 1; index++) {
            if ("printers".equals(parts[index])) {
                try {
                    return UUID.fromString(parts[index + 1]);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    @ModelAttribute("printers")
    public List<Printer> printers() {
        try {
            return printerAccessService.getPrinters();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    @ModelAttribute("selectedPrinter")
    public Printer selectedPrinter(HttpServletRequest request) {
        UUID printerId = printerId(request);
        if (printerId == null) {
            return null;
        }
        try {
            return printerAccessService.getPrinter(printerId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

}
