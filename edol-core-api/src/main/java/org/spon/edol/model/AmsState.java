package org.spon.edol.model;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class AmsState {

    private int amsId = 0;

    private List<AmsSlot> slots = Collections.emptyList();

    private Integer activeSlot = 255;
    private Integer previousSlot = 255;

    private Double temperature = 0.0;

    private Integer humidity = 0;
    private Integer humidityRaw = 0;

}