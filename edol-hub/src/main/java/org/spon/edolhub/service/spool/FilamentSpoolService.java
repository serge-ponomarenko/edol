package org.spon.edolhub.service.spool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolhub.model.entity.FilamentSpool;
import org.spon.edolhub.repository.FilamentSpoolRepository;
import org.spon.edolhub.service.TenantContext;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class FilamentSpoolService {

    private final FilamentSpoolRepository filamentSpoolRepository;
    private final TenantContext tenantContext;

    public List<FilamentSpool> findFiltered(
            String vendor,
            String material,
            List<FilamentSpool.FilamentSpoolStatus> status
    ) {
        return filamentSpoolRepository.findAllByTenantIdAndFiltersWithDetails(
                tenantContext.getCurrentTenantId(),
                normalizeFilter(vendor),
                normalizeFilter(material),
                status
        );
    }

    private static String normalizeFilter(String filter) {
        return filter == null || filter.isBlank() ? null : filter;
    }

}
