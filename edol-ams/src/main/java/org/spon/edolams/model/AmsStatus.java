package org.spon.edolams.model;

import lombok.Data;

import java.util.List;

@Data
public class AmsStatus {

    private int amsId;
    private int activeSlot;
    private Slot extTray;
    private List<Slot> slots;
    private int humidity;
    private int humidityRaw;
    private double temperature;

    private boolean printing;

    private int currentLayer;
    private int totalLayers;

    @Data
    public static class Slot {
        private boolean active;
        private boolean empty;
        private String brand;
        private String color;
        private int id;
        private String material;
        private int remaining;
        private Long spoolId;
        private String vendor;
    }
}
