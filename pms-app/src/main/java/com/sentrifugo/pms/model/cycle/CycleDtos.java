package com.sentrifugo.pms.model.cycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sentrifugo.pms.db.dto.PmsCycleApplicabilityDto;
import com.sentrifugo.pms.db.dto.PmsCycleFinalizeDto;
import com.sentrifugo.pms.db.dto.PmsCycleNotificationDto;
import com.sentrifugo.pms.db.dto.PmsCycleStageDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * API DTO family for {@code pms_cycles} (PMS Cycle contract, screens 1.1–1.6).
 * The stage / applicability / finalize / notification shapes are persistence
 * DTOs in pms-db, shared by the request and response sides.
 */
public final class CycleDtos {

    private CycleDtos() {
    }

    public record Basic(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String description,
            @NotBlank String type,
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd) {
    }

    // `finalize` clashes with Object.finalize(), so the Java name is
    // `finalizeSettings` with the wire name pinned back to `finalize`.
    public record UpsertRequest(
            @NotNull @Valid Basic basic,
            @NotNull List<@Valid PmsCycleStageDto> stages,
            @NotNull @Valid PmsCycleApplicabilityDto applicability,
            @JsonProperty("finalize") @NotNull @Valid PmsCycleFinalizeDto finalizeSettings) {
    }

    public record CycleDto(UUID id, String cycleCode, String status, LocalDate createdOn, LocalDate publishedOn,
                           String applicableTo, Basic basic, List<PmsCycleStageDto> stages,
                           PmsCycleApplicabilityDto applicability,
                           @JsonProperty("finalize") PmsCycleFinalizeDto finalizeSettings) {
    }

    public record ListItem(UUID id, String cycleCode, String name, String type, LocalDate periodStart,
                           LocalDate periodEnd, String applicableTo, String status, LocalDate createdOn) {
    }

    public record Summary(long all, long draft, long active, long closed, long cancelled) {
    }

    public record ListResponse(List<ListItem> items, long total, Summary summary) {
    }

    public record ActivationDto(CycleDto cycle, LocalDate publishedOn, List<PmsCycleNotificationDto> notifications) {
    }

    public record EligibleEmployee(String employeeId, String employeeCode, String name, String department,
                                   String plant, String employmentType, Integer serviceMonths) {
    }

    public record EligibilityPreview(int totalEligible, int excludedProbation, int excludedNoticePeriod,
                                     int excludedMinService, List<EligibleEmployee> items) {
    }
}
