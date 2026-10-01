package com.sentrifugo.db.config;

import com.sentrifugo.db.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "pms_competencies")
public class Competency extends Auditable {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private CompetencyCategory category;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Competency() {
    }

    public Competency(String organisationId, String name, CompetencyCategory category, boolean active) {
        setOrganisationId(organisationId);
        this.name = name;
        this.category = category;
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CompetencyCategory getCategory() {
        return category;
    }

    public void setCategory(CompetencyCategory category) {
        this.category = category;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
