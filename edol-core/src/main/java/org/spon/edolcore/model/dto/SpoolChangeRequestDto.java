package org.spon.edolcore.model.dto;

import lombok.Data;

@Data
public class SpoolChangeRequestDto {
    Integer amsId;          // Index of the ams
    Integer trayId;         // Index of the tray
    String trayInfoIdx;     // Probably the setting ID of the filament profile
    String trayColor;       // Formatted as hex RRGGBBAA (alpha is always FF)
    String trayType;        // Type of filament, such as "PLA" or "ABS"
}
