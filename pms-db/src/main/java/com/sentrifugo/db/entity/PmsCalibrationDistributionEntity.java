package com.sentrifugo.db.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/** HR's normalization target for one rating level in one financial year (screen 1.9). */
@Entity
@Table(
        name = "calibration_distribution",
        schema = "pms",
        uniqueConstraints = @UniqueConstraint(name = "uk_calibration_distribution",
                columnNames = {"organisation_id", "financial_year", "rating"})
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PmsCalibrationDistributionEntity extends BaseEntity<UUID> {

    @Column(name = "organisation_id", nullable = false)
    private String organisationId;

    @Column(name = "financial_year", nullable = false, length = 20)
    private String financialYear;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    /** Share of employees HR wants at this rating, 0-100. */
    @Column(name = "hr_percent", nullable = false)
    private Integer hrPercent;
}
