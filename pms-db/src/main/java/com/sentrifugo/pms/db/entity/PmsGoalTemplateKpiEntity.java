package com.sentrifugo.pms.db.entity;

import com.sentrifugo.pms.db.enums.PmsTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One KPI ticked into a goal template under one of its KRAs. {@code targetType},
 * {@code expectedOutcome} and {@code evidenceRequired} are per-template overrides
 * of the KPI master's defaults — null means "use the master's value".
 */
@Entity
@Table(name = "pms_goal_template_kpis", schema = "pms")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PmsGoalTemplateKpiEntity extends BaseEntity<UUID> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_kra_id", nullable = false)
    private PmsGoalTemplateKraEntity templateKra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kpi_id", nullable = false)
    private PmsKpiEntity kpi;

    @Column(name = "weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 20)
    private PmsTargetType targetType;

    @Column(name = "expected_outcome", length = 200)
    private String expectedOutcome;

    @Column(name = "evidence_required", length = 200)
    private String evidenceRequired;
}
