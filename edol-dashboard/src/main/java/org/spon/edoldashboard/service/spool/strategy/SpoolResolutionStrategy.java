package org.spon.edoldashboard.service.spool.strategy;

import org.spon.edoldashboard.model.entity.FilamentSpool;

import java.util.Comparator;

public interface SpoolResolutionStrategy {

    Comparator<FilamentSpool> comparator();

}
