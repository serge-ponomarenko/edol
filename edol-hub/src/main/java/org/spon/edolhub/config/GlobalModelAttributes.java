package org.spon.edolhub.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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

    private static final String SELECTED_PRINTER_SESSION_ATTRIBUTE =
            GlobalModelAttributes.class.getName() + ".selectedPrinterId";
    private static final String PRINTER_SELECTION_REQUEST_ATTRIBUTE =
            GlobalModelAttributes.class.getName() + ".printerSelection";

    private final PrinterAccessService printerAccessService;

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ModelAttribute("printerId")
    public UUID printerId(HttpServletRequest request) {
        return printerSelection(request).id();
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
        return printerSelection(request).printer();
    }

    private PrinterSelection printerSelection(HttpServletRequest request) {
        Object cachedSelection = request.getAttribute(PRINTER_SELECTION_REQUEST_ATTRIBUTE);
        if (cachedSelection instanceof PrinterSelection selection) {
            return selection;
        }

        UUID pathPrinterId = printerIdFromPath(request);
        UUID selectedPrinterId = pathPrinterId == null
                ? printerIdFromSession(request)
                : pathPrinterId;
        PrinterSelection selection = resolvePrinterSelection(request, selectedPrinterId, pathPrinterId != null);
        request.setAttribute(PRINTER_SELECTION_REQUEST_ATTRIBUTE, selection);
        return selection;
    }

    private UUID printerIdFromPath(HttpServletRequest request) {
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

    private UUID printerIdFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        Object selectedPrinterId = session.getAttribute(SELECTED_PRINTER_SESSION_ATTRIBUTE);
        if (selectedPrinterId instanceof UUID printerId) {
            return printerId;
        }

        if (selectedPrinterId != null) {
            session.removeAttribute(SELECTED_PRINTER_SESSION_ATTRIBUTE);
        }

        return null;
    }

    private PrinterSelection resolvePrinterSelection(
            HttpServletRequest request,
            UUID printerId,
            boolean selectedFromPath
    ) {
        if (printerId == null) {
            return PrinterSelection.empty();
        }

        try {
            Printer printer = printerAccessService.getPrinter(printerId);
            if (selectedFromPath) {
                request.getSession().setAttribute(SELECTED_PRINTER_SESSION_ATTRIBUTE, printerId);
            }
            return new PrinterSelection(printerId, printer);
        } catch (RuntimeException ignored) {
            if (!selectedFromPath) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.removeAttribute(SELECTED_PRINTER_SESSION_ATTRIBUTE);
                }
            }
            return PrinterSelection.empty();
        }
    }

    private record PrinterSelection(UUID id, Printer printer) {

        private static PrinterSelection empty() {
            return new PrinterSelection(null, null);
        }
    }

}
