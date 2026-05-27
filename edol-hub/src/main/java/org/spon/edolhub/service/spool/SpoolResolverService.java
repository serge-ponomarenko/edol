package org.spon.edolhub.service.spool;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.Filament;
import org.spon.edolhub.model.entity.FilamentSpool;
import org.spon.edolhub.repository.FilamentSpoolRepository;
import org.spon.edolhub.service.spool.strategy.SpoolResolutionStrategy;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

import static org.spon.edolhub.model.entity.FilamentSpool.FilamentSpoolStatus.ACTIVE;
import static org.spon.edolhub.model.entity.FilamentSpool.FilamentSpoolStatus.SEALED;

@Service
@RequiredArgsConstructor
public class SpoolResolverService {

    private final FilamentSpoolRepository filamentSpoolRepository;

    private final List<SpoolResolutionStrategy> strategies;

    public List<FilamentSpool> resolveCandidates(
            Filament filament
    ) {
        List<FilamentSpool> candidates =
                filamentSpoolRepository.findAllByFilamentIdAndStatusIn(
                        filament.getId(),
                        List.of(
                                ACTIVE,
                                SEALED
                        )
                );

        if (candidates.isEmpty()) {
            return List.of();
        }

        Comparator<FilamentSpool> comparator = (a, b) -> 0;

        for (SpoolResolutionStrategy strategy : strategies) {
            comparator =
                    comparator.thenComparing(
                            strategy.comparator()
                    );
        }

        return candidates.stream()
                .sorted(comparator)
                .toList();

    }

}