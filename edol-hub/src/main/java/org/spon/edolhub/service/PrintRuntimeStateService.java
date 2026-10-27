package org.spon.edolhub.service;
import org.spon.edolhub.model.entity.PrintJob;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PrintRuntimeStateService {

    private final TransactionalRuntimeMap<PrintJob> jobs = new TransactionalRuntimeMap<>();
    private final TransactionalRuntimeMap<Boolean> allocationReady = new TransactionalRuntimeMap<>();

    public PrintJob getCurrentJob(UUID printerId) {
        return jobs.get(printerId);
    }

    public void setCurrentJob(UUID printerId, PrintJob currentJob) {
        jobs.put(printerId, currentJob);
    }

    public boolean isAllocationPreviewReady(UUID printerId) {
        return Boolean.TRUE.equals(allocationReady.get(printerId));
    }

    public void setAllocationPreviewReady(UUID printerId, boolean ready) {
        allocationReady.put(printerId, ready);
    }

    public void clear(UUID printerId) {
        jobs.put(printerId, null);
        allocationReady.put(printerId, null);
    }

}
