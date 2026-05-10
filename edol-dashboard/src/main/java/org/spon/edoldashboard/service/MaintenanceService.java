package org.spon.edoldashboard.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.spon.edoldashboard.model.dto.MaintenanceStatusDto;
import org.spon.edoldashboard.model.entity.MaintenanceDefinition;
import org.spon.edoldashboard.model.entity.MaintenanceExecution;
import org.spon.edoldashboard.repository.MaintenanceDefinitionRepository;
import org.spon.edoldashboard.repository.MaintenanceExecutionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final MaintenanceDefinitionRepository definitionRepo;
    private final MaintenanceExecutionRepository executionRepo;
    private final PrinterStatsService printerStatsService;

    public List<MaintenanceStatusDto> getMaintenanceStatus() {

        int currentHours = printerStatsService.getTotalPrinterHours();
        LocalDateTime startPrintingDate = printerStatsService.getStats().getPrinterStartedPrintingDate();

        return definitionRepo.findByActiveTrue()
                .stream()
                .map(m -> calculateStatus(m, currentHours, startPrintingDate))
                .sorted(Comparator.comparing(MaintenanceStatusDto::isDue).reversed())
                .toList();
    }

    private MaintenanceStatusDto calculateStatus(
            MaintenanceDefinition m,
            int currentHours,
            LocalDateTime startPrintingDate) {

        MaintenanceExecution last = executionRepo
                .findTopByMaintenanceIdOrderByExecutedAtDesc(m.getId())
                .orElse(null);

        Integer nextHour = null;
        LocalDateTime nextDate = null;

        int lastPrinterHours = 0;
        LocalDateTime lastExecutionTime = startPrintingDate;

        if (last != null) {
            lastPrinterHours = last.getPrinterTotalHours();
            lastExecutionTime = last.getExecutedAt();
        }

        if (m.getIntervalHours() != null) {
            nextHour = lastPrinterHours + m.getIntervalHours();
        }

        if (m.getIntervalDays() != null) {
            nextDate = lastExecutionTime.plusDays(m.getIntervalDays());
        }

        boolean due = nextHour != null && currentHours >= nextHour;

        if (nextDate != null && LocalDateTime.now().isAfter(nextDate))
            due = true;

        int hoursSinceLast = currentHours - lastPrinterHours;

        int progressPercent = 0;

        if (m.getIntervalHours() != null) {
            progressPercent = (hoursSinceLast * 100) / m.getIntervalHours();

            if (progressPercent > 100) {
                progressPercent = 100;
            }
        }

        MaintenanceStatusDto dto = new MaintenanceStatusDto();

        dto.setId(m.getId());
        dto.setName(m.getName());
        dto.setDescription(m.getDescription());
        dto.setIntervalHours(m.getIntervalHours());
        dto.setIntervalDays(m.getIntervalDays());
        dto.setNextHour(nextHour);
        dto.setNextDate(nextDate);
        dto.setDue(due);
        dto.setCurrentPrinterHours(currentHours);
        dto.setHoursSinceLast(hoursSinceLast);
        dto.setNeverExecuted(last == null);
        dto.setProgressPercent(progressPercent);

        return dto;
    }

    @Transactional
    public void completeMaintenance(Long id, String notes) {

        MaintenanceDefinition definition =
                definitionRepo.findById(id).orElseThrow();

        int currentHours = printerStatsService.getTotalPrinterHours();

        MaintenanceExecution exec = new MaintenanceExecution();

        exec.setMaintenance(definition);
        exec.setExecutedAt(LocalDateTime.now());
        exec.setPrinterTotalHours(currentHours);
        exec.setNotes(notes);

        executionRepo.save(exec);
    }
}
