package org.spon.edolhub.service.spool;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.dto.AllocationGroupDto;
import org.spon.edolhub.model.dto.AllocationItemDto;
import org.spon.edolhub.model.dto.PrintAllocationPreviewDto;
import org.spon.edolhub.model.entity.AllocationStatus;
import org.spon.edolhub.model.entity.PrintAllocationGroup;
import org.spon.edolhub.model.entity.PrintAllocationItem;
import org.spon.edolhub.model.entity.PrintAllocationPreview;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PrintAllocationPreviewMapper {

    public PrintAllocationPreviewDto toDto(
            PrintAllocationPreview preview
    ) {
        PrintAllocationPreviewDto dto = new PrintAllocationPreviewDto();

        dto.setPrintJobId(preview.getPrintJob().getId());
        dto.setFinalized(preview.getFinalized());

        dto.setJobStatus(
                preview.getPrintJob()
                        .getStatus()
                        .name()
        );

        boolean reconciliationRequired =
                preview.getGroups()
                        .stream()
                        .anyMatch(group ->
                                group.getStatus()
                                        != AllocationStatus.RESOLVED
                        );

        dto.setReconciliationRequired(reconciliationRequired);

        dto.setGroups(
                preview.getGroups()
                        .stream()
                        .map(this::toGroupDto)
                        .toList()
        );

        return dto;

    }

    private AllocationGroupDto toGroupDto(
            PrintAllocationGroup group
    ) {
        AllocationGroupDto dto = new AllocationGroupDto();

        dto.setFilamentId(
                group.getFilament() != null
                        ? group.getFilament().getId()
                        : null
        );

        dto.setFilamentName(
                group.getFilament() != null
                        ? group.getFilament().getFullId()
                        : null
        );

        dto.setColorHex(
                group.getFilament() != null
                        ? group.getFilament().getColorHex()
                        : null
        );

        dto.setColorName(
                group.getFilament() != null
                        ? group.getFilament().getColorName()
                        : null
        );

        dto.setStatus(group.getStatus());
        dto.setRequestedGrams(group.getRequestedGrams());
        dto.setAllocatedGrams(group.getAllocatedGrams());
        dto.setMissingGrams(group.getMissingGrams());
        dto.setUserOverridden(group.getUserOverridden());

        dto.setItems(
                group.getItems()
                        .stream()
                        .map(this::toItemDto)
                        .toList()
        );

        return dto;

    }

    private AllocationItemDto toItemDto(
            PrintAllocationItem item
    ) {
        AllocationItemDto dto = new AllocationItemDto();

        dto.setSpoolId(
                item.getSpool() != null
                        ? item.getSpool().getId()
                        : null
        );

        dto.setSpoolName(
                item.getSpool() != null
                        ? item.getSpool().getDisplayName()
                        : null
        );

        dto.setColorHex(
                item.getSpool() != null
                        ? item.getSpool()
                          .getFilament()
                          .getColorHex()
                        : null
        );

        dto.setAllocatedGrams(item.getAllocatedGrams());

        dto.setSpoolRemainingGrams(
                item.getSpool() != null
                        ? item.getSpool()
                          .getWeightRemaining()
                        : null
        );

        dto.setEstimatedCost(
                item.getEstimatedCost() != null
                        ? item.getEstimatedCost()
                          .doubleValue()
                        : null
        );

        dto.setUserSelected(item.getUserSelected());

        return dto;

    }

}
