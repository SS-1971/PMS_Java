package com.sentrifugo.pms.db.enums;

import com.sentrifugo.common.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

/**
 * Persisted by constant name ({@code @Enumerated(EnumType.STRING)}); {@link #wire()}
 * is the lowercase form used by the REST API contract.
 */
public enum PmsCycleStage {
    GOAL_SETTING("goal_setting"),
    EMPLOYEE_ACKNOWLEDGEMENT("employee_acknowledgement"),
    HOD_APPROVAL("hod_approval"),
    PROGRESS_TRACKING("progress_tracking"),
    MID_YEAR_REVIEW("mid_year_review"),
    SELF_APPRAISAL("self_appraisal"),
    MANAGER_APPRAISAL("manager_appraisal"),
    HOD_REVIEW("hod_review"),
    CALIBRATION_FINAL_APPROVAL("calibration_final_approval");

    private final String wire;

    PmsCycleStage(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static PmsCycleStage fromWire(String wire) {
        return Arrays.stream(values())
                .filter(v -> v.wire.equals(wire))
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        "stage must be one of: goal_setting, employee_acknowledgement, hod_approval, progress_tracking, mid_year_review, self_appraisal, manager_appraisal, hod_review, calibration_final_approval", "VALIDATION_ERROR",
                        HttpStatus.UNPROCESSABLE_CONTENT));
    }
}
