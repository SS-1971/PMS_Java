package com.sentrifugo.pms.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(name = "pms_kras", schema = "pms")
@SQLDelete(sql = "UPDATE pms.pms_kras SET is_active = false, modified_date = now() WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PmsKraEntity extends BaseEntity<UUID> {

    @Column(name = "organisation_id", nullable = false, length = 24, updatable = false)
    private String organisationId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;
}
