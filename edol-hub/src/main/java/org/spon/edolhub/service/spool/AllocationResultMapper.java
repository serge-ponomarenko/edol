package org.spon.edolhub.service.spool;

import org.spon.edolhub.model.dto.AllocationResult;
import org.spon.edolhub.model.entity.PrintAllocationGroup;
import org.spon.edolhub.model.entity.PrintAllocationItem;
import org.springframework.stereotype.Component;

@Component
public class AllocationResultMapper {

    public PrintAllocationItem toItem(
            PrintAllocationGroup group,
            AllocationResult allocation
    ) {
        return PrintAllocationItem.builder()
                .group(group)
                .spool(allocation.getSpool())
                .allocatedGrams(allocation.getAllocatedGrams())
                .estimatedCost(allocation.getCost())
                .userSelected(false)
                .build();
    }

}