package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.dto.FilamentUsageDto;
import org.spon.edolhub.model.dto.PrintJobDto;
import org.spon.edolhub.model.dto.SpoolUsageDto;
import org.spon.edolhub.model.entity.JobSpoolUsage;
import org.spon.edolhub.model.entity.PrintJob;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.spon.edolhub.service.spool.PrintAllocationPreviewMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class PrintJobMapper {

    private final PrintAllocationPreviewRepository
            previewRepository;

    private final PrintAllocationPreviewMapper
            allocationPreviewMapper;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public PrintJobDto toDto(
            PrintJob job
    ) {
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
                job.getJobSpoolUsages()
                        .stream()
                        .filter(su ->
                                su.getFilamentSpool() != null
                                        && su.getFilamentSpool()
                                        .getFilament() != null
                        )
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        su -> su.getFilamentSpool()
                                                .getFilament()
                                )
                        )
                        .entrySet()
                        .stream()
                        .map(entry ->
                                toFilamentUsageDto(
                                        entry.getKey(),
                                        entry.getValue()
                                )
                        )
                        .toList()
        );

        previewRepository
                .findByPrintJobId(job.getId())
                .ifPresent(preview ->
                        dto.setAllocationPreview(
                                allocationPreviewMapper
                                        .toDto(preview)
                        )
                );

        return dto;

    }


    private FilamentUsageDto toFilamentUsageDto(
            org.spon.edolhub.model.entity.Filament filament,
            java.util.List<JobSpoolUsage> spoolUsages
    ) {
        FilamentUsageDto dto = new FilamentUsageDto();

        dto.setVendor(filament.getVendor().getName());
        dto.setMaterial(filament.getMaterialType().getName());
        dto.setBrand(filament.getBrand());
        dto.setColorHex(filament.getColorHex());

        dto.setUsedGrams(
                spoolUsages.stream()
                        .mapToDouble(
                                JobSpoolUsage::getUsedGrams
                        )
                        .sum()
        );

        dto.setCost(
                spoolUsages.stream()
                        .map(JobSpoolUsage::getCost)
                        .filter(java.util.Objects::nonNull)
                        .mapToDouble(
                                java.math.BigDecimal::doubleValue
                        )
                        .sum()
        );

        dto.setSpoolUsages(
                spoolUsages.stream()
                        .map(this::toSpoolUsageDto)
                        .toList()
        );

        return dto;
    }

    private SpoolUsageDto toSpoolUsageDto(
            JobSpoolUsage usage
    ) {
        SpoolUsageDto dto = new SpoolUsageDto();
        dto.setSpoolId(usage.getFilamentSpool().getId());
        dto.setUsedGrams(usage.getUsedGrams());
        return dto;
    }

    private String format(
            LocalDateTime dateTime
    ) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(
                FORMATTER
        );
    }

}