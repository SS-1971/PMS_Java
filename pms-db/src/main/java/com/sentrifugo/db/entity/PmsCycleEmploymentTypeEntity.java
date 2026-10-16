package com.sentrifugo.db.entity;

import com.sentrifugo.db.enums.PmsEmploymentScope;
import com.sentrifugo.db.enums.PmsEmploymentType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/** Screen 1.4 - an employment type a cycle applies to, for goal setting or for the appraisal. */
@Entity
@Table(
        name = "pms_cycle_employment_type",
        schema = "pms",
        uniqueConstraints = @UniqueConstraint(name = "uk_pms_cycle_employment_type_cycle_scope_type", columnNames = {"cycle_id", "scope", "employment_type"}),
        indexes = @Index(name = "idx_pms_cycle_employment_type_cycle", columnList = "cycle_id")
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PmsCycleEmploymentTypeEntity extends BaseEntity<UUID> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false)
    private PmsCycleEntity cycle;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20, columnDefinition = "varchar(20) default 'GOAL_SETTING'")
    @Builder.Default
    private PmsEmploymentScope scope = PmsEmploymentScope.GOAL_SETTING;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 30)
    private PmsEmploymentType employmentType;
}
