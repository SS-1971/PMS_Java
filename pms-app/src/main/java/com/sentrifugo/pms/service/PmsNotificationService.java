package com.sentrifugo.pms.service;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.db.entity.PmsCycleEntity;
import com.sentrifugo.db.entity.PmsCycleNotificationEntity;
import com.sentrifugo.db.entity.PmsCycleStageEntity;
import com.sentrifugo.db.entity.PmsUserNotificationEntity;
import com.sentrifugo.db.enums.PmsCycleStageType;
import com.sentrifugo.db.enums.PmsNotificationAudience;
import com.sentrifugo.db.enums.PmsNotificationStatus;
import com.sentrifugo.db.repository.PmsCycleNotificationRepository;
import com.sentrifugo.db.repository.PmsCycleStageRepository;
import com.sentrifugo.db.repository.PmsUserNotificationRepository;
import com.sentrifugo.integration.IamEmployeeClient;
import com.sentrifugo.integration.model.IamUserRef;
import com.sentrifugo.pms.model.PmsNotificationReadRequest;
import com.sentrifugo.pms.model.PmsNotificationResponse;
import com.sentrifugo.security.context.PmsUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * In-app notifications (the bell). When a cycle is published, every audience ticked on screen 1.5 is notified:
 * the people who hold the matching grant in IAM each get a notification with the cycle name, period and the
 * goal-setting window. Who counts as which audience follows the grants the super admin gave to the roles.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmsNotificationService {

    private static final String MODULE = "performance_management";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private final IamEmployeeClient iam;
    private final PmsUserNotificationRepository notificationRepository;
    private final PmsCycleNotificationRepository cycleNotificationRepository;
    private final PmsCycleStageRepository stageRepository;

    /** The grant whose holders make up an audience, and the screen their notification opens. */
    private record Target(String permission, String screen) {
    }

    private static Target target(PmsNotificationAudience audience) {
        return switch (audience) {
            case EMPLOYEES -> new Target("my_goals", "my-goals");
            case REPORTING_MANAGERS -> new Target("team_goals", "team-goal-setting");
            case HOD_REVIEWERS -> new Target("goal_approval", "goal-approvals");
            case HR -> new Target("appraisal_history", "team-appraisal");
            case GP -> new Target("goal_template", "goal-approvals");
        };
    }

    /**
     * Notifies each audience once. A failure to reach IAM is recorded against that audience and never fails the
     * publish. Returns the number of notifications written.
     */
    @Transactional
    public int notifyCycleLive(PmsCycleEntity cycle, Set<PmsNotificationAudience> audiences) {
        int total = 0;
        String title = cycle.getName() + " is live";
        String message = buildMessage(cycle);
        int year = cycle.getPeriodStart().getMonthValue() >= 4 ? cycle.getPeriodStart().getYear()
                : cycle.getPeriodStart().getYear() - 1;
        for (PmsNotificationAudience audience : audiences) {
            Target target = target(audience);
            PmsCycleNotificationEntity record = PmsCycleNotificationEntity.builder()
                    .cycle(cycle)
                    .audience(audience)
                    .status(PmsNotificationStatus.PENDING)
                    .build();
            try {
                Set<String> userIds = new LinkedHashSet<>();
                for (IamUserRef u : iam.usersWithPermission(cycle.getOrganisationId(), MODULE, target.permission())) {
                    if (u.userId() != null) {
                        userIds.add(u.userId());
                    }
                }
                List<PmsUserNotificationEntity> rows = new ArrayList<>();
                for (String userId : userIds) {
                    rows.add(PmsUserNotificationEntity.builder()
                            .organisationId(cycle.getOrganisationId())
                            .userId(userId)
                            .cycleId(cycle.getId())
                            .audience(audience)
                            .title(title)
                            .message(message)
                            .linkPath("/pms/" + target.screen() + "/" + year)
                            .build());
                }
                notificationRepository.saveAll(rows);
                record.setSentCount(rows.size());
                record.setStatus(PmsNotificationStatus.SENT);
                record.setSentOn(LocalDateTime.now());
                total += rows.size();
            } catch (DomainException e) {
                log.warn("Could not notify {} for cycle {}: {}", audience, cycle.getId(), e.getMessage());
                record.setStatus(PmsNotificationStatus.FAILED);
                record.setErrorMessage(e.getMessage());
            }
            cycleNotificationRepository.save(record);
        }
        return total;
    }

    /** "FY 2026-27 runs 01-Apr-2026 to 31-Mar-2027. Goal setting is open from 01-May-2026 to 20-May-2026." */
    private String buildMessage(PmsCycleEntity cycle) {
        StringBuilder sb = new StringBuilder();
        sb.append(cycle.getName()).append(" covers ").append(cycle.getPeriodStart().format(DAY)).append(" to ")
                .append(cycle.getPeriodEnd().format(DAY)).append(".");
        for (PmsCycleStageEntity stage : stageRepository.findByCycleId(cycle.getId())) {
            if (stage.getStage() == PmsCycleStageType.GOAL_SETTING && stage.getStartDate() != null
                    && stage.getEndDate() != null) {
                LocalDate s = stage.getStartDate();
                LocalDate e = stage.getEndDate();
                sb.append(" Goal setting is open from ").append(s.format(DAY)).append(" to ").append(e.format(DAY))
                        .append(".");
            }
        }
        return sb.toString();
    }

    // ── the bell ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PmsNotificationResponse mine(PmsUserPrincipal user) {
        List<PmsNotificationResponse.Item> items = notificationRepository
                .findTop50ByOrganisationIdAndUserIdOrderByCreatedDateDesc(user.organisationId(), user.id()).stream()
                .map(n -> new PmsNotificationResponse.Item(n.getId(), n.getTitle(), n.getMessage(), n.getLinkPath(),
                        n.getCreatedDate(), n.getReadOn() != null))
                .toList();
        return new PmsNotificationResponse(
                notificationRepository.countByOrganisationIdAndUserIdAndReadOnIsNull(user.organisationId(),
                        user.id()), items);
    }

    @Transactional
    public PmsNotificationResponse markRead(PmsUserPrincipal user, PmsNotificationReadRequest request) {
        List<UUID> ids = request == null ? null : request.ids();
        List<PmsUserNotificationEntity> rows = ids == null || ids.isEmpty()
                ? notificationRepository.findByOrganisationIdAndUserIdAndReadOnIsNull(user.organisationId(),
                user.id())
                : notificationRepository.findByOrganisationIdAndUserIdAndIdIn(user.organisationId(), user.id(),
                ids);
        LocalDateTime now = LocalDateTime.now();
        rows.stream().filter(n -> n.getReadOn() == null).forEach(n -> n.setReadOn(now));
        notificationRepository.saveAll(rows);
        return mine(user);
    }
}
