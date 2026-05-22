package org.spon.edoldashboard.service.filament;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.ExtTray;
import org.spon.edol.model.PrinterState;
import org.spon.edoldashboard.model.entity.Filament;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FilamentMatchingService {

    private final FilamentService filamentService;

    public Filament match(
            PrinterState printerState,
            org.spon.edol.model.Filament filamentDto
    ) {
        String filamentColor =
                resolveColor(
                        printerState,
                        filamentDto
                );

        return filamentService.findOrCreateFilament(
                filamentDto.getFullId(),
                filamentColor,
                filamentDto.getFilamentBrandIndex()
        );
    }

    public String resolveColor(
            PrinterState printerState,
            org.spon.edol.model.Filament filamentDto
    ) {
        String filamentColor = filamentDto.getColor();

        // AMS override
        if (
                filamentDto.getAmsSlot() != null
                        && filamentDto.getAmsSlot() != -1
                        && !printerState.isExternalSpoolUsed()
        ) {
            org.spon.edol.model.AmsSlot amsSlot =
                    printerState.getAms()
                            .getSlots()
                            .get(filamentDto.getAmsSlot());

            if (!amsSlot.getColor().equals(filamentDto.getColor())) {
                log.warn("""
                                Filament {}, {} -> AMS: {}
                                has color mismatch with AMS mapping!
                                AMS color will be used.
                                """,
                        filamentDto.getFullId(),
                        filamentDto.getColor(),
                        amsSlot.getColor()
                );
                filamentColor = amsSlot.getColor();

            }

        }

        // EXT tray override
        if (printerState.isExternalSpoolUsed()) {
            ExtTray extTray = printerState.getExtTray();
            if (!extTray.getColor().equals(filamentDto.getColor())) {
                log.warn("""
                                Filament {}, {} -> EXT: {}
                                has color mismatch with EXT Tray mapping!
                                EXT Tray color will be used.
                                """,
                        filamentDto.getFullId(),
                        filamentDto.getColor(),
                        extTray.getColor()
                );

                filamentColor = extTray.getColor();

            }

        }

        return filamentColor;
    }

}
