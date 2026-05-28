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

import static org.spon.edolhub.config.GramUtils.GRAM_EPSILON;
import static org.spon.edolhub.config.GramUtils.round;

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

            double remaining =
                    spool.getWeightRemaining() != null
                            ? spool.getWeightRemaining()
                            : spool.getWeightTotal();

            double consumed =
                    usage.getUsedGrams();

            double newRemaining =
                    Math.max(
                            0.0,
                            round(
                                    remaining - consumed
                            )
                    );

            spool.setWeightRemaining(
                    newRemaining
            );

            spool.setLastUsedAt(
                    LocalDateTime.now()
            );

            if (newRemaining <= GRAM_EPSILON) {
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

            double remaining =
                    spool.getWeightRemaining() != null
                            ? spool.getWeightRemaining()
                            : spool.getWeightTotal();

            double restored =
                    usage.getUsedGrams();
            double newRemaining =
                    round(
                            Math.min(
                                    remaining + restored,
                                    spool.getWeightTotal()
                            )
                    );

            spool.setWeightRemaining(newRemaining);

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