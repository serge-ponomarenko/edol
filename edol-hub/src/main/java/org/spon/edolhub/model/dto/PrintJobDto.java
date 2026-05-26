package org.spon.edolhub.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class PrintJobDto {

    private Long id;
    private String taskName;
    private String status;

    private Integer progress;
    private Integer currentLayer;
    private Integer totalLayers;

    private String startedAtFormatted;
    private String finishedAtFormatted;

    private String formattedDuration;
    private String remainingTimeFormatted;
    private String formattedEstimatedFinishTime;

    private List<FilamentUsageDto> filaments;

    private PrintAllocationPreviewDto allocationPreview;

}
