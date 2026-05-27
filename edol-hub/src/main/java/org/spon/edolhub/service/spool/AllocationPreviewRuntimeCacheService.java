package org.spon.edolhub.service.spool;

import lombok.Getter;
import lombok.Setter;
import org.spon.edolhub.model.dto.PrintAllocationPreviewDto;
import org.springframework.stereotype.Service;

@Service
@Getter
@Setter
public class AllocationPreviewRuntimeCacheService {

    private PrintAllocationPreviewDto
            currentAllocationPreview;

}