package com.sentrifugo.pms.service;

import com.sentrifugo.pms.db.repository.DepartmentReplicaRepository;
import com.sentrifugo.pms.db.repository.DesignationReplicaRepository;
import com.sentrifugo.pms.db.repository.PlantReplicaRepository;
import com.sentrifugo.pms.model.lookup.DepartmentDto;
import com.sentrifugo.pms.model.lookup.DesignationDto;
import com.sentrifugo.pms.model.lookup.PlantDto;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Plants / departments / designations are local replicas kept current by
 * {@link ReplicaSyncListener}; KPI units are a small fixed list, same as
 * IAM's own small enumerated lookups (e.g. the ACL list).
 */
@Service
public class LookupService {

    /** Managed list rather than free text — see the PMS Configuration contract's
     * open question #2; revisit if orgs need to add their own units. */
    private static final List<String> KPI_UNITS = List.of(
            "TPD", "kcal/kg", "%", "kWh/t", "Count", "Months", "MPa", "Hours", "Days", "Tonnes", "INR", "Score");

    private final PlantReplicaRepository plants;
    private final DepartmentReplicaRepository departments;
    private final DesignationReplicaRepository designations;

    public LookupService(PlantReplicaRepository plants, DepartmentReplicaRepository departments,
                         DesignationReplicaRepository designations) {
        this.plants = plants;
        this.departments = departments;
        this.designations = designations;
    }

    public List<PlantDto> listPlants(String organisationId) {
        return plants.findByOrganisationIdAndDeletedFalseOrderByNameAsc(organisationId).stream()
                .map(p -> new PlantDto(p.getId(), p.getName()))
                .toList();
    }

    public List<DepartmentDto> listDepartments(String organisationId) {
        return departments.findByOrganisationIdAndDeletedFalseOrderByNameAsc(organisationId).stream()
                .map(d -> new DepartmentDto(d.getId(), d.getName()))
                .toList();
    }

    public List<DesignationDto> listDesignations(String organisationId, String departmentId) {
        var rows = (departmentId == null || departmentId.isBlank())
                ? designations.findByOrganisationIdAndDeletedFalseOrderByNameAsc(organisationId)
                : designations.findByOrganisationIdAndDepartmentIdAndDeletedFalseOrderByNameAsc(
                        organisationId, departmentId);
        return rows.stream().map(d -> new DesignationDto(d.getId(), d.getName(), d.getDepartmentId())).toList();
    }

    public List<String> listKpiUnits() {
        return KPI_UNITS;
    }
}
