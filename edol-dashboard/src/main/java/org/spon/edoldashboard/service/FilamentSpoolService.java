package org.spon.edoldashboard.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.spon.edoldashboard.model.entity.Filament;
import org.spon.edoldashboard.model.entity.FilamentSpool;
import org.spon.edoldashboard.model.entity.JobFilamentUsage;
import org.spon.edoldashboard.repository.FilamentSpoolRepository;
import org.spon.edoldashboard.repository.JobFilamentUsageRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class FilamentSpoolService {

    private final FilamentSpoolRepository filamentSpoolRepository;
    private final JobFilamentUsageRepository jobFilamentUsageRepository;

    public FilamentSpool findOrCreateForFilament(Filament filament) {
        return filamentSpoolRepository
                .findFirstByFilamentIdAndStatus(filament.getId(), FilamentSpool.FilamentSpoolStatus.ACTIVE)
                .orElseGet(() -> {

                    FilamentSpool spool = FilamentSpool.builder()
                            .filament(filament)
                            .weightTotal(1000)
                            .weightRemaining(1000)
                            .price(BigDecimal.valueOf(500))
                            .openedAt(LocalDateTime.now())
                            .status(FilamentSpool.FilamentSpoolStatus.NEW)
                            .build();

                    return filamentSpoolRepository.save(spool);
                });
    }

    @Cacheable("spools")
    public Optional<FilamentSpool> findSpool(Long filamentId, FilamentSpool.FilamentSpoolStatus status) {
        return filamentSpoolRepository.findFirstByFilamentIdAndStatus(filamentId, status);
    }

    public void recalculateSpoolsForJobs(Filament filament, List<Long> jobIds) {
        // 🔹 Only usages from affected jobs
        List<JobFilamentUsage> usages =
                jobFilamentUsageRepository.findByFilamentIdAndPrintJobIdIn(
                        filament.getId(), jobIds
                );

        double deltaUsed = usages.stream()
                .mapToDouble(JobFilamentUsage::getUsedGrams)
                .sum();

        FilamentSpool spool = filamentSpoolRepository
                .findFirstByFilamentIdAndStatus(
                        filament.getId(),
                        FilamentSpool.FilamentSpoolStatus.ACTIVE
                )
                .orElseThrow();

        int remaining = spool.getWeightRemaining() != null
                ? spool.getWeightRemaining() - (int) deltaUsed
                : spool.getWeightTotal() - (int) deltaUsed;

        spool.setWeightRemaining(Math.max(remaining, 0));

        if (spool.getWeightRemaining() == 0) {
            spool.setStatus(FilamentSpool.FilamentSpoolStatus.EMPTY);
        }
    }

    public List<FilamentSpool> findFiltered(
            String vendor,
            String material,
            List<FilamentSpool.FilamentSpoolStatus> status
    ) {
        return filamentSpoolRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (vendor != null && !vendor.isEmpty()) {
                predicates.add(cb.equal(
                        root.get("filament").get("vendor").get("name"),
                        vendor
                ));
            }

            if (material != null && !material.isEmpty()) {
                predicates.add(cb.equal(
                        root.get("filament").get("materialType").get("name"),
                        material
                ));
            }

            if (status != null && !status.isEmpty()) {
                predicates.add(root.get("status").in(status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

}
