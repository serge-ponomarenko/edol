package org.spon.edolams.service;

import jakarta.annotation.PostConstruct;
import org.spon.edolams.config.Stage8TerminalSimulatorProperties;
import org.spon.edolams.model.AmsStatus;
import org.spon.edolams.model.Spool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Deterministic operational fixture for the isolated Stage 8 physical-terminal smoke test.
 * It never calls Core, Hub, MQTT, or a printer command endpoint.
 */
@Service
@Profile("stage8-terminal-simulator")
@ConditionalOnProperty(name = "edol-ams.terminal-simulator.enabled", havingValue = "true")
public class Stage8TerminalOperationalSimulator implements TerminalOperationalService {

    static final long PLA_SPOOL_ID = 800001L;
    static final long PETG_SPOOL_ID = 800002L;

    private final Stage8TerminalSimulatorProperties properties;
    private final Map<Long, FixtureSpool> spools = Map.of(
            PLA_SPOOL_ID, new FixtureSpool(PLA_SPOOL_ID, "Bambu Lab", "Basic", "PLA", "#D92D20", 780),
            PETG_SPOOL_ID, new FixtureSpool(PETG_SPOOL_ID, "Bambu Lab", "Basic", "PETG", "#2E90FA", 640)
    );
    private final Map<Integer, FixtureSpool> slots = new ConcurrentHashMap<>();
    private volatile int activeSlot = 0;
    private final AtomicReference<FixtureSpool> extTray = new AtomicReference<>();

    public Stage8TerminalOperationalSimulator(Stage8TerminalSimulatorProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initializeFixture() {
        if (properties.tenantId() == null || properties.printerId() == null) {
            throw new IllegalStateException("Stage 8 terminal simulator requires tenant-id and printer-id");
        }
        slots.put(0, spools.get(PLA_SPOOL_ID));
        slots.put(2, spools.get(PETG_SPOOL_ID));
    }

    @Override
    public AmsStatus state(TerminalAuthenticationService.AuthenticatedTerminal terminal) {
        requireFixtureTerminal(terminal);

        AmsStatus status = new AmsStatus();
        status.setAmsId(0);
        status.setActiveSlot(activeSlot);
        status.setTemperature(23.5);
        status.setHumidity(2);
        status.setHumidityRaw(35);
        status.setPrinting(false);
        status.setSlots(List.of(slot(0), slot(1), slot(2), slot(3)));
        FixtureSpool externalSpool = extTray.get();
        status.setExtTray(externalSpool == null ? emptySlot(255) : externalSpool.toSlot(255, activeSlot == 254));
        return status;
    }

    @Override
    public ResponseEntity<Spool> findSpool(
            TerminalAuthenticationService.AuthenticatedTerminal terminal,
            Long spoolId
    ) {
        requireFixtureTerminal(terminal);
        FixtureSpool spool = spools.get(spoolId);
        if (spool == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(spool.toSpool());
    }

    @Override
    public ResponseEntity<String> setSpool(
            TerminalAuthenticationService.AuthenticatedTerminal terminal,
            Long spoolId,
            Integer slot
    ) {
        requireFixtureTerminal(terminal);
        if (slot == null || (slot != 254 && (slot < 0 || slot > 3))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Simulator slot must be an AMS slot or the external tray");
        }
        FixtureSpool spool = spools.get(spoolId);
        if (spool == null) {
            return ResponseEntity.notFound().build();
        }
        if (slot == 254) {
            extTray.set(spool);
        } else {
            slots.put(slot, spool);
        }
        activeSlot = slot;
        return ResponseEntity.ok("Spool assigned to simulated AMS slot");
    }

    private void requireFixtureTerminal(TerminalAuthenticationService.AuthenticatedTerminal terminal) {
        if (!properties.tenantId().equals(terminal.tenantId())
                || !properties.printerId().equals(terminal.printerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Terminal is not authorized for the Stage 8 fixture");
        }
    }

    private AmsStatus.Slot slot(int id) {
        FixtureSpool spool = slots.get(id);
        return spool == null ? emptySlot(id) : spool.toSlot(id, activeSlot == id);
    }

    private AmsStatus.Slot emptySlot(int id) {
        AmsStatus.Slot slot = new AmsStatus.Slot();
        slot.setId(id);
        slot.setEmpty(true);
        return slot;
    }

    private record FixtureSpool(
            long id,
            String vendor,
            String brand,
            String material,
            String color,
            int remaining
    ) {
        private Spool toSpool() {
            Spool spool = new Spool();
            spool.setSpoolId(id);
            spool.setVendor(vendor);
            spool.setBrand(brand);
            spool.setMaterial(material);
            spool.setColor(color);
            spool.setRemaining(remaining);
            return spool;
        }

        private AmsStatus.Slot toSlot(int slotId, boolean active) {
            AmsStatus.Slot slot = new AmsStatus.Slot();
            slot.setId(slotId);
            slot.setActive(active);
            slot.setEmpty(false);
            slot.setSpoolId(id);
            slot.setVendor(vendor);
            slot.setBrand(brand);
            slot.setMaterial(material);
            slot.setColor(color);
            slot.setRemaining(remaining);
            return slot;
        }
    }
}
