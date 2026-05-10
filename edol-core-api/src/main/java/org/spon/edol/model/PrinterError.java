package org.spon.edol.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PrinterError {

    private int code;
    private String message;

}
