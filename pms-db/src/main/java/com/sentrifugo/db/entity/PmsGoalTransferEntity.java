package com.sentrifugo.db.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import com.sentrifugo.db.enums.PmsTransferStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * An employee who moved into a manager team part-way through the year (screens 3.6 and 3.8).
 * HR records the transfer; the new manager then sets the employee period 2 KRAs.
 */
@Entity
@Table(
        name = "goal_transfer",
        schema = "pms",
        uniqueConstraints = @UniqueConstraint(name = "uk_goal_transfer_employee_fy",
                columnNames = {"organisation_id", "employee_user_id", "financial_year"}),
        indexes = @Index(name = "idx_goal_transfer_manager", columnList = "new_manager_user_id")
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PmsGoalTransferEntity extends BaseEntity<UUID> {

    @Column(name = "organisation_id", nullable = false)
    private String organisationId;

    @Column(name = "employee_user_id", nullable = false)
    private String employeeUserId;

    @Column(name = "financial_year", nullable = false, length = 20)
    private String financialYear;

    /** Snapshot of the employee, so the sheet needs no IAM lookup. */
    @Column(name = "employee_name", length = 200)
    private String employeeName;

    @Column(name = "emp_code", length = 50)
    private String empCode;

    @Column(name = "new_manager_user_id", nullable = false)
    private String newManagerUserId;

    @Column(name = "previous_manager_name", length = 200)
    private String previousManagerName;

    @Column(name = "from_department", length = 200)
    private String fromDepartment;

    @Column(name = "to_department", length = 200)
    private String toDepartment;

    /** E.g. "Apr - Sep 2026". */
    @Column(name = "previous_period_label", length = 100)
    private String previousPeriodLabel;

    @Column(name = "joined_on", nullable = false)
    private LocalDate joinedOn;

    @Column(name = "recorded_by", length = 200)
    private String recordedBy;

    @Column(name = "recorded_on")
    private LocalDate recordedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PmsTransferStatus status;

    @Column(name = "reason", length = 100)
    private String reason;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;
}
