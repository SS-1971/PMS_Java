package com.sentrifugo.db.config;

import com.sentrifugo.db.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "pms_kras")
public class Kra extends Auditable {

    @Column(nullable = false, length = 100)
    private String name;

    protected Kra() {
    }

    public Kra(String organisationId, String name) {
        setOrganisationId(organisationId);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
