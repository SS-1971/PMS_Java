package com.sentrifugo.pms.service;

import com.sentrifugo.db.entity.PmsGoalAssignmentEntity;
import com.sentrifugo.db.entity.PmsGoalSheetHistoryEntity;
import com.sentrifugo.db.repository.PmsGoalSheetHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Writes the step-by-step trail of a goal sheet: who did what and when, with the target before and after.
 * The goal-setting, mid-year and HOD actions call it; the HOD history and trail screens read it back.
 */
@Component
@RequiredArgsConstructor
public class PmsSheetHistory {

    public static final String GOAL_SHEET = "goal_sheet";
    public static final String TARGET_REVISION = "target_revision";

    public static final String MANAGER = "manager";
    public static final String EMPLOYEE = "employee";
    public static final String HOD = "hod";

    private final PmsGoalSheetHistoryRepository repository;

    /** One step. Pass nulls for the KPI fields when the step is about the sheet as a whole. */
    public void record(PmsGoalAssignmentEntity assignment, String requestType, UUID requestRef, String action,
                       String actorUserId, String actorName, String actorRole, UUID kpiId, String kpiName,
                       String unit, BigDecimal before, BigDecimal after, int moreChanges, String summary,
                       String remarks) {
        repository.save(PmsGoalSheetHistoryEntity.builder()
                .assignment(assignment)
                .requestType(requestType)
                .requestRef(requestRef)
                .action(action)
                .actorUserId(actorUserId)
                .actorName(actorName)
                .actorRole(actorRole)
                .kpiId(kpiId)
                .kpiName(kpiName)
                .unit(unit)
                .targetBefore(before)
                .targetAfter(after)
                .moreChanges(moreChanges)
                .summary(trim(summary, 500))
                .remarks(trim(remarks, 500))
                .occurredOn(LocalDateTime.now())
                .build());
    }

    /** A step about the whole goal sheet, with no KPI. */
    public void sheet(PmsGoalAssignmentEntity assignment, String action, String actorUserId, String actorName,
                      String actorRole, String summary, String remarks) {
        record(assignment, GOAL_SHEET, null, action, actorUserId, actorName, actorRole, null, null, null, null,
                null, 0, summary, remarks);
    }

    /** Plain number without trailing zeros, for the summary lines. */
    public static String num(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
