package org.spon.edolhub.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Entity
@Table(name = "printer_stats")
@Data
public class PrinterStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // total printer usage
    private Long totalPrintSeconds = 0L;

    private Long totalJobs = 0L;

    private Long totalFilamentUsedGrams = 0L;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime printerStartedPrintingDate;

    private LocalDateTime updatedAt;

    @Transient
    private Long totalPrintHours;
}
