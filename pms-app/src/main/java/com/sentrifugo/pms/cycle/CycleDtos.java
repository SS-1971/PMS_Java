package com.sentrifugo.pms.cycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTO family for {@code pms_cycles} (PMS Cycle contract, screens 1.1–1.6). */
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

    // `notify` is illegal as a record component (clashes with Object.notify()),
    // so the Java name is `notifyEnabled` with the wire name pinned back to
    // `notify` via @JsonProperty.
    public record StageEntry(@NotBlank String stage, LocalDate startDate, LocalDate endDate,
                             @JsonProperty("notify") boolean notifyEnabled) {
    }

    public record Applicability(
            List<String> plantIds,
            boolean allDepartments,
            List<String> departmentIds,
            List<String> employmentTypes,
            @Min(0) int minServiceMonths,
            LocalDate serviceAsOn,
            boolean excludeProbation,
            boolean excludeNoticePeriod) {

        public Applicability {
            plantIds = plantIds != null ? plantIds : List.of();
            departmentIds = departmentIds != null ? departmentIds : List.of();
            employmentTypes = employmentTypes != null ? employmentTypes : List.of();
        }
    }

    public record Finalize(
            UUID ratingScaleId,
            boolean notifyManagers,
            boolean notifyEmployees,
            boolean notifyHod,
            boolean notifyHr) {
    }

    // Same `finalize` clash as above (Object.finalize()) — Java name
    // `finalizeSettings`, wire name pinned back to `finalize`.
    public record UpsertRequest(
            @NotNull @Valid Basic basic,
            @NotNull List<@Valid StageEntry> stages,
            @NotNull @Valid Applicability applicability,
            @JsonProperty("finalize") @NotNull @Valid Finalize finalizeSettings) {
    }

    public record CycleDto(UUID id, String cycleCode, String status, LocalDate createdOn, LocalDate publishedOn,
                           String applicableTo, Basic basic, List<StageEntry> stages, Applicability applicability,
                           @JsonProperty("finalize") Finalize finalizeSettings) {
    }

    public record ListItem(UUID id, String cycleCode, String name, String type, LocalDate periodStart,
                           LocalDate periodEnd, String applicableTo, String status, LocalDate createdOn) {
    }

    public record Summary(long all, long draft, long active, long closed, long cancelled) {
    }

    public record ListResponse(List<ListItem> items, long total, Summary summary) {
    }

    public record NotificationEntry(String audience, int sent) {
    }

    public record ActivationDto(CycleDto cycle, LocalDate publishedOn, List<NotificationEntry> notifications) {
    }

    public record EligibleEmployee(String employeeId, String employeeCode, String name, String department,
                                   String plant, String employmentType, Integer serviceMonths) {
    }

    public record EligibilityPreview(int totalEligible, int excludedProbation, int excludedNoticePeriod,
                                     int excludedMinService, List<EligibleEmployee> items) {
    }
}
