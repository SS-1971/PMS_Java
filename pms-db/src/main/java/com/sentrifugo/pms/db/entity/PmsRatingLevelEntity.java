package com.sentrifugo.pms.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

/** One rating value on a scale; {@code rating} is fixed once seeded. */
@Entity
@Table(name = "pms_rating_levels", schema = "pms")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PmsRatingLevelEntity extends BaseEntity<UUID> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scale_id", nullable = false)
    private PmsRatingScaleEntity scale;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "label", nullable = false, length = 40)
    private String label;

    @Column(name = "definition", length = 200)
    private String definition;

    @Column(name = "score_min", nullable = false, precision = 4, scale = 2)
    private BigDecimal scoreMin;

    @Column(name = "score_max", nullable = false, precision = 4, scale = 2)
    private BigDecimal scoreMax;

    @Column(name = "color", nullable = false, length = 7)
    private String color;
}
