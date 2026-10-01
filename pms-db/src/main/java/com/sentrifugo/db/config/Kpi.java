package com.sentrifugo.db.config;

import com.sentrifugo.db.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pms_kpis")
public class Kpi extends Auditable {

    @ManyToOne(optional = false)
    @JoinColumn(name = "kra_id", nullable = false)
    private Kra kra;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 40)
    private String unit;

    @Column(name = "target_type", nullable = false, length = 20)
    private TargetType targetType;

    @Column(name = "expected_outcome", length = 200)
    private String expectedOutcome;

    @Column(name = "evidence_required", length = 200)
    private String evidenceRequired;

    protected Kpi() {
    }

    public Kpi(String organisationId, Kra kra, String name, String unit, TargetType targetType,
               String expectedOutcome, String evidenceRequired) {
        setOrganisationId(organisationId);
        this.kra = kra;
        this.name = name;
        this.unit = unit;
        this.targetType = targetType;
        this.expectedOutcome = expectedOutcome;
        this.evidenceRequired = evidenceRequired;
    }

    public Kra getKra() {
        return kra;
    }

    public void setKra(Kra kra) {
        this.kra = kra;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
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
