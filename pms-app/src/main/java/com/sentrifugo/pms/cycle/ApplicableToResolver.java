package com.sentrifugo.pms.cycle;

import com.sentrifugo.db.cycle.CycleApplicability;
import com.sentrifugo.db.replica.PlantReplicaRepository;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code "All Plants"} when every active plant in the org is covered,
 * comma-joined plant names otherwise, {@code ""} for an incomplete draft
 * (no plants chosen yet) — per the PMS Cycle contract's #1/#2 description.
 */
@Component
public class ApplicableToResolver {

    private final PlantReplicaRepository plants;

    public ApplicableToResolver(PlantReplicaRepository plants) {
        this.plants = plants;
    }

    public String resolve(String organisationId, CycleApplicability applicability) {
        List<String> plantIds = applicability.getPlantIds();
        if (plantIds.isEmpty()) {
            return "";
        }
        Set<String> allActiveIds = plants.findByOrganisationIdAndDeletedFalseOrderByNameAsc(organisationId).stream()
                .map(p -> p.getId()).collect(Collectors.toSet());
        if (!allActiveIds.isEmpty() && new HashSet<>(plantIds).equals(allActiveIds)) {
            return "All Plants";
        }
        return plants.findByIdInAndOrganisationId(plantIds, organisationId).stream()
                .map(p -> p.getName())
                .sorted()
                .collect(Collectors.joining(", "));
    }
}
