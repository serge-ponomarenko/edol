package org.spon.edolhub.service.filament;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.dto.FilamentDeletePreviewDto;
import org.spon.edolhub.model.entity.JobSpoolUsage;
import org.spon.edolhub.repository.FilamentSpoolRepository;
import org.spon.edolhub.repository.JobSpoolUsageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FilamentDeleteService {

    private final FilamentSpoolRepository spoolRepository;
    private final JobSpoolUsageRepository spoolUsageRepository;

    public FilamentDeletePreviewDto preview(Long filamentId) {
        List<FilamentDeletePreviewDto.SpoolInfo> spools =
                spoolRepository.findByFilamentId(filamentId)
                        .stream()
                        .map(s -> new FilamentDeletePreviewDto.SpoolInfo(
                                s.getId(),
                                s.getWeightRemaining(),
                                s.getStatus().name()
                        ))
                        .toList();

        List<FilamentDeletePreviewDto.JobInfo> jobs =
                spoolUsageRepository.findAll()
                        .stream()
                        .filter(su ->
                                su.getFilamentSpool() != null
                                        && su.getFilamentSpool()
                                        .getFilament() != null
                                        && su.getFilamentSpool()
                                        .getFilament()
                                        .getId()
                                        .equals(filamentId)
                        )
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        JobSpoolUsage::getPrintJob
                                )
                        )
                        .entrySet()
                        .stream()
                        .map(entry -> {
                            double usedGrams =
                                    entry.getValue()
                                            .stream()
                                            .mapToDouble(
                                                    org.spon.edolhub.model.entity.JobSpoolUsage::getUsedGrams
                                            )
                                            .sum();

                            return new FilamentDeletePreviewDto.JobInfo(
                                    entry.getKey().getId(),
                                    entry.getKey().getTaskName(),
                                    usedGrams
                            );
                        })
                        .toList();

        return new FilamentDeletePreviewDto(spools, jobs);
    }
}
