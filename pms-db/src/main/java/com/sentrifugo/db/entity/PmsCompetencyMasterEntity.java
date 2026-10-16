package com.sentrifugo.db.entity;

import com.sentrifugo.db.enums.PmsCompetencyType;
import com.sentrifugo.db.enums.PmsMasterStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/** Screen 2.9 - competency master. */
@Entity
@Table(
        name = "competency_master",
        schema = "pms",
        indexes = {
                @Index(name = "idx_competency_master_org", columnList = "organisation_id"),
                @Index(name = "idx_competency_master_category", columnList = "category"),
                @Index(name = "idx_competency_master_status", columnList = "status"),
                @Index(name = "idx_competency_master_name", columnList = "name")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PmsCompetencyMasterEntity extends BaseEntity<UUID> {

    @Column(name = "organisation_id", nullable = false)
    private String organisationId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    /** Who rates it: HR or the manager. Drives the appraisal screens and the goal template step. */
    @Enumerated(EnumType.STRING)
    @Column(name = "competency_type", nullable = false, length = 20,
            columnDefinition = "varchar(20) default 'MANAGER'")
    @Builder.Default
    private PmsCompetencyType competencyType = PmsCompetencyType.MANAGER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PmsMasterStatus status;
}
