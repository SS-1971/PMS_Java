package com.sentrifugo.db.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

/** One KPI's weight and target inside a goal assignment. */
@Entity
@Table(
        name = "goal_assignment_target",
        schema = "pms",
        uniqueConstraints = @UniqueConstraint(name = "uk_goal_assignment_target_kpi",
                columnNames = {"assignment_id", "kpi_id"}),
        indexes = @Index(name = "idx_goal_assignment_target_assignment", columnList = "assignment_id")
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PmsGoalAssignmentTargetEntity extends BaseEntity<UUID> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private PmsGoalAssignmentEntity assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kpi_id", nullable = false)
    private PmsKpiMasterEntity kpi;

    /** Share of the total KPI weightage, out of 100. */
    @Column(name = "weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    /** Numeric target. Null until the manager fills it in. */
    @Column(name = "target", precision = 14, scale = 2)
    private BigDecimal target;

    /**
     * The target the manager first assigned. Revisions never change it: the employee always sees this in the
     * Target column, and a revised target is shown beside it. Null until the sheet is first sent.
     */
    @Column(name = "original_target", precision = 14, scale = 2)
    private BigDecimal originalTarget;

    /**
     * The target as it stood when the sheet was last sent to the employee. Save Draft rewrites {@code target},
     * so this is what a revised target is compared with. Null until the sheet is first sent.
     */
    @Column(name = "sent_target", precision = 14, scale = 2)
    private BigDecimal sentTarget;
}
