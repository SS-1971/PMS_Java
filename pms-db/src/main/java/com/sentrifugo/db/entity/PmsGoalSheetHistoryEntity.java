package com.sentrifugo.db.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One step in the life of a goal sheet or a mid-year target revision: who did what, when, with the
 * target before and after. Written by the goal-setting actions and read by the HOD history and trail screens.
 */
@Entity
@Table(
        name = "goal_sheet_history",
        schema = "pms",
        indexes = {
                @Index(name = "idx_goal_sheet_history_assignment", columnList = "assignment_id"),
                @Index(name = "idx_goal_sheet_history_actor", columnList = "actor_role, action")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PmsGoalSheetHistoryEntity extends BaseEntity<UUID> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private PmsGoalAssignmentEntity assignment;

    /** {@code goal_sheet} or {@code target_revision}. */
    @Column(name = "request_type", nullable = false, length = 20)
    private String requestType;

    /** The target revision this step belongs to; null for the goal sheet. Groups steps into one request. */
    @Column(name = "request_ref")
    private UUID requestRef;

    /** sent, requested_change, answered, acknowledged, sent_to_hod, revision_requested, approved, returned. */
    @Column(name = "action", nullable = false, length = 30)
    private String action;

    @Column(name = "actor_user_id", nullable = false, length = 64)
    private String actorUserId;

    @Column(name = "actor_name", length = 200)
    private String actorName;

    /** manager, employee or hod. */
    @Column(name = "actor_role", nullable = false, length = 20)
    private String actorRole;

    @Column(name = "kpi_id")
    private UUID kpiId;

    @Column(name = "kpi_name", length = 255)
    private String kpiName;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "target_before", precision = 14, scale = 2)
    private BigDecimal targetBefore;

    @Column(name = "target_after", precision = 14, scale = 2)
    private BigDecimal targetAfter;

    /** How many other KPIs moved in the same step; the row names only the first. */
    @Column(name = "more_changes", nullable = false)
    private int moreChanges;

    /** Plain-language line shown on the trail. */
    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "occurred_on", nullable = false)
    private LocalDateTime occurredOn;
}
