package org.spon.edoldashboard.service.filament;

import lombok.RequiredArgsConstructor;
import org.spon.edoldashboard.model.dto.FilamentDeletePreviewDto;
import org.spon.edoldashboard.repository.FilamentSpoolRepository;
import org.spon.edoldashboard.repository.JobFilamentUsageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FilamentDeleteService {

    private final FilamentSpoolRepository spoolRepository;
    private final JobFilamentUsageRepository usageRepository;

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
                usageRepository.findByFilamentId(filamentId)
                        .stream()
                        .map(u -> new FilamentDeletePreviewDto.JobInfo(
                                u.getPrintJob().getId(),
                                u.getPrintJob().getTaskName(),
                                u.getUsedGrams()
                        ))
                        .toList();

        return new FilamentDeletePreviewDto(spools, jobs);
    }
}
