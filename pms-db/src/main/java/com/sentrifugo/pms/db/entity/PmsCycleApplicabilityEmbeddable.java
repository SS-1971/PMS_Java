package com.sentrifugo.pms.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Who a cycle applies to; stored inline on {@code pms.pms_cycles} ({@code appl_*} columns). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PmsCycleApplicabilityEmbeddable {

    /** IAM plant (business-unit) ids. JSONB: a variable-length id list, only ever read/written whole. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "appl_plant_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> plantIds = new ArrayList<>();

    @Column(name = "appl_all_departments", nullable = false)
    @Builder.Default
    private boolean allDepartments = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "appl_department_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> departmentIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "appl_employment_types", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> employmentTypes = new ArrayList<>();

    @Column(name = "appl_min_service_months", nullable = false)
    private int minServiceMonths;

    @Column(name = "appl_service_as_on")
    private LocalDate serviceAsOn;

    @Column(name = "appl_exclude_probation", nullable = false)
    private boolean excludeProbation;

    @Column(name = "appl_exclude_notice_period", nullable = false)
    private boolean excludeNoticePeriod;

    public void setPlantIds(List<String> plantIds) {
        this.plantIds = plantIds != null ? plantIds : new ArrayList<>();
    }

    public void setDepartmentIds(List<String> departmentIds) {
        this.departmentIds = departmentIds != null ? departmentIds : new ArrayList<>();
    }

    public void setEmploymentTypes(List<String> employmentTypes) {
        this.employmentTypes = employmentTypes != null ? employmentTypes : new ArrayList<>();
    }
}
