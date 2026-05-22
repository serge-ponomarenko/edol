package org.spon.edoldashboard.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Entity
@Table(name = "print_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrintJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Printer job identifier
     */
    private String sessionId;

    /**
     * Printer identifier
     */
    private int printerId;

    /**
     * File name
     */
    private String fileName;

    /**
     * Task name
     */
    private String taskName;

    /**
     * Current job status
     */
    @Enumerated(EnumType.STRING)
    private PrintJobStatus status;

    private Integer progress;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @OneToMany(mappedBy = "printJob", cascade = CascadeType.ALL)
    private List<JobFilamentUsage> jobFilamentUsages;

    @OneToMany(mappedBy = "printJob", cascade = CascadeType.ALL)
    private List<JobSpoolUsage> jobSpoolUsages;

    @Column
    private Integer currentLayer;

    @Column
    private Integer totalLayers;

    @Column
    private Integer remainingTime;

    @Lob
    @Column(name = "plate_image")
    private byte[] plateImage;

    @Column(name = "plate_image_type")
    private String plateImageType;

    @Transient
    public String getRemainingTimeFormatted() {

        if (remainingTime == null || remainingTime <= 0) {
            return "-";
        }

        int hours = remainingTime / 60;
        int minutes = remainingTime % 60;

        if (hours > 0 && minutes > 0) {
            return hours + " hr " + minutes + " min";
        }

        if (hours > 0) {
            return hours + " hr";
        }

        return minutes + " min";
    }

    public Duration getPrintDuration() {

        if (startedAt == null || finishedAt == null) {
            return null;
        }

        return Duration.between(startedAt, finishedAt);
    }

    public String getFormattedDuration() {

        Duration d = getPrintDuration();

        if (d == null) {
            return "-";
        }

        long hours = d.toHours();
        long minutes = d.toMinutesPart();

        if (hours > 0) {
            return hours + " hr " + minutes + " min";
        }

        return minutes + " min";
    }

    public LocalDateTime getEstimatedFinishTime() {

        if (remainingTime == null) {
            return null;
        }

        return LocalDateTime.now().plusMinutes(remainingTime);
    }

    public String getFormattedEstimatedFinishTime() {

        LocalDateTime eta = getEstimatedFinishTime();

        if (eta == null) {
            return "-";
        }

        return eta.format(DateTimeFormatter.ofPattern("dd.MM HH:mm"));
    }

}
