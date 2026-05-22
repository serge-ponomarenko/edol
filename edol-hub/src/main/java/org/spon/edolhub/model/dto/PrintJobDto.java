package org.spon.edolhub.model.dto;

import lombok.Data;
import org.spon.edolhub.model.entity.PrintJob;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public static PrintJobDto toDto(PrintJob job) {
        PrintJobDto dto = new PrintJobDto();

        dto.setId(job.getId());
        dto.setTaskName(job.getTaskName());
        dto.setStatus(job.getStatus().name());

        dto.setProgress(job.getProgress());
        dto.setCurrentLayer(job.getCurrentLayer());
        dto.setTotalLayers(job.getTotalLayers());

        dto.setStartedAtFormatted(format(job.getStartedAt()));
        dto.setFinishedAtFormatted(format(job.getFinishedAt()));

        dto.setFormattedDuration(job.getFormattedDuration());
        dto.setRemainingTimeFormatted(job.getRemainingTimeFormatted());
        dto.setFormattedEstimatedFinishTime(job.getFormattedEstimatedFinishTime());

        dto.setFilaments(
                job.getJobFilamentUsages().stream()
                        .map(u -> {
                            FilamentUsageDto f = new FilamentUsageDto();
                            f.setVendor(u.getFilament().getVendor().getName());
                            f.setMaterial(u.getFilament().getMaterialType().getName());
                            f.setBrand(u.getFilament().getBrand());
                            f.setColorHex(u.getFilament().getColorHex());
                            f.setUsedGrams(u.getUsedGrams());
                            f.setCost(u.getCost().doubleValue());

                            f.setSpoolUsages(
                                    job.getJobSpoolUsages().stream()
                                            .filter(su ->
                                                    su.getFilamentSpool() != null
                                                            && su.getFilamentSpool().getFilament() != null
                                                            && su.getFilamentSpool().getFilament().getId()
                                                            .equals(u.getFilament().getId())
                                            )
                                            .map(su -> {
                                                SpoolUsageDto spoolUsage = new SpoolUsageDto();
                                                spoolUsage.setSpoolId(su.getFilamentSpool().getId());
                                                spoolUsage.setUsedGrams(su.getUsedGrams());
                                                return spoolUsage;
                                            })
                                            .toList()
                            );
                            return f;
                        })
                        .toList()
        );

        return dto;
    }

    private static String format(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(FORMATTER);
    }

}
