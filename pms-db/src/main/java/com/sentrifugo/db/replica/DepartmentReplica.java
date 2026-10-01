package com.sentrifugo.db.replica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Local copy of an IAM department, kept current by the domain-events consumer. */
@Entity
@Table(name = "pms_departments")
public class DepartmentReplica {

    @Id
    @Column(length = 24)
    private String id;

    @Column(name = "organisation_id", nullable = false, length = 24)
    private String organisationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "synced_on", nullable = false)
    private Instant syncedOn = Instant.now();

    protected DepartmentReplica() {
    }

    public DepartmentReplica(String id, String organisationId, String name, boolean active) {
        this.id = id;
        this.organisationId = organisationId;
        this.name = name;
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public String getOrganisationId() {
        return organisationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void touch() {
        this.syncedOn = Instant.now();
    }
}
