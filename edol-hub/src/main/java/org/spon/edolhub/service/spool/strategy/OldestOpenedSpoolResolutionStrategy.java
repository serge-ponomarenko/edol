package org.spon.edolhub.service.spool.strategy;

import org.spon.edolhub.model.entity.FilamentSpool;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
@Order(100)
public class OldestOpenedSpoolResolutionStrategy
        implements SpoolResolutionStrategy {

    @Override
    public Comparator<FilamentSpool> comparator() {
        return Comparator.comparing(
                FilamentSpool::getOpenedAt,
                Comparator.nullsLast(
                        Comparator.naturalOrder()
                )
        );
    }

}
