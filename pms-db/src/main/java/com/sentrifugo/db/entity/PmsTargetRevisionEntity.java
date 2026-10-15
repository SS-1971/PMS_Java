package com.sentrifugo.db.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import com.sentrifugo.db.enums.PmsRevisionSource;
import com.sentrifugo.db.enums.PmsRevisionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/** A target revision the manager raised at mid-year, waiting for the HOD (screen 3.12). */
@Entity
@Table(
        name = "target_revision",
        schema = "pms",
        indexes = @Index(name = "idx_target_revision_assignment", columnList = "assignment_id")
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PmsTargetRevisionEntity extends BaseEntity<UUID> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private PmsGoalAssignmentEntity assignment;

    @Column(name = "attachment_name", length = 255)
    private String attachmentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PmsRevisionStatus status;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "requested_on", nullable = false)
    private LocalDateTime requestedOn;

    /** Null on rows from before this column existed, which are HOD-approved revisions. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 30)
    private PmsRevisionSource source;

    /** Name of the HOD who approved the revision, or the manager who revised the targets. */
    @Column(name = "approved_by", length = 200)
    private String approvedBy;

    @Column(name = "approved_on")
    private LocalDateTime approvedOn;

    /** When the employee acknowledged the revised targets (screen 4.5). Null while it is waiting for them. */
    @Column(name = "acknowledged_on")
    private LocalDateTime acknowledgedOn;
}
