package org.spon.edolams.model;

import lombok.Data;

@Data
public class Spool {

    public Long spoolId;
    public String brand;
    public String color;
    public String material;
    public int remaining;
    public String vendor;

}
