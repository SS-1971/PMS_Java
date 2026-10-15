package com.sentrifugo.db.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
import java.util.UUID;

/** One KPI target changed by a revision request. Weights and KRAs are locked at mid-year. */
@Entity
@Table(
        name = "target_revision_row",
        schema = "pms",
        indexes = @Index(name = "idx_target_revision_row_revision", columnList = "revision_id")
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PmsTargetRevisionRowEntity extends BaseEntity<UUID> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private PmsTargetRevisionEntity revision;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kpi_id", nullable = false)
    private PmsKpiMasterEntity kpi;

    @Column(name = "previous_target", precision = 14, scale = 2)
    private BigDecimal previousTarget;

    @Column(name = "revised_target", nullable = false, precision = 14, scale = 2)
    private BigDecimal revisedTarget;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    /** HOD decision for this KPI: null while waiting, then {@code approved} or {@code returned}. */
    @Column(name = "decision", length = 20)
    private String decision;

    @Column(name = "hod_remarks", length = 500)
    private String hodRemarks;
}
