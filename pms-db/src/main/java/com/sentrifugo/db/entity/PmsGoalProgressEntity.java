package com.sentrifugo.db.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import com.sentrifugo.db.enums.PmsProgressPeriod;

import java.math.BigDecimal;
import java.util.UUID;

/** An actual reported against one KPI of a goal sheet, for one reading (current, half-year or year-end). */
@Entity
@Table(
        name = "goal_progress",
        schema = "pms",
        uniqueConstraints = @UniqueConstraint(name = "uk_goal_progress_assignment_kpi_period",
                columnNames = {"assignment_id", "kpi_id", "period_code"}),
        indexes = @Index(name = "idx_goal_progress_assignment", columnList = "assignment_id")
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PmsGoalProgressEntity extends BaseEntity<UUID> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private PmsGoalAssignmentEntity assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kpi_id", nullable = false)
    private PmsKpiMasterEntity kpi;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_code", nullable = false, length = 20)
    private PmsProgressPeriod period;

    @Column(name = "actual", precision = 14, scale = 2)
    private BigDecimal actual;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "attachment_name", length = 255)
    private String attachmentName;

    /** The employee own status for this KPI: on_track, at_risk or off_track. */
    @Column(name = "status", length = 20)
    private String status;

    /** When the employee submitted this period to the manager; null while it is a draft. Submitted rows are frozen. */
    @Column(name = "submitted_on")
    private java.time.LocalDateTime submittedOn;
}
