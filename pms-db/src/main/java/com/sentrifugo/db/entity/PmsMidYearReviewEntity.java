package com.sentrifugo.db.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import com.sentrifugo.db.enums.PmsMidYearStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/** The manager mid-year review of one goal sheet (screens 3.10 - 3.11). */
@Entity
@Table(
        name = "mid_year_review",
        schema = "pms",
        uniqueConstraints = @UniqueConstraint(name = "uk_mid_year_review_assignment", columnNames = "assignment_id")
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PmsMidYearReviewEntity extends BaseEntity<UUID> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private PmsGoalAssignmentEntity assignment;

    @Column(name = "discussion_notes", columnDefinition = "TEXT")
    private String discussionNotes;

    @Column(name = "revision_required")
    private Boolean revisionRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PmsMidYearStatus status;

    @Column(name = "reviewed_on")
    private LocalDateTime reviewedOn;
}
