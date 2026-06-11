package org.spon.edol.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AmsSlot extends SpoolTray {

    private int id = 0;

    private boolean active;

    public boolean isEmpty() {
        return filamentType.isEmpty() && filamentBrandIndex.isEmpty() && color.isEmpty();
    }
}
