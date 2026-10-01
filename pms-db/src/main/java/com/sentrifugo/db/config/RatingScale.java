package com.sentrifugo.db.config;

import com.sentrifugo.db.audit.Auditable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pms_rating_scales")
public class RatingScale extends Auditable {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "show_definitions_to_employees", nullable = false)
    private boolean showDefinitionsToEmployees = true;

    @OneToMany(mappedBy = "scale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rating DESC")
    private List<RatingLevel> levels = new ArrayList<>();

    protected RatingScale() {
    }

    public RatingScale(String organisationId, String name, boolean isDefault, boolean showDefinitionsToEmployees) {
        setOrganisationId(organisationId);
        this.name = name;
        this.isDefault = isDefault;
        this.showDefinitionsToEmployees = showDefinitionsToEmployees;
    }

    public void addLevel(RatingLevel level) {
        level.setScale(this);
        this.levels.add(level);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public boolean isShowDefinitionsToEmployees() {
        return showDefinitionsToEmployees;
    }

    public void setShowDefinitionsToEmployees(boolean showDefinitionsToEmployees) {
        this.showDefinitionsToEmployees = showDefinitionsToEmployees;
    }

    public List<RatingLevel> getLevels() {
        return levels;
    }
}
