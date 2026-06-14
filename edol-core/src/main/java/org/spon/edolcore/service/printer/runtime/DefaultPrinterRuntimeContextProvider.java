package org.spon.edolcore.service.printer.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultPrinterRuntimeContextProvider
        implements PrinterRuntimeContextProvider {

    private final PrinterRuntimeRegistry runtimeRegistry;

    @Override
    public PrinterRuntimeContext getContext(UUID printerId) {
        return runtimeRegistry.getOrCreate(printerId);
    }
}