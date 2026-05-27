package org.spon.edolhub.service.filament;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.ExtTray;
import org.spon.edol.model.PrinterState;
import org.spon.edolhub.model.entity.Filament;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class FilamentMatchingService {

    private final FilamentService filamentService;

    public Filament match(
            PrinterState printerState,
            org.spon.edol.model.Filament filamentDto
    ) {
        RuntimeFilamentMapping mapping =
                resolveRuntimeMapping(
                        printerState,
                        filamentDto
                );

        if (Objects.equals(
                mapping.filamentBrandIndex(),
                filamentDto.getFilamentBrandIndex()
        )) {

            return filamentService.findOrCreateFilament(
                    filamentDto.getFullId(),
                    mapping.color(),
                    mapping.filamentBrandIndex()
            );
        }

        return filamentService.findByBrandIndexOrCreate(
                mapping.filamentBrandIndex(),
                mapping.color(),
                filamentDto.getFullId()
        );
    }

    private RuntimeFilamentMapping resolveRuntimeMapping(
            PrinterState printerState,
            org.spon.edol.model.Filament filamentDto
    ) {
        String color = filamentDto.getColor();

        String filamentBrandIndex = filamentDto.getFilamentBrandIndex();

        if (
                filamentDto.getAmsSlot() != null
                        && filamentDto.getAmsSlot() != -1
                        && !printerState.isExternalSpoolUsed()
        ) {
            org.spon.edol.model.AmsSlot amsSlot =
                    printerState.getAms()
                            .getSlots()
                            .get(filamentDto.getAmsSlot());

            if (!Objects.equals(
                    amsSlot.getColor(),
                    filamentDto.getColor()
            )) {
                log.info("""
                                Filament {}, {} -> AMS: {}
                                has color mismatch with AMS mapping.
                                AMS color will be used.
                                """,
                        filamentDto.getFullId(),
                        filamentDto.getColor(),
                        amsSlot.getColor()
                );

                color = amsSlot.getColor();
            }

            if (!Objects.equals(
                    amsSlot.getFilamentBrandIndex(),
                    filamentDto.getFilamentBrandIndex()
            )) {
                log.info("""
                                Filament {}, {} -> AMS: {}
                                has filament brand index mismatch with AMS mapping.
                                AMS filament brand index will be used.
                                """,
                        filamentDto.getFullId(),
                        filamentDto.getFilamentBrandIndex(),
                        amsSlot.getFilamentBrandIndex()
                );

                filamentBrandIndex =
                        amsSlot.getFilamentBrandIndex();
            }
        }

        if (printerState.isExternalSpoolUsed()) {
            ExtTray extTray =
                    printerState.getExtTray();

            if (!Objects.equals(
                    extTray.getColor(),
                    filamentDto.getColor()
            )) {
                log.info("""
                                Filament {}, {} -> EXT: {}
                                has color mismatch with EXT tray mapping.
                                EXT tray color will be used.
                                """,
                        filamentDto.getFullId(),
                        filamentDto.getColor(),
                        extTray.getColor()
                );

                color = extTray.getColor();
            }

            if (!Objects.equals(
                    extTray.getFilamentBrandIndex(),
                    filamentDto.getFilamentBrandIndex()
            )) {
                log.info("""
                                Filament {}, {} -> EXT: {}
                                has filament brand index mismatch with EXT tray mapping.
                                EXT tray filament brand index will be used.
                                """,
                        filamentDto.getFullId(),
                        filamentDto.getFilamentBrandIndex(),
                        extTray.getFilamentBrandIndex()
                );

                filamentBrandIndex =
                        extTray.getFilamentBrandIndex();
            }
        }

        return new RuntimeFilamentMapping(
                color,
                filamentBrandIndex
        );
    }

    public record RuntimeFilamentMapping(
            String color,
            String filamentBrandIndex
    ) {
    }

}
