package org.spon.edolcore.persistence.printer;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "printers")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Printer {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String displayId;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, unique = true)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrinterModel model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrinterConnectionMode connectionMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrinterCameraProvider cameraProvider;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}