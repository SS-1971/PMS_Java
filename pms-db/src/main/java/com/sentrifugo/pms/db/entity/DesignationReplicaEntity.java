package com.sentrifugo.pms.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Local copy of an IAM record, kept current by pms-messaging's domain-events
 * consumer. IAM is the system of record; the id is IAM's opaque 24-char id, so
 * this does not extend {@link BaseEntity}. {@code active} mirrors IAM's own flag
 * and {@code deleted} is the replica's tombstone.
 */
@Entity
@Table(name = "pms_designations", schema = "pms")
@Getter
@Setter
@NoArgsConstructor
public class DesignationReplicaEntity {

    @Id
    @Column(name = "id", length = 24)
    private String id;

    @Column(name = "organisation_id", nullable = false, length = 24)
    private String organisationId;

    @Column(name = "department_id", length = 24)
    private String departmentId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "synced_on", nullable = false)
    private LocalDateTime syncedOn = LocalDateTime.now();

    public DesignationReplicaEntity(String id, String organisationId, String departmentId, String name,
                                    boolean active) {
        this.id = id;
        this.organisationId = organisationId;
        this.departmentId = departmentId;
        this.name = name;
        this.active = active;
    }

    public void touch() {
        this.syncedOn = LocalDateTime.now();
    }
}
