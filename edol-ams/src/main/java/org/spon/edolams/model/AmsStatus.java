package org.spon.edolams.model;

import lombok.Data;

import java.util.List;

@Data
public class AmsStatus {

    public int amsId;
    public int activeSlot;
    public Slot extTray;
    public List<Slot> slots;
    public int humidity;
    public int humidityRaw;
    public double temperature;

    @Data
    public static class Slot {
        public boolean active;
        public boolean empty;
        public String brand;
        public String color;
        public int id;
        public String material;
        public int remaining;
        public Long spoolId;
        public String vendor;
    }
}
