package com.sentrifugo.db.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One KPI ticked into a goal template, under one of its KRAs. {@code targetType} /
 * {@code expectedOutcome} / {@code evidenceRequired} are per-template overrides of
 * the KPI master's defaults — null means "use the master's value".
 */
@Entity
@Table(name = "pms_goal_template_kpis")
public class GoalTemplateKpi {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "template_kra_id", nullable = false)
    private GoalTemplateKra templateKra;

    @ManyToOne(optional = false)
    @JoinColumn(name = "kpi_id", nullable = false)
    private Kpi kpi;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(name = "target_type", length = 20)
    private TargetType targetType;

    @Column(name = "expected_outcome", length = 200)
    private String expectedOutcome;

    @Column(name = "evidence_required", length = 200)
    private String evidenceRequired;

    protected GoalTemplateKpi() {
    }

    public GoalTemplateKpi(Kpi kpi, BigDecimal weight, TargetType targetType, String expectedOutcome,
                           String evidenceRequired) {
        this.kpi = kpi;
        this.weight = weight;
        this.targetType = targetType;
        this.expectedOutcome = expectedOutcome;
        this.evidenceRequired = evidenceRequired;
    }

    public UUID getId() {
        return id;
    }

    public GoalTemplateKra getTemplateKra() {
        return templateKra;
    }

    public void setTemplateKra(GoalTemplateKra templateKra) {
        this.templateKra = templateKra;
    }

    public Kpi getKpi() {
        return kpi;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(TargetType targetType) {
        this.targetType = targetType;
    }

    public String getExpectedOutcome() {
        return expectedOutcome;
    }

    public void setExpectedOutcome(String expectedOutcome) {
        this.expectedOutcome = expectedOutcome;
    }

    public String getEvidenceRequired() {
        return evidenceRequired;
    }

    public void setEvidenceRequired(String evidenceRequired) {
        this.evidenceRequired = evidenceRequired;
    }
}
