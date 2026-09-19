package org.spon.edolhub.service;
import org.spon.edolhub.model.entity.PrintJob;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class PrintRuntimeStateService {

    private final ConcurrentMap<UUID, PrinterRuntimeState> states = new ConcurrentHashMap<>();

    public PrintJob getCurrentJob(UUID printerId) {
        return state(printerId).currentJob;
    }

    public void setCurrentJob(UUID printerId, PrintJob currentJob) {
        state(printerId).currentJob = currentJob;
    }

    public boolean isAllocationPreviewReady(UUID printerId) {
        return state(printerId).allocationPreviewReady;
    }

    public void setAllocationPreviewReady(UUID printerId, boolean ready) {
        state(printerId).allocationPreviewReady = ready;
    }

    public void clear(UUID printerId) {
        states.remove(printerId);
    }

    private PrinterRuntimeState state(UUID printerId) {
        return states.computeIfAbsent(printerId, ignored -> new PrinterRuntimeState());
    }

    private static final class PrinterRuntimeState {

        private volatile boolean allocationPreviewReady;

        private volatile PrintJob currentJob;
    }

}
