package org.spon.edolcore.event;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class AmsEvent {

    UUID printerId;

    private AmsEventType type;

    private Integer slot;

}