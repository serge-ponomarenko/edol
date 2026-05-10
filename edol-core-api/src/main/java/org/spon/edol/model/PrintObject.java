package org.spon.edol.model;

import lombok.Data;

@Data
public class PrintObject {

    // <object identify_id="127" name="result (1).obj_A" skipped="false" />

    public PrintObject(int id, String name, boolean skipped) {
        this.id = id;
        this.name = name;
        this.skipped = skipped;
    }

    private int id;
    private String name;
    private boolean skipped;

    private BoundingBox boundingBox;

}
