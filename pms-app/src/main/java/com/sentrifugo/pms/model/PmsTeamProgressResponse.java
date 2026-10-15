package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/** One row of screen 3.9, the team progress dashboard. */
public record PmsTeamProgressResponse(
        @JsonProperty("employee_user_id") String employeeUserId,
        @JsonProperty("employee_name") String employeeName,
        /** True until the HOD has approved the goals, so progress tracking has not opened. */
        @JsonProperty("locked") boolean locked,
        @JsonProperty("kras_on_track") int krasOnTrack,
        @JsonProperty("kras_total") int krasTotal,
        @JsonProperty("attention") List<Attention> attention,
        @JsonProperty("achievement") BigDecimal achievement,
        @JsonProperty("comment") String comment,
        @JsonProperty("overall_rag") String overallRag) {

    public record Attention(
            @JsonProperty("kra_name") String kraName,
            @JsonProperty("rag") String rag) {
    }
}
