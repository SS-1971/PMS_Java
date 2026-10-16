package com.sentrifugo.pms.service;

import com.sentrifugo.db.entity.PmsCycleEntity;
import com.sentrifugo.db.entity.PmsCycleStageEntity;
import com.sentrifugo.db.enums.PmsCycleStageType;
import com.sentrifugo.db.enums.PmsCycleStatus;
import com.sentrifugo.db.repository.PmsCycleRepository;
import com.sentrifugo.db.repository.PmsCycleStageRepository;
import com.sentrifugo.pms.model.PmsStageNoticeResponse;
import com.sentrifugo.security.context.PmsUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The stage windows the cycle timeline sets for the people who act in them: managers set goals in the
 * goal-setting window, employees acknowledge in theirs, the HOD approves in theirs. Which of them concern
 * the caller follows from the permissions they hold.
 */
@Service
@RequiredArgsConstructor
public class PmsStageNoticeService {

    private static final String MODULE = "performance_management";

    private final PmsCycleRepository cycleRepository;
    private final PmsCycleStageRepository cycleStageRepository;

    /** One notice type: the stage, who it is for, the permission that makes it theirs, and its switch. */
    private record Kind(PmsCycleStageType stage, String wire, String title, String audience, String permission,
                        Function<PmsCycleEntity, Boolean> audienceSwitch) {
    }

    private static final List<Kind> KINDS = List.of(
            new Kind(PmsCycleStageType.GOAL_SETTING, "goal_setting", "Goal Setting", "manager", "team_goals",
                    PmsCycleEntity::getNotifyManagers),
            new Kind(PmsCycleStageType.EMPLOYEE_ACKNOWLEDGEMENT, "employee_acknowledgement",
                    "Employee Acknowledgement", "employee", "my_goals", PmsCycleEntity::getNotifyEmployees),
            new Kind(PmsCycleStageType.HOD_APPROVAL, "hod_approval", "HOD Approval", "hod", "goal_approval",
                    PmsCycleEntity::getNotifyHod));

    /** The windows of the organisation's active cycle for the year that concern the caller, in flow order. */
    @Transactional(readOnly = true)
    public List<PmsStageNoticeResponse> notices(PmsUserPrincipal user, String financialYear) {
        Optional<PmsCycleEntity> cycle = activeCycle(user.organisationId(), financialYear);
        if (cycle.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        List<PmsCycleStageEntity> stages = cycleStageRepository.findByCycleId(cycle.get().getId());
        List<PmsStageNoticeResponse> out = new ArrayList<>();
        for (Kind kind : KINDS) {
            if (!user.hasPermission(MODULE, kind.permission())) {
                continue;
            }
            for (PmsCycleStageEntity stage : stages) {
                if (stage.getStage() != kind.stage() || stage.getEndDate() == null) {
                    continue;
                }
                LocalDate start = stage.getStartDate() != null ? stage.getStartDate() : stage.getEndDate();
                String state = today.isBefore(start) ? "upcoming" : today.isAfter(stage.getEndDate()) ? "closed" : "open";
                Long daysLeft = switch (state) {
                    case "upcoming" -> ChronoUnit.DAYS.between(today, start);
                    case "open" -> ChronoUnit.DAYS.between(today, stage.getEndDate()) + 1;
                    default -> null;
                };
                boolean notify = Boolean.TRUE.equals(stage.getNotificationEnabled())
                        && Boolean.TRUE.equals(kind.audienceSwitch().apply(cycle.get())) && !"closed".equals(state);
                out.add(new PmsStageNoticeResponse(kind.wire(), kind.title(), kind.audience(),
                        cycle.get().getName(), start, stage.getEndDate(), state, daysLeft, notify));
            }
        }
        return out;
    }

    /** The most recently published active cycle whose period starts in the financial year. */
    private Optional<PmsCycleEntity> activeCycle(String organisationId, String financialYear) {
        Integer start = startYear(financialYear);
        if (start == null) {
            return Optional.empty();
        }
        return cycleRepository.findByOrganisationIdAndStatus(organisationId, PmsCycleStatus.ACTIVE).stream()
                .filter(c -> {
                    LocalDate periodStart = c.getPeriodStart();
                    int cycleYear = periodStart.getMonthValue() >= 4 ? periodStart.getYear()
                            : periodStart.getYear() - 1;
                    return cycleYear == start;
                })
                .max(Comparator.comparing(PmsCycleEntity::getPublishedOn,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    /** {@code 2026-27} to 2026; null when it is not in that shape. */
    private static Integer startYear(String financialYear) {
        if (financialYear == null || financialYear.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(financialYear.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
