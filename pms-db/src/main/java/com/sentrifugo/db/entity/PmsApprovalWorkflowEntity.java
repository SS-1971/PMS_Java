package com.sentrifugo.db.entity;

import com.sentrifugo.db.enums.PmsMasterStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Approval Workflow master: the named chain of approvers a cycle uses (screen 1.5 picks one). The steps are kept
 * in order, one approver label per line, e.g. Section Head, Functional Head, Plant HR, Corporate HR, GP.
 */
@Entity
@Table(
        name = "approval_workflow",
        schema = "pms",
        indexes = {
                @Index(name = "idx_approval_workflow_org", columnList = "organisation_id"),
                @Index(name = "idx_approval_workflow_name", columnList = "name")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PmsApprovalWorkflowEntity extends BaseEntity<UUID> {

    @Column(name = "organisation_id", nullable = false)
    private String organisationId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** Approver labels in order, separated by new lines. */
    @Column(name = "steps", nullable = false, columnDefinition = "TEXT")
    private String steps;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PmsMasterStatus status;
}
