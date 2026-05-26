package org.spon.edolhub.service.spool;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolhub.model.entity.FilamentSpool;
import org.spon.edolhub.model.entity.JobSpoolUsage;
import org.spon.edolhub.repository.FilamentSpoolRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpoolConsumptionService {

    private final FilamentSpoolRepository filamentSpoolRepository;

    @Transactional
    public void consume(
            List<JobSpoolUsage> spoolUsages
    ) {
        for (JobSpoolUsage usage : spoolUsages) {
            FilamentSpool spool = usage.getFilamentSpool();

            int remaining =
                    spool.getWeightRemaining() != null
                            ? spool.getWeightRemaining()
                            : spool.getWeightTotal();

            int consumed = usage.getUsedGrams().intValue();

            int newRemaining =
                    Math.max(
                            remaining - consumed,
                            0
                    );

            spool.setWeightRemaining(
                    newRemaining
            );

            spool.setLastUsedAt(
                    LocalDateTime.now()
            );

            if (newRemaining == 0) {
                spool.setStatus(
                        FilamentSpool.FilamentSpoolStatus.EMPTY
                );

            } else {
                spool.setStatus(
                        FilamentSpool.FilamentSpoolStatus.ACTIVE
                );
            }

            filamentSpoolRepository.save(spool);

            log.info("""
                            Spool {} consumed {}g, remaining {}g
                            """,
                    spool.getId(),
                    consumed,
                    newRemaining
            );

        }

    }

    @Transactional
    public void rollback(
            List<JobSpoolUsage> spoolUsages
    ) {
        for (JobSpoolUsage usage
                : spoolUsages) {
            FilamentSpool spool = usage.getFilamentSpool();

            int remaining =
                    spool.getWeightRemaining() != null
                            ? spool.getWeightRemaining()
                            : spool.getWeightTotal();

            int restored = usage.getUsedGrams().intValue();
            int newRemaining = remaining + restored;

            spool.setWeightRemaining(
                    Math.min(
                            newRemaining,
                            spool.getWeightTotal()
                    )
            );

            spool.setStatus(
                    FilamentSpool
                            .FilamentSpoolStatus
                            .ACTIVE
            );

            filamentSpoolRepository.save(spool);

            log.info("Spool {} rollback {}g, remaining {}g",
                    spool.getId(),
                    restored,
                    spool.getWeightRemaining()
            );

        }

    }

}