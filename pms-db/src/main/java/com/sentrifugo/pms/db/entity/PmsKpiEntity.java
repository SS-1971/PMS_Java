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
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(name = "pms_kpis", schema = "pms")
@SQLDelete(sql = "UPDATE pms.pms_kpis SET is_active = false, modified_date = now() WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PmsKpiEntity extends BaseEntity<UUID> {

    @Column(name = "organisation_id", nullable = false, length = 24, updatable = false)
    private String organisationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kra_id", nullable = false)
    private PmsKraEntity kra;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "unit", length = 40)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private PmsTargetType targetType;

    @Column(name = "expected_outcome", length = 200)
    private String expectedOutcome;

    @Column(name = "evidence_required", length = 200)
    private String evidenceRequired;
}
