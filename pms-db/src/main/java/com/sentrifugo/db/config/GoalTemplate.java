package com.sentrifugo.db.config;

import com.sentrifugo.db.audit.Auditable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pms_goal_templates")
public class GoalTemplate extends Auditable {

    @Column(name = "financial_year", nullable = false)
    private int financialYear;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "department_id", nullable = false, length = 24)
    private String departmentId;

    @Column(name = "designation_id", nullable = false, length = 24)
    private String designationId;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(nullable = false, length = 20)
    private TemplateStatus status;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoalTemplateKra> kras = new ArrayList<>();

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoalTemplateCompetency> competencies = new ArrayList<>();

    protected GoalTemplate() {
    }

    public GoalTemplate(String organisationId, int financialYear, String name, String description,
                        String departmentId, String designationId, LocalDate effectiveFrom,
                        TemplateStatus status) {
        setOrganisationId(organisationId);
        this.financialYear = financialYear;
        this.name = name;
        this.description = description;
        this.departmentId = departmentId;
        this.designationId = designationId;
        this.effectiveFrom = effectiveFrom;
        this.status = status;
    }

    public void replaceKras(List<GoalTemplateKra> newKras) {
        this.kras.clear();
        for (GoalTemplateKra kra : newKras) {
            kra.setTemplate(this);
            this.kras.add(kra);
        }
    }

    public void replaceCompetencies(List<GoalTemplateCompetency> newCompetencies) {
        this.competencies.clear();
        for (GoalTemplateCompetency competency : newCompetencies) {
            competency.setTemplate(this);
            this.competencies.add(competency);
        }
    }

    public int getFinancialYear() {
        return financialYear;
    }

    public void setFinancialYear(int financialYear) {
        this.financialYear = financialYear;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getDesignationId() {
        return designationId;
    }

    public void setDesignationId(String designationId) {
        this.designationId = designationId;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public TemplateStatus getStatus() {
        return status;
    }

    public void setStatus(TemplateStatus status) {
        this.status = status;
    }

    public List<GoalTemplateKra> getKras() {
        return kras;
    }

    public List<GoalTemplateCompetency> getCompetencies() {
        return competencies;
    }
}
