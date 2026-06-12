package org.spon.edolcore.service.print;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.spon.edolcore.persistence.print.ActivePrintContextEntity;
import org.spon.edolcore.persistence.print.ActivePrintContextRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivePrintContextService {

    private final ActivePrintContextRepository repository;

    public void updateRuntimeState(
            UUID sessionId,
            int layer,
            int progress,
            int remainingTime
    ) {
        repository.findById(sessionId)
                .ifPresent(context -> {
                    context.setSavedLayer(layer);
                    context.setSavedProgress(progress);
                    context.setRemainingTime(remainingTime);
                    context.setLastUpdatedAt(Instant.now());

                    repository.save(context);
                });
    }

    public Optional<ActivePrintContext> find(UUID sessionId) {
        return repository.findById(sessionId)
                .map(this::toDomain);
    }

    public long count() {
        return repository.count();
    }

    @Transactional
    public void save(ActivePrintContext context) {
        repository.deleteAll();
        repository.save(toEntity(context));
    }

    public void delete(UUID sessionId) {
        repository.deleteById(sessionId);
    }

    public Optional<ActivePrintContext> findAny() {
        return repository.findAll()
                .stream()
                .map(this::toDomain)
                .findFirst();
    }

    private ActivePrintContextEntity toEntity(ActivePrintContext context) {
        ActivePrintContextEntity entity = new ActivePrintContextEntity();

        entity.setSessionId(context.getSessionId());
        entity.setGcodeFile(context.getGcodeFile());
        entity.setSubtaskName(context.getSubtaskName());
        entity.setTotalLayers(context.getTotalLayers());

        entity.setSavedLayer(context.getSavedLayer());
        entity.setSavedProgress(context.getSavedProgress());

        entity.setRemainingTime(context.getRemainingTime());

        entity.setSpoolFingerprint(context.getSpoolFingerprint());

        entity.setStartedAt(context.getStartedAt());
        entity.setLastUpdatedAt(context.getLastUpdatedAt());

        return entity;
    }

    private ActivePrintContext toDomain(ActivePrintContextEntity entity) {
        return ActivePrintContext.builder()
                .sessionId(entity.getSessionId())
                .gcodeFile(entity.getGcodeFile())
                .subtaskName(entity.getSubtaskName())
                .totalLayers(entity.getTotalLayers())
                .savedLayer(entity.getSavedLayer())
                .savedProgress(entity.getSavedProgress())
                .remainingTime(entity.getRemainingTime())
                .spoolFingerprint(entity.getSpoolFingerprint())
                .startedAt(entity.getStartedAt())
                .lastUpdatedAt(entity.getLastUpdatedAt())
                .build();
    }
}