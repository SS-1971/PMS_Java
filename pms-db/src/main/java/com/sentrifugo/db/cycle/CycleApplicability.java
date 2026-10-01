package com.sentrifugo.db.cycle;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Embeddable
public class CycleApplicability {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "appl_plant_ids", columnDefinition = "jsonb", nullable = false)
    private List<String> plantIds = new ArrayList<>();

    @Column(name = "appl_all_departments", nullable = false)
    private boolean allDepartments = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "appl_department_ids", columnDefinition = "jsonb", nullable = false)
    private List<String> departmentIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "appl_employment_types", columnDefinition = "jsonb", nullable = false)
    private List<String> employmentTypes = new ArrayList<>();

    @Column(name = "appl_min_service_months", nullable = false)
    private int minServiceMonths;

    @Column(name = "appl_service_as_on")
    private LocalDate serviceAsOn;

    @Column(name = "appl_exclude_probation", nullable = false)
    private boolean excludeProbation;

    @Column(name = "appl_exclude_notice_period", nullable = false)
    private boolean excludeNoticePeriod;

    public List<String> getPlantIds() {
        return plantIds;
    }

    public void setPlantIds(List<String> plantIds) {
        this.plantIds = plantIds != null ? plantIds : new ArrayList<>();
    }

    public boolean isAllDepartments() {
        return allDepartments;
    }

    public void setAllDepartments(boolean allDepartments) {
        this.allDepartments = allDepartments;
    }

    public List<String> getDepartmentIds() {
        return departmentIds;
    }

    public void setDepartmentIds(List<String> departmentIds) {
        this.departmentIds = departmentIds != null ? departmentIds : new ArrayList<>();
    }

    public List<String> getEmploymentTypes() {
        return employmentTypes;
    }

    public void setEmploymentTypes(List<String> employmentTypes) {
        this.employmentTypes = employmentTypes != null ? employmentTypes : new ArrayList<>();
    }

    public int getMinServiceMonths() {
        return minServiceMonths;
    }

    public void setMinServiceMonths(int minServiceMonths) {
        this.minServiceMonths = minServiceMonths;
    }

    public LocalDate getServiceAsOn() {
        return serviceAsOn;
    }

    public void setServiceAsOn(LocalDate serviceAsOn) {
        this.serviceAsOn = serviceAsOn;
    }

    public boolean isExcludeProbation() {
        return excludeProbation;
    }

    public void setExcludeProbation(boolean excludeProbation) {
        this.excludeProbation = excludeProbation;
    }

    public boolean isExcludeNoticePeriod() {
        return excludeNoticePeriod;
    }

    public void setExcludeNoticePeriod(boolean excludeNoticePeriod) {
        this.excludeNoticePeriod = excludeNoticePeriod;
    }
}
