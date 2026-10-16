package com.sentrifugo.db.entity;

import com.sentrifugo.db.enums.PmsChangeDecision;
import com.sentrifugo.db.enums.PmsChangeRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One employee request to change one KPI target (screen 4.2), kept permanently with the manager's answer.
 * {@code revisionNo} counts the requests made for that KPI on that sheet: 1, 2, 3 ...
 */
@Entity
@Table(
        name = "goal_change_request",
        schema = "pms",
        uniqueConstraints = @UniqueConstraint(name = "uk_goal_change_request_no",
                columnNames = {"assignment_id", "kpi_id", "revision_no"}),
        indexes = @Index(name = "idx_goal_change_request_assignment", columnList = "assignment_id")
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PmsGoalChangeRequestEntity extends BaseEntity<UUID> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private PmsGoalAssignmentEntity assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kpi_id", nullable = false)
    private PmsKpiMasterEntity kpi;

    /** 1 for the first request on this KPI, then 2, 3 ... */
    @Column(name = "revision_no", nullable = false)
    private Integer revisionNo;

    @Column(name = "requested_by", nullable = false, length = 64)
    private String requestedBy;

    @Column(name = "requested_on", nullable = false)
    private LocalDateTime requestedOn;

    /** The target the employee was looking at when asking. */
    @Column(name = "target_at_request", precision = 14, scale = 2)
    private BigDecimal targetAtRequest;

    @Column(name = "proposed_target", precision = 14, scale = 2)
    private BigDecimal proposedTarget;

    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PmsChangeRequestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 20)
    private PmsChangeDecision decision;

    /** The target the manager sent back (equal to {@code targetAtRequest} when kept). */
    @Column(name = "final_target", precision = 14, scale = 2)
    private BigDecimal finalTarget;

    @Column(name = "manager_remarks", columnDefinition = "TEXT")
    private String managerRemarks;

    @Column(name = "responded_by", length = 64)
    private String respondedBy;

    @Column(name = "responded_on")
    private LocalDateTime respondedOn;

    /** When the employee acknowledged the sheet that carried the manager's answer. */
    @Column(name = "acknowledged_on")
    private LocalDateTime acknowledgedOn;
}
