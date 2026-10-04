package com.sentrifugo.pms.db.entity;

import com.sentrifugo.pms.db.enums.PmsTemplateStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Soft-deleted by setting {@code isActive = false} rather than {@code @SQLDelete}:
 * the KRA/competency child rows cascade on {@code remove}, which would
 * physically erase them and make the template unrecoverable.
 */
@Entity
@Table(name = "pms_goal_templates", schema = "pms")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PmsGoalTemplateEntity extends BaseEntity<UUID> {

    @Column(name = "organisation_id", nullable = false, length = 24, updatable = false)
    private String organisationId;

    @Column(name = "financial_year", nullable = false)
    private int financialYear;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** IAM department id (opaque 24-char string). */
    @Column(name = "department_id", nullable = false, length = 24)
    private String departmentId;

    /** IAM designation id (opaque 24-char string). */
    @Column(name = "designation_id", nullable = false, length = 24)
    private String designationId;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PmsTemplateStatus status;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @lombok.Builder.Default
    private List<PmsGoalTemplateKraEntity> kras = new ArrayList<>();

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @lombok.Builder.Default
    private List<PmsGoalTemplateCompetencyEntity> competencies = new ArrayList<>();

    public void replaceKras(List<PmsGoalTemplateKraEntity> newKras) {
        kras.clear();
        for (PmsGoalTemplateKraEntity kra : newKras) {
            kra.setTemplate(this);
            kras.add(kra);
        }
    }

    public void replaceCompetencies(List<PmsGoalTemplateCompetencyEntity> newCompetencies) {
        competencies.clear();
        for (PmsGoalTemplateCompetencyEntity competency : newCompetencies) {
            competency.setTemplate(this);
            competencies.add(competency);
        }
    }
}
