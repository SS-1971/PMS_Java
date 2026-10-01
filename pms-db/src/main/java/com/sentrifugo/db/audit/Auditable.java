package com.sentrifugo.db.audit;

import com.sentrifugo.common.correlation.CorrelationContext;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit + soft-delete columns shared by every PMS-owned table. Java
 * equivalent of the Python services' {@code audit_create()} /
 * {@code stamp_modified()} helpers plus their {@code deleted_on} convention.
 *
 * {@code createdBy} / {@code modifiedBy} are filled by Spring Data JPA
 * auditing ({@link AuditingEntityListener}, wired to the caller's id via an
 * {@code AuditorAware} bean in pms-app); {@code correlationId} is stamped
 * directly here from the request's {@link CorrelationContext} since that has
 * no Spring Data auditing equivalent. Soft-delete fields are set explicitly
 * by the repository/service on delete — nothing here does it automatically.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditable {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false, length = 24, updatable = false)
    private String organisationId;

    @CreatedDate
    @Column(name = "created_on", nullable = false, updatable = false)
    private Instant createdOn;

    @CreatedBy
    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "modified_on")
    private Instant modifiedOn;

    @LastModifiedBy
    @Column(name = "modified_by", length = 64)
    private String modifiedBy;

    @Column(name = "deleted_on")
    private Instant deletedOn;

    @Column(name = "deleted_by", length = 64)
    private String deletedBy;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @PrePersist
    @PreUpdate
    void stampCorrelationId() {
        this.correlationId = CorrelationContext.get();
    }

    public void softDelete(String actorId) {
        this.deletedOn = Instant.now();
        this.deletedBy = actorId;
    }

    public boolean isDeleted() {
        return deletedOn != null;
    }

    public UUID getId() {
        return id;
    }

    public String getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(String organisationId) {
        this.organisationId = organisationId;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getModifiedOn() {
        return modifiedOn;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public Instant getDeletedOn() {
        return deletedOn;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
