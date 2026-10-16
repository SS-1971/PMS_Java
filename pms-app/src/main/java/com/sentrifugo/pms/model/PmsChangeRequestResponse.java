package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One employee change request with the manager's answer (screen 4.2 history). */
public record PmsChangeRequestResponse(
        @JsonProperty("kpi_id") UUID kpiId,
        @JsonProperty("kra_name") String kraName,
        @JsonProperty("kpi_name") String kpiName,
        /** 1 for the first request on this KPI, then 2, 3 ... */
        @JsonProperty("revision_no") int revisionNo,
        @JsonProperty("requested_on") LocalDateTime requestedOn,
        @JsonProperty("reason") String reason,
        @JsonProperty("proposed_target") BigDecimal proposedTarget,
        @JsonProperty("target_at_request") BigDecimal targetAtRequest,
        /** {@code requested} or {@code responded}. */
        @JsonProperty("status") String status,
        /** {@code revised} or {@code unchanged}; null while waiting. */
        @JsonProperty("decision") String decision,
        @JsonProperty("final_target") BigDecimal finalTarget,
        @JsonProperty("manager_remarks") String managerRemarks,
        @JsonProperty("responded_on") LocalDateTime respondedOn,
        @JsonProperty("acknowledged_on") LocalDateTime acknowledgedOn) {
}
