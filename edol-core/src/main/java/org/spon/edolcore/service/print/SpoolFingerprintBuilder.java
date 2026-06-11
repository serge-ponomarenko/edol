package org.spon.edolcore.service.print;

import org.spon.edol.model.AmsSlot;
import org.spon.edol.model.AmsState;
import org.spon.edol.model.ExtTray;
import org.springframework.stereotype.Component;

@Component
public class SpoolFingerprintBuilder {

    public String build(AmsState ams, ExtTray extTray) {
        StringBuilder sb = new StringBuilder();

        for (AmsSlot slot : ams.getSlots()) {
            sb.append("AMS")
                    .append(slot.getId())
                    .append("=")
                    .append(slot.getFilamentType())
                    .append("|")
                    .append(slot.getFilamentBrandIndex())
                    .append("|")
                    .append(slot.getColor())
                    .append(";");

        }

        sb.append("EXT=")
                .append(extTray.getFilamentType())
                .append("|")
                .append(extTray.getFilamentBrandIndex())
                .append("|")
                .append(extTray.getColor());

        return sb.toString();
    }
}