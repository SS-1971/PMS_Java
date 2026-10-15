package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Create / update payload for a cycle (the four-step wizard, screens 1.2-1.5). {@code organisation_id},
 * {@code cycle_code}, status and the audit columns are never accepted from the client.
 *
 * <p>Draft saves are partial: {@code stages}, {@code applicability} and {@code finalize} may be omitted
 * (left unchanged) and are only checked for completeness on publish. A supplied list replaces the stored one.
 */
public record PmsCycleRequest(
        @JsonProperty("basic") @NotNull @Valid Basic basic,
        @JsonProperty("stages") List<@Valid Stage> stages,
        @JsonProperty("applicability") @Valid Applicability applicability,
        @JsonProperty("finalize") @Valid Finalize finalizeSettings,
        @JsonProperty("current_step") @Min(1) @Max(4) Integer currentStep,
        @JsonProperty("completed_step") @Min(0) @Max(4) Integer completedStep) {

    public record Basic(
            @JsonProperty("name") @NotBlank @Size(max = 200) String name,
            @JsonProperty("description") @Size(max = 2000) String description,
            @JsonProperty("type") @NotBlank String type,
            @JsonProperty("period_start") @NotNull LocalDate periodStart,
            @JsonProperty("period_end") @NotNull LocalDate periodEnd) {
    }

    /** One timeline row (screen 1.3). {@code stage} is e.g. {@code goal_setting}. */
    public record Stage(
            @JsonProperty("stage") @NotBlank String stage,
            @JsonProperty("start_date") LocalDate startDate,
            @JsonProperty("end_date") LocalDate endDate,
            @JsonProperty("notify") Boolean notifyEnabled) {
    }

    /**
     * Screen 1.4. Plant and department ids are Sentrifugo ids; employment types are
     * {@code permanent | contract | probationary | trainee}. Who gets goals ({@code goal_setting}) and who is
     * rated ({@code appraisal}) carry their own employment types and minimum service.
     */
    public record Applicability(
            @JsonProperty("all_plants") Boolean allPlants,
            @JsonProperty("plant_ids") List<String> plantIds,
            @JsonProperty("all_departments") Boolean allDepartments,
            @JsonProperty("department_ids") List<String> departmentIds,
            @JsonProperty("goal_setting") @Valid GoalSetting goalSetting,
            @JsonProperty("appraisal") @Valid Appraisal appraisal,
            @JsonProperty("exclude_notice_period") Boolean excludeNoticePeriod) {
    }

    /** Goal Setting - who gets goals. */
    public record GoalSetting(
            @JsonProperty("employment_types") List<String> employmentTypes,
            @JsonProperty("min_service_months") @Min(0) Integer minServiceMonths) {
    }

    /** Appraisal - who is rated: service must be complete on or before {@code service_as_on}. */
    public record Appraisal(
            @JsonProperty("employment_types") List<String> employmentTypes,
            @JsonProperty("min_service_months") @Min(0) Integer minServiceMonths,
            @JsonProperty("service_as_on") LocalDate serviceAsOn) {
    }

    /** Screen 1.5. */
    public record Finalize(
            @JsonProperty("rating_scale_id") UUID ratingScaleId,
            @JsonProperty("notify_managers") Boolean notifyManagers,
            @JsonProperty("notify_employees") Boolean notifyEmployees,
            @JsonProperty("notify_hod") Boolean notifyHod,
            @JsonProperty("notify_hr") Boolean notifyHr,
            @JsonProperty("notify_gp") Boolean notifyGp,
            @JsonProperty("approval_workflow_id") UUID approvalWorkflowId) {
    }
}
