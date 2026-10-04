package com.sentrifugo.pms.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Common columns for every PMS-owned table: generated id, Spring Data audit
 * stamps, and the {@code is_active} soft-delete flag.
 *
 * <p>{@code createdBy} / {@code modifiedBy} are filled by Spring Data JPA
 * auditing from the authenticated caller (see the {@code AuditorAware} bean in
 * pms-app) — never from request data. Subclasses must not redeclare any of
 * these fields.
 *
 * <p>Uses {@code @Getter}/{@code @Setter} rather than {@code @Data}: a
 * generated {@code equals/hashCode} over all fields makes two unsaved entities
 * (all-null fields) equal and breaks collections of children.
 */
@Getter
@Setter
@MappedSuperclass
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity<ID extends Serializable> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private ID id;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "modified_by")
    private UUID modifiedBy;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "modified_date")
    private LocalDateTime modifiedDate;

    @Column(name = "is_active", nullable = false, columnDefinition = "boolean DEFAULT true")
    @lombok.Builder.Default
    private Boolean isActive = true;
}
