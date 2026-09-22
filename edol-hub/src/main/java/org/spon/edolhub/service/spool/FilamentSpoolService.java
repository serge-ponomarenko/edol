package org.spon.edolhub.service.spool;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolhub.model.entity.Filament;
import org.spon.edolhub.model.entity.FilamentSpool;
import org.spon.edolhub.repository.FilamentSpoolRepository;
import org.spon.edolhub.service.TenantContext;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Slf4j
public class FilamentSpoolService {

    private static final String FILAMENT_PROPERTY = "filament";

    private final FilamentSpoolRepository filamentSpoolRepository;
    private final TenantContext tenantContext;

    public List<FilamentSpool> findFiltered(
            String vendor,
            String material,
            List<FilamentSpool.FilamentSpoolStatus> status
    ) {
        return filamentSpoolRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(
                    root.get(FILAMENT_PROPERTY).get("tenant").get("id"),
                    tenantContext.getCurrentTenantId()
            ));

            if (vendor != null && !vendor.isEmpty()) {
                predicates.add(cb.equal(
                        root.get(FILAMENT_PROPERTY).get("vendor").get("name"),
                        vendor
                ));
            }

            if (material != null && !material.isEmpty()) {
                predicates.add(cb.equal(
                        root.get(FILAMENT_PROPERTY).get("materialType").get("name"),
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
