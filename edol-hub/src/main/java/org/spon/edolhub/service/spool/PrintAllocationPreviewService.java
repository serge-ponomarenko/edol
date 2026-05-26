package org.spon.edolhub.service.spool;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.dto.AllocationResult;
import org.spon.edolhub.model.entity.*;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PrintAllocationPreviewService {

    private final PrintAllocationPreviewRepository previewRepository;
    private final AllocationResultMapper allocationResultMapper;

    public PrintAllocationPreview createOrUpdate(
            PrintJob job,
            Filament filament,
            Integer amsSlot,
            double requestedGrams,
            List<AllocationResult> allocations
    ) {
        PrintAllocationPreview preview =
                previewRepository
                        .findByPrintJobId(job.getId())
                        .orElseGet(() -> {
                            PrintAllocationPreview p = new PrintAllocationPreview();
                            p.setPrintJob(job);
                            p.setFinalized(false);
                            p.setGroups(new ArrayList<>());
                            return p;
                        });

        PrintAllocationGroup existingGroup =
                preview.getGroups()
                        .stream()
                        .filter(group ->
                                group.getFilament() != null
                                        && group.getFilament().getId()
                                        .equals(filament.getId())
                        )
                        .findFirst()
                        .orElse(null);

        if (
                existingGroup != null
                        && Boolean.TRUE.equals(
                        existingGroup.getUserOverridden()
                )
        ) {
            return preview;
        }

        if (existingGroup != null) {
            preview.getGroups().remove(existingGroup);
        }

        int allocated =
                allocations.stream()
                        .mapToInt(
                                AllocationResult::getAllocatedGrams
                        )
                        .sum();

        int requested = (int) Math.ceil(requestedGrams);

        int missing =
                Math.max(
                        0,
                        requested - allocated
                );

        AllocationStatus status;

        if (allocations.isEmpty()) {
            status = AllocationStatus.MISSING_SPOOL;
        } else if (missing > 0) {
            status = AllocationStatus.PARTIAL;
        } else {
            status = AllocationStatus.RESOLVED;
        }

        PrintAllocationGroup group =
                PrintAllocationGroup.builder()
                        .preview(preview)
                        .filament(filament)
                        .status(status)
                        .requestedGrams(requested)
                        .allocatedGrams(allocated)
                        .missingGrams(missing)
                        .userOverridden(false)
                        .amsSlot(amsSlot)
                        .items(new ArrayList<>())
                        .build();

        for (AllocationResult allocation : allocations) {
            PrintAllocationItem item =
                    PrintAllocationItem.builder()
                            .group(group)
                            .spool(allocation.getSpool())
                            .allocatedGrams(
                                    allocation.getAllocatedGrams()
                            )
                            .estimatedCost(
                                    allocation.getCost()
                            )
                            .userSelected(false)
                            .build();
            group.getItems().add(item);
        }

        preview.getGroups().add(group);

        return previewRepository.save(preview);

    }

    public void updateActualUsage(
            PrintJob job,
            Filament filament,
            double actualUsedGrams,
            List<AllocationResult> allocations
    ) {
        PrintAllocationPreview preview =
                previewRepository
                        .findByPrintJobId(job.getId())
                        .orElseThrow();

        PrintAllocationGroup group =
                preview.getGroups()
                        .stream()
                        .filter(g ->
                                g.getFilament() != null
                                        && g.getFilament()
                                        .getId()
                                        .equals(filament.getId())
                        )
                        .findFirst()
                        .orElseThrow();

        group.setRequestedGrams((int) Math.ceil(actualUsedGrams));
        group.getItems().clear();

        for (AllocationResult allocation
                : allocations) {
            PrintAllocationItem item =
                    allocationResultMapper
                            .toItem(
                                    group,
                                    allocation
                            );
            group.getItems().add(item);
        }

        int allocated =
                allocations.stream()
                        .mapToInt(
                                AllocationResult::getAllocatedGrams
                        )
                        .sum();

        group.setAllocatedGrams(allocated);

        int missing =
                Math.max(
                        0,
                        group.getRequestedGrams()
                                - allocated
                );

        group.setMissingGrams(
                missing
        );

        if (allocations.isEmpty()) {
            group.setStatus(AllocationStatus.MISSING_SPOOL);
        } else if (missing > 0) {
            group.setStatus(AllocationStatus.PARTIAL);
        } else {
            group.setStatus(AllocationStatus.RESOLVED);
        }

        previewRepository.save(preview);

    }

}