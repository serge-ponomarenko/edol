package org.spon.edol.model;

import lombok.Getter;

import java.time.Instant;

@Getter
public class CameraSnapshot {

    private final Instant timestamp;
    private final byte[] image;

    public CameraSnapshot(byte[] image) {
        this.timestamp = Instant.now();
        this.image = image;
    }

}