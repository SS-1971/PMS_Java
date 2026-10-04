package com.sentrifugo.pms.service;

import com.sentrifugo.common.event.DepartmentDeletedEvent;
import com.sentrifugo.common.event.DepartmentSyncedEvent;
import com.sentrifugo.common.event.DesignationDeletedEvent;
import com.sentrifugo.common.event.DesignationSyncedEvent;
import com.sentrifugo.common.event.PlantDeletedEvent;
import com.sentrifugo.common.event.PlantSyncedEvent;
import com.sentrifugo.pms.db.entity.DepartmentReplicaEntity;
import com.sentrifugo.pms.db.repository.DepartmentReplicaRepository;
import com.sentrifugo.pms.db.entity.DesignationReplicaEntity;
import com.sentrifugo.pms.db.repository.DesignationReplicaRepository;
import com.sentrifugo.pms.db.entity.PlantReplicaEntity;
import com.sentrifugo.pms.db.repository.PlantReplicaRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the IAM domain events {@code pms-messaging} parses — upsert on
 * sync, soft-delete flag on delete. IAM is the system of record; these
 * tables only serve local filtering/display (see
 * {@code IamDomainEventsConfig}'s class doc for the full replica-sync
 * picture).
 */
@Component
public class ReplicaSyncListener {

    private final PlantReplicaRepository plants;
    private final DepartmentReplicaRepository departments;
    private final DesignationReplicaRepository designations;

    public ReplicaSyncListener(PlantReplicaRepository plants, DepartmentReplicaRepository departments,
                               DesignationReplicaRepository designations) {
        this.plants = plants;
        this.departments = departments;
        this.designations = designations;
    }

    @EventListener
    @Transactional
    public void onPlantSynced(PlantSyncedEvent event) {
        if (event.id() == null) {
            return;
        }
        PlantReplicaEntity row = plants.findById(event.id()).orElseGet(
                () -> new PlantReplicaEntity(event.id(), event.organisationId(), event.name(), event.isActive()));
        row.setName(event.name());
        row.setActive(event.isActive());
        row.touch();
        plants.save(row);
    }

    @EventListener
    @Transactional
    public void onPlantDeleted(PlantDeletedEvent event) {
        plants.findById(event.id()).ifPresent(row -> {
            row.setDeleted(true);
            row.touch();
            plants.save(row);
        });
    }

    @EventListener
    @Transactional
    public void onDepartmentSynced(DepartmentSyncedEvent event) {
        if (event.id() == null) {
            return;
        }
        DepartmentReplicaEntity row = departments.findById(event.id()).orElseGet(
                () -> new DepartmentReplicaEntity(event.id(), event.organisationId(), event.name(), event.isActive()));
        row.setName(event.name());
        row.setActive(event.isActive());
        row.touch();
        departments.save(row);
    }

    @EventListener
    @Transactional
    public void onDepartmentDeleted(DepartmentDeletedEvent event) {
        departments.findById(event.id()).ifPresent(row -> {
            row.setDeleted(true);
            row.touch();
            departments.save(row);
        });
    }

    @EventListener
    @Transactional
    public void onDesignationSynced(DesignationSyncedEvent event) {
        if (event.id() == null) {
            return;
        }
        DesignationReplicaEntity row = designations.findById(event.id()).orElseGet(() -> new DesignationReplicaEntity(
                event.id(), event.organisationId(), event.departmentId(), event.name(), event.isActive()));
        row.setName(event.name());
        row.setDepartmentId(event.departmentId());
        row.setActive(event.isActive());
        row.touch();
        designations.save(row);
    }

    @EventListener
    @Transactional
    public void onDesignationDeleted(DesignationDeletedEvent event) {
        designations.findById(event.id()).ifPresent(row -> {
            row.setDeleted(true);
            row.touch();
            designations.save(row);
        });
    }
}
