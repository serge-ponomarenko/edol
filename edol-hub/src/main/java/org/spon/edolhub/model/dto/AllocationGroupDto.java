package org.spon.edolhub.model.dto;

import lombok.Data;
import org.spon.edolhub.model.entity.AllocationStatus;

import java.util.List;

@Data
public class AllocationGroupDto {

    private Long filamentId;

    private String filamentName;

    private String colorHex;

    private String colorName;

    private AllocationStatus status;

    private Double requestedGrams;

    private Double allocatedGrams;

    private Double missingGrams;

    private Boolean userOverridden;

    private List<AllocationItemDto> items;

}