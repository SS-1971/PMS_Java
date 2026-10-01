package com.sentrifugo.pms.lookups;

import com.sentrifugo.db.replica.DepartmentReplicaRepository;
import com.sentrifugo.db.replica.DesignationReplicaRepository;
import com.sentrifugo.db.replica.PlantReplicaRepository;
import com.sentrifugo.security.context.PmsUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints 10–12 (PMS Cycle contract) / 9, 23 (PMS Configuration contract).
 * Plants / departments / designations are local replicas kept current by
 * {@link ReplicaSyncListener}; KPI units are a small fixed list, same as
 * IAM's own small enumerated lookups (e.g. the ACL list).
 */
@RestController
@RequestMapping("/pms/lookups")
public class LookupsController {

    /** Managed list rather than free text — see the PMS Configuration contract's
     * open question #2; revisit if orgs need to add their own units. */
    private static final List<String> KPI_UNITS = List.of(
            "TPD", "kcal/kg", "%", "kWh/t", "Count", "Months", "MPa", "Hours", "Days", "Tonnes", "INR", "Score");

    private final PlantReplicaRepository plants;
    private final DepartmentReplicaRepository departments;
    private final DesignationReplicaRepository designations;

    public LookupsController(PlantReplicaRepository plants, DepartmentReplicaRepository departments,
                             DesignationReplicaRepository designations) {
        this.plants = plants;
        this.departments = departments;
        this.designations = designations;
    }

    @GetMapping("/plants")
    public List<PlantDto> plants(@AuthenticationPrincipal PmsUserPrincipal user) {
        return plants.findByOrganisationIdAndDeletedFalseOrderByNameAsc(user.organisationId()).stream()
                .map(p -> new PlantDto(p.getId(), p.getName()))
                .toList();
    }

    @GetMapping("/departments")
    public List<DepartmentDto> departments(@AuthenticationPrincipal PmsUserPrincipal user) {
        return departments.findByOrganisationIdAndDeletedFalseOrderByNameAsc(user.organisationId()).stream()
                .map(d -> new DepartmentDto(d.getId(), d.getName()))
                .toList();
    }

    @GetMapping("/designations")
    public List<DesignationDto> designations(@AuthenticationPrincipal PmsUserPrincipal user,
                                             @RequestParam(name = "department_id", required = false) String departmentId) {
        var rows = (departmentId == null || departmentId.isBlank())
                ? designations.findByOrganisationIdAndDeletedFalseOrderByNameAsc(user.organisationId())
                : designations.findByOrganisationIdAndDepartmentIdAndDeletedFalseOrderByNameAsc(
                        user.organisationId(), departmentId);
        return rows.stream().map(d -> new DesignationDto(d.getId(), d.getName(), d.getDepartmentId())).toList();
    }

    @GetMapping("/kpi-units")
    public List<String> kpiUnits() {
        return KPI_UNITS;
    }
}
