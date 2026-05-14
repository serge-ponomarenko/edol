package org.spon.edolcore.event;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AmsEvent {

    private AmsEventType type;

    private Integer slot;

}