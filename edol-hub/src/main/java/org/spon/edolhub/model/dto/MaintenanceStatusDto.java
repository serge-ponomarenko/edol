package org.spon.edolhub.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaintenanceStatusDto {

    private Long id;
    private String name;
    private String description;

    private Integer intervalHours;
    private Integer intervalDays;

    private Integer currentPrinterHours;

    private Integer nextHour;
    private LocalDateTime nextDate;

    private Integer hoursSinceLast;

    private boolean due;

    private boolean neverExecuted;

    private int progressPercent;

}
