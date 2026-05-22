package org.spon.edolhub.service.spool.strategy;

import org.spon.edolhub.model.entity.FilamentSpool;

import java.util.Comparator;

public interface SpoolResolutionStrategy {

    Comparator<FilamentSpool> comparator();

}
