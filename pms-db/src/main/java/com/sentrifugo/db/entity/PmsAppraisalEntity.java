package com.sentrifugo.db.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import com.sentrifugo.db.enums.PmsHrRatingStatus;
import com.sentrifugo.db.enums.PmsManagerAppraisalStatus;
import com.sentrifugo.db.enums.PmsSelfAppraisalStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One employee year-end appraisal: the self appraisal and the manager rating (screens 3.13 - 3.14). */
@Entity
@Table(
        name = "appraisal",
        schema = "pms",
        uniqueConstraints = @UniqueConstraint(name = "uk_appraisal_assignment", columnNames = "assignment_id"),
        indexes = @Index(name = "idx_appraisal_manager_status", columnList = "manager_status")
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PmsAppraisalEntity extends BaseEntity<UUID> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private PmsGoalAssignmentEntity assignment;

    @Enumerated(EnumType.STRING)
    @Column(name = "self_status", nullable = false, length = 20)
    private PmsSelfAppraisalStatus selfStatus;

    /** Mean of the self ratings, set when the self appraisal is submitted. */
    @Column(name = "self_score", precision = 4, scale = 2)
    private BigDecimal selfScore;

    @Column(name = "self_submitted_on")
    private LocalDateTime selfSubmittedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "manager_status", nullable = false, length = 30)
    private PmsManagerAppraisalStatus managerStatus;

    @Column(name = "recommended_rating")
    private Integer recommendedRating;

    @Column(name = "manager_comments", columnDefinition = "TEXT")
    private String managerComments;

    @Column(name = "manager_submitted_on")
    private LocalDateTime managerSubmittedOn;

    /** The HOD / functional head's overall rating. Written by the HOD review; read by HR and calibration. */
    @Column(name = "hod_rating")
    private Integer hodRating;

    @Column(name = "hod_rated_on")
    private LocalDateTime hodRatedOn;

    /** Null until Corporate HR opens the employee; read as {@code PENDING}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "hr_status", length = 20)
    private PmsHrRatingStatus hrStatus;

    @Column(name = "hr_remarks", columnDefinition = "TEXT")
    private String hrRemarks;

    @Column(name = "hr_submitted_on")
    private LocalDateTime hrSubmittedOn;

    /** Set when HR sends the rated employee on to calibration (screen 1.7). */
    @Column(name = "sent_to_calibration_on")
    private LocalDateTime sentToCalibrationOn;

    /** HR's calibrated final rating and why it differs from the rating it started at (screen 1.9). */
    @Column(name = "calibrated_rating")
    private Integer calibratedRating;

    @Column(name = "calibration_reason", columnDefinition = "TEXT")
    private String calibrationReason;

    /** Set when the calibrated ratings are approved and published; locks the rating. */
    @Column(name = "published_on")
    private LocalDateTime publishedOn;
}
