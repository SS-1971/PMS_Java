package com.sentrifugo.db.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import com.sentrifugo.db.enums.PmsTransferKraSource;

import java.math.BigDecimal;
import java.util.UUID;

/** One KRA row of a transfer sheet. Period 1 rows are frozen at the transfer date; period 2 rows are editable. */
@Entity
@Table(
        name = "goal_transfer_kra",
        schema = "pms",
        indexes = @Index(name = "idx_goal_transfer_kra_transfer", columnList = "transfer_id")
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PmsGoalTransferKraEntity extends BaseEntity<UUID> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private PmsGoalTransferEntity transfer;

    /** 1 = before the transfer, 2 = after. */
    @Column(name = "period", nullable = false)
    private int period;

    @Column(name = "kra_name", nullable = false, length = 200)
    private String kraName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private PmsTransferKraSource source;

    @Column(name = "source_label", length = 100)
    private String sourceLabel;

    @Column(name = "weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(name = "target", precision = 14, scale = 2)
    private BigDecimal target;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "display_order")
    private Integer displayOrder;
}
