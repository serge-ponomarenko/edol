package org.spon.edolcore.persistence.printer;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "printer_connection_configurations")
@Getter
@Setter
public class PrinterConnectionConfiguration {

    @Id
    @Column(nullable = false)
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(
            name = "printer_id",
            nullable = false,
            unique = true
    )
    private Printer printer;

    private String mqttHost;

    private Integer mqttPort;

    private String ftpHost;

    private Integer ftpPort;

    private String accessCode;

    private String agentId;
}