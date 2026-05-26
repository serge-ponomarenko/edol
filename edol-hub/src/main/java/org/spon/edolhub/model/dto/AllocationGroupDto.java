package org.spon.edolhub.model.dto;

import lombok.Data;
import org.spon.edolhub.model.entity.AllocationStatus;

import java.util.List;

@Data
public class AllocationGroupDto {

    private Long filamentId;

    private String filamentName;

    private String colorHex;

    private AllocationStatus status;

    private Integer requestedGrams;

    private Integer allocatedGrams;

    private Integer missingGrams;

    private Boolean userOverridden;

    private List<AllocationItemDto> items;

}