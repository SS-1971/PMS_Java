package com.sentrifugo.db.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import com.sentrifugo.db.enums.PmsAppraisalLineType;

import java.util.UUID;

/** One rated item of an appraisal: a KPI, a manager competency or an HR competency. */
@Entity
@Table(
        name = "appraisal_line",
        schema = "pms",
        uniqueConstraints = @UniqueConstraint(name = "uk_appraisal_line_ref",
                columnNames = {"appraisal_id", "line_type", "ref_id"}),
        indexes = @Index(name = "idx_appraisal_line_appraisal", columnList = "appraisal_id")
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PmsAppraisalLineEntity extends BaseEntity<UUID> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appraisal_id", nullable = false)
    private PmsAppraisalEntity appraisal;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 20)
    private PmsAppraisalLineType lineType;

    /** The KPI id or the competency id, per the line type. */
    @Column(name = "ref_id", nullable = false)
    private UUID refId;

    @Column(name = "self_rating")
    private Integer selfRating;

    @Column(name = "manager_rating")
    private Integer managerRating;

    /** The HOD's rating of a manager competency; written by the HOD review. */
    @Column(name = "hod_rating")
    private Integer hodRating;

    /** Corporate HR's rating of an HR competency. Its remark is kept in {@code comment}. */
    @Column(name = "hr_rating")
    private Integer hrRating;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "attachment_name", length = 255)
    private String attachmentName;

    /** The employee own figure for a KRA line in the self appraisal. */
    @Column(name = "actual", precision = 14, scale = 2)
    private java.math.BigDecimal actual;

    /** What the employee achieved, in their words (KRA lines). */
    @Column(name = "evidence", length = 500)
    private String evidence;

    /** The employee comment on a competency line; kept apart from the manager and HR comments. */
    @Column(name = "self_comment", length = 500)
    private String selfComment;
}
