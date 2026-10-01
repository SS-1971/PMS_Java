package com.sentrifugo.db.cycle;

import com.sentrifugo.common.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

/**
 * The fixed set of cycle stages, in process order (enum declaration order
 * doubles as that order — {@link #values()} is never reshuffled).
 */
public enum CycleStage {
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

    CycleStage(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static CycleStage fromWire(String wire) {
        return Arrays.stream(values())
                .filter(v -> v.wire.equals(wire))
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        "Unknown stage '" + wire + "'. Must be one of: "
                                + String.join(", ", Arrays.stream(values()).map(CycleStage::wire).toList()),
                        "VALIDATION_ERROR", HttpStatus.UNPROCESSABLE_CONTENT));
    }
}
