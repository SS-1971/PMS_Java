package com.sentrifugo.pms.db.entity;

import com.sentrifugo.pms.db.enums.PmsCompetencyCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(name = "pms_competencies", schema = "pms")
@SQLDelete(sql = "UPDATE pms.pms_competencies SET is_active = false, modified_date = now() WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PmsCompetencyEntity extends BaseEntity<UUID> {

    @Column(name = "organisation_id", nullable = false, length = 24, updatable = false)
    private String organisationId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private PmsCompetencyCategory category;

    /**
     * The business "enabled" switch shown in the UI. Deliberately not
     * {@code is_active}, which {@link BaseEntity} reserves for soft delete.
     */
    @Column(name = "is_enabled", nullable = false)
    @lombok.Builder.Default
    private boolean enabled = true;
}
