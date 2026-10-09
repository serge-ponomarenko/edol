package org.spon.edolams.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Narrow read contract consumed from Hub spool lookup endpoints.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HubFilamentSpool(Long id, Filament filament, Double weightRemaining) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Filament(Vendor vendor, MaterialType materialType, String brand, String colorHex) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vendor(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MaterialType(String name) {
    }
}
