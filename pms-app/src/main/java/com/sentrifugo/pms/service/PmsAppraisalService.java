package com.sentrifugo.pms.service;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.db.entity.PmsAppraisalEntity;
import com.sentrifugo.db.entity.PmsAppraisalLineEntity;
import com.sentrifugo.db.entity.PmsGoalAssignmentEntity;
import com.sentrifugo.db.entity.PmsGoalAssignmentTargetEntity;
import com.sentrifugo.db.entity.PmsGoalProgressEntity;
import com.sentrifugo.db.entity.PmsGoalTemplateCompetencyEntity;
import com.sentrifugo.db.enums.PmsAppraisalLineType;
import com.sentrifugo.db.enums.PmsAssignmentStatus;
import com.sentrifugo.db.enums.PmsManagerAppraisalStatus;
import com.sentrifugo.db.enums.PmsProgressPeriod;
import com.sentrifugo.db.enums.PmsSelfAppraisalStatus;
import com.sentrifugo.db.repository.PmsAppraisalLineRepository;
import com.sentrifugo.db.repository.PmsAppraisalRepository;
import com.sentrifugo.db.repository.PmsGoalAssignmentRepository;
import com.sentrifugo.db.repository.PmsGoalTemplateCompetencyRepository;
import com.sentrifugo.integration.IamEmployeeClient;
import com.sentrifugo.integration.model.IamEmployee;
import com.sentrifugo.pms.model.PmsManagerAppraisalResponse;
import com.sentrifugo.pms.model.PmsManagerAppraisalSaveRequest;
import com.sentrifugo.pms.model.PmsSelfAppraisalRequest;
import com.sentrifugo.pms.model.PmsSelfAppraisalResponse;
import com.sentrifugo.pms.model.PmsTeamAppraisalResponse;
import com.sentrifugo.security.context.PmsUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Year-end appraisal: the employee's self appraisal and the manager's rating of the team (screens 3.13 and
 * 3.14). The manager rates KRAs and the manager competencies; HR competencies are shown for context only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmsAppraisalService {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;
    /** A manager rating this far from the self rating needs a comment. */
    private static final int COMMENT_GAP = 2;
    private static final int MIN_COMMENT = 5;

    private final IamEmployeeClient iam;
    private final PmsTeamAccess access;
    private final PmsGoalAssignmentRepository assignmentRepository;
    private final PmsAppraisalRepository appraisalRepository;
    private final PmsAppraisalLineRepository lineRepository;
    private final PmsGoalTemplateCompetencyRepository templateCompetencyRepository;

    // ── employee ─────────────────────────────────────────────────────────────

    /** Saves the signed-in employee's self appraisal; {@code submit} also validates it and hands it over. */
    @Transactional
    public PmsSelfAppraisalResponse saveSelf(PmsUserPrincipal employee, PmsSelfAppraisalRequest request) {
        PmsGoalAssignmentEntity a = access.requireApproved(employee.organisationId(), employee.id(),
                request.financialYear());
        PmsAppraisalEntity appraisal = appraisalFor(a);
        if (appraisal.getSelfStatus() == PmsSelfAppraisalStatus.SUBMITTED) {
            throw DomainException.conflict("Your self appraisal was already submitted.", "PMS_INVALID_GOAL_STATE");
        }

        Map<UUID, PmsGoalAssignmentTargetEntity> onSheet = new HashMap<>();
        access.targetsInOrder(a).forEach(t -> onSheet.put(t.getKpi().getId(), t));
        Map<UUID, PmsGoalTemplateCompetencyEntity> competencies = templateCompetencies(a);

        for (PmsSelfAppraisalRequest.Line line : request.kras()) {
            if (!onSheet.containsKey(line.kpiId())) {
                throw DomainException.unprocessable("kpi_id " + line.kpiId() + " is not on your goal sheet",
                        "PMS_KPI_NOT_IN_TEMPLATE");
            }
            checkRating(line.rating());
        }
        for (PmsSelfAppraisalRequest.CompetencyLine line : request.competencies()) {
            if (!competencies.containsKey(line.competencyId())) {
                throw DomainException.unprocessable("competency_id " + line.competencyId()
                        + " is not part of your goal template", "PMS_COMPETENCY_NOT_IN_TEMPLATE");
            }
            checkRating(line.rating());
        }

        appraisal = appraisalRepository.save(appraisal);
        Map<String, PmsAppraisalLineEntity> lines = linesByKey(appraisal);
        for (PmsSelfAppraisalRequest.Line line : request.kras()) {
            PmsAppraisalLineEntity row = lineFor(appraisal, lines, PmsAppraisalLineType.KRA, line.kpiId());
            row.setSelfRating(line.rating());
            row.setAttachmentName(line.attachmentName());
            row.setActual(line.actual());
            row.setEvidence(blankToNull(line.evidence()));
            lineRepository.save(row);
        }
        for (PmsSelfAppraisalRequest.CompetencyLine line : request.competencies()) {
            PmsAppraisalLineEntity row = lineFor(appraisal, lines, competencyType(competencies.get(
                    line.competencyId())), line.competencyId());
            row.setSelfRating(line.rating());
            row.setSelfComment(blankToNull(line.comment()));
            lineRepository.save(row);
        }
        appraisal.setSelfStatus(PmsSelfAppraisalStatus.DRAFT);

        if (request.submit()) {
            List<String> missing = new ArrayList<>();
            for (PmsGoalAssignmentTargetEntity t : onSheet.values()) {
                PmsAppraisalLineEntity row = lines.get(key(PmsAppraisalLineType.KRA, t.getKpi().getId()));
                if (row == null || row.getSelfRating() == null) {
                    missing.add(t.getKpi().getName());
                }
            }
            for (PmsGoalTemplateCompetencyEntity c : competencies.values()) {
                PmsAppraisalLineEntity row = lines.get(key(competencyType(c), c.getCompetency().getId()));
                if (row == null || row.getSelfRating() == null) {
                    missing.add(c.getCompetency().getName());
                }
            }
            if (!missing.isEmpty()) {
                throw DomainException.unprocessable("Rate every KRA and competency before submitting: "
                        + String.join(", ", missing), "PMS_APPRAISAL_INCOMPLETE");
            }
            appraisal.setSelfStatus(PmsSelfAppraisalStatus.SUBMITTED);
            appraisal.setSelfSubmittedOn(LocalDateTime.now());
            appraisal.setSelfScore(mean(lines.values().stream().map(PmsAppraisalLineEntity::getSelfRating).toList()));
        }
        appraisalRepository.save(appraisal);
        log.info("Employee {} {} the self appraisal for {}", employee.id(),
                request.submit() ? "submitted" : "saved", request.financialYear());
        return new PmsSelfAppraisalResponse(request.financialYear(), PmsTeamAccess.wire(appraisal.getSelfStatus()),
                appraisal.getSelfScore());
    }

    /**
     * Screen 4.6: the self appraisal sheet. Targets are the original ones, with the final target of a revision
     * in {@code revised_target}; the actual falls back to what the employee reported in Update Progress.
     */
    @Transactional(readOnly = true)
    public com.sentrifugo.pms.model.PmsMySelfAppraisalResponse mySelf(PmsUserPrincipal employee,
                                                                      String financialYear) {
        PmsGoalAssignmentEntity a = access.requireApproved(employee.organisationId(), employee.id(), financialYear);
        Optional<PmsAppraisalEntity> appraisal = appraisalRepository.findByAssignmentId(a.getId());
        Map<String, PmsAppraisalLineEntity> lines = appraisal.isPresent() ? linesByKey(appraisal.get()) : Map.of();
        List<PmsGoalAssignmentTargetEntity> targets = access.targetsInOrder(a);
        Map<UUID, PmsGoalProgressEntity> reported = access.readings(List.of(a.getId()),
                PmsProgressPeriod.CURRENT, PmsProgressPeriod.H1, PmsProgressPeriod.FY)
                .getOrDefault(a.getId(), Map.of());

        List<com.sentrifugo.pms.model.PmsMySelfAppraisalResponse.Kra> kras = new ArrayList<>();
        for (PmsGoalAssignmentTargetEntity t : targets) {
            PmsAppraisalLineEntity line = lines.get(key(PmsAppraisalLineType.KRA, t.getKpi().getId()));
            PmsGoalProgressEntity reading = reported.get(t.getKpi().getId());
            BigDecimal original = t.getOriginalTarget() != null ? t.getOriginalTarget() : t.getTarget();
            BigDecimal revised = t.getTarget() != null && original != null && t.getTarget().compareTo(original) != 0
                    ? t.getTarget() : null;
            BigDecimal actual = line != null && line.getActual() != null ? line.getActual()
                    : reading == null ? null : reading.getActual();
            kras.add(new com.sentrifugo.pms.model.PmsMySelfAppraisalResponse.Kra(t.getKpi().getId(),
                    t.getKpi().getKra() == null ? null : t.getKpi().getKra().getName(), t.getKpi().getName(),
                    t.getKpi().getUnit(), t.getWeight(), original, revised, actual,
                    line == null ? null : line.getSelfRating(), line == null ? null : line.getEvidence(),
                    line == null ? null : line.getAttachmentName()));
        }
        List<com.sentrifugo.pms.model.PmsMySelfAppraisalResponse.Competency> competencies = new ArrayList<>();
        for (PmsGoalTemplateCompetencyEntity c : templateCompetencies(a).values()) {
            PmsAppraisalLineType type = competencyType(c);
            PmsAppraisalLineEntity line = lines.get(key(type, c.getCompetency().getId()));
            competencies.add(new com.sentrifugo.pms.model.PmsMySelfAppraisalResponse.Competency(
                    c.getCompetency().getId(), c.getCompetency().getName(),
                    type == PmsAppraisalLineType.HR_COMPETENCY ? "hr" : "manager",
                    line == null ? null : line.getSelfRating(), line == null ? null : line.getSelfComment()));
        }
        PmsAppraisalEntity ap = appraisal.orElse(null);
        return new com.sentrifugo.pms.model.PmsMySelfAppraisalResponse(financialYear,
                ap != null && ap.getSelfStatus() == PmsSelfAppraisalStatus.SUBMITTED ? "submitted" : "draft",
                ap == null ? null : ap.getSelfSubmittedOn(), ap == null ? null : ap.getSelfScore(), kras,
                competencies);
    }

    // ── manager ──────────────────────────────────────────────────────────────

    /** Screen 3.13: every team member with an approved goal sheet, their self appraisal and your rating. */
    @Transactional(readOnly = true)
    public List<PmsTeamAppraisalResponse> team(PmsUserPrincipal manager, String financialYear) {
        List<IamEmployee> reports = iam.directReports(manager.organisationId(), manager.id());
        if (reports.isEmpty()) {
            return List.of();
        }
        Map<String, IamEmployee> employeeById = new HashMap<>();
        reports.forEach(e -> employeeById.put(e.userId(), e));
        List<PmsGoalAssignmentEntity> approved = assignmentRepository
                .findByOrganisationIdAndFinancialYearAndEmployeeUserIdIn(manager.organisationId(), financialYear,
                        reports.stream().map(IamEmployee::userId).toList())
                .stream()
                .filter(a -> a.getStatus() == PmsAssignmentStatus.APPROVED)
                .toList();
        Map<UUID, PmsAppraisalEntity> appraisalByAssignment = new HashMap<>();
        if (!approved.isEmpty()) {
            appraisalRepository.findByAssignmentIdIn(approved.stream().map(PmsGoalAssignmentEntity::getId).toList())
                    .forEach(ap -> appraisalByAssignment.put(ap.getAssignment().getId(), ap));
        }
        List<PmsTeamAppraisalResponse> rows = new ArrayList<>();
        for (PmsGoalAssignmentEntity a : approved) {
            IamEmployee e = employeeById.get(a.getEmployeeUserId());
            PmsAppraisalEntity ap = appraisalByAssignment.get(a.getId());
            boolean selfSubmitted = ap != null && ap.getSelfStatus() == PmsSelfAppraisalStatus.SUBMITTED;
            String managerStatus = !selfSubmitted ? "waiting" : PmsTeamAccess.wire(ap.getManagerStatus());
            rows.add(new PmsTeamAppraisalResponse(a.getEmployeeUserId(), PmsTeamAccess.fullName(e),
                    e.designationName(), selfSubmitted ? "submitted" : "not_submitted",
                    selfSubmitted ? ap.getSelfScore() : null, managerStatus, null));
        }
        return rows;
    }

    /** Screen 3.14: KRAs and competencies with the self rating, and the manager rating so far. */
    @Transactional(readOnly = true)
    public PmsManagerAppraisalResponse manager(PmsUserPrincipal manager, String employeeUserId,
                                               String financialYear) {
        IamEmployee employee = access.requireTeamMember(manager, employeeUserId);
        PmsGoalAssignmentEntity a = access.requireApproved(manager.organisationId(), employeeUserId, financialYear);
        PmsAppraisalEntity appraisal = requireSubmittedSelf(a);
        return view(employee, a, appraisal);
    }

    /** Screen 3.14 Save Draft: any rating may still be empty. */
    @Transactional
    public PmsManagerAppraisalResponse saveManager(PmsUserPrincipal manager,
                                                   PmsManagerAppraisalSaveRequest request) {
        IamEmployee employee = access.requireTeamMember(manager, request.employeeUserId());
        PmsGoalAssignmentEntity a = access.requireApproved(manager.organisationId(), request.employeeUserId(),
                request.financialYear());
        PmsAppraisalEntity appraisal = requireSubmittedSelf(a);
        if (appraisal.getManagerStatus() == PmsManagerAppraisalStatus.SUBMITTED_TO_HOD) {
            throw DomainException.conflict("This appraisal is already with the HOD.", "PMS_INVALID_GOAL_STATE");
        }
        store(a, appraisal, request);
        appraisal.setManagerStatus(PmsManagerAppraisalStatus.DRAFT);
        appraisalRepository.save(appraisal);
        return view(employee, a, appraisal);
    }

    /**
     * Screen 3.14 Submit to HOD: every KRA and manager competency rated 1-5, a recommended rating, and a
     * comment wherever the rating is two or more points from the self rating.
     */
    @Transactional
    public PmsManagerAppraisalResponse submitManager(PmsUserPrincipal manager,
                                                     PmsManagerAppraisalSaveRequest request) {
        IamEmployee employee = access.requireTeamMember(manager, request.employeeUserId());
        PmsGoalAssignmentEntity a = access.requireApproved(manager.organisationId(), request.employeeUserId(),
                request.financialYear());
        PmsAppraisalEntity appraisal = requireSubmittedSelf(a);
        if (appraisal.getManagerStatus() == PmsManagerAppraisalStatus.SUBMITTED_TO_HOD) {
            throw DomainException.conflict("This appraisal is already with the HOD.", "PMS_INVALID_GOAL_STATE");
        }
        store(a, appraisal, request);

        List<String> errors = new ArrayList<>();
        Map<String, PmsAppraisalLineEntity> lines = linesByKey(appraisal);
        Map<UUID, PmsGoalTemplateCompetencyEntity> competencies = templateCompetencies(a);
        for (PmsGoalAssignmentTargetEntity t : access.targetsInOrder(a)) {
            checkLine(errors, t.getKpi().getName(), lines.get(key(PmsAppraisalLineType.KRA, t.getKpi().getId())));
        }
        for (PmsGoalTemplateCompetencyEntity c : competencies.values()) {
            if (competencyType(c) == PmsAppraisalLineType.COMPETENCY) {
                checkLine(errors, c.getCompetency().getName(),
                        lines.get(key(PmsAppraisalLineType.COMPETENCY, c.getCompetency().getId())));
            }
        }
        if (appraisal.getRecommendedRating() == null) {
            errors.add("Choose a recommended rating");
        }
        if (!errors.isEmpty()) {
            throw new DomainException(String.join("; ", errors), "PMS_APPRAISAL_INVALID",
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }
        appraisal.setManagerStatus(PmsManagerAppraisalStatus.SUBMITTED_TO_HOD);
        appraisal.setManagerSubmittedOn(LocalDateTime.now());
        appraisalRepository.save(appraisal);
        log.info("Manager {} submitted the appraisal of {} to the HOD", manager.id(), request.employeeUserId());
        return view(employee, a, appraisal);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private PmsAppraisalEntity appraisalFor(PmsGoalAssignmentEntity assignment) {
        return appraisalRepository.findByAssignmentId(assignment.getId())
                .orElseGet(() -> PmsAppraisalEntity.builder()
                        .assignment(assignment)
                        .selfStatus(PmsSelfAppraisalStatus.NOT_SUBMITTED)
                        .managerStatus(PmsManagerAppraisalStatus.PENDING)
                        .build());
    }

    private PmsAppraisalEntity requireSubmittedSelf(PmsGoalAssignmentEntity assignment) {
        Optional<PmsAppraisalEntity> appraisal = appraisalRepository.findByAssignmentId(assignment.getId());
        if (appraisal.isEmpty() || appraisal.get().getSelfStatus() != PmsSelfAppraisalStatus.SUBMITTED) {
            throw DomainException.conflict("Waiting for the employee to submit the self appraisal.",
                    "PMS_SELF_APPRAISAL_PENDING");
        }
        return appraisal.get();
    }

    /** Writes the manager's ratings and comments onto the lines, creating a line where the employee left none. */
    private void store(PmsGoalAssignmentEntity a, PmsAppraisalEntity appraisal,
                       PmsManagerAppraisalSaveRequest request) {
        Set<UUID> kpis = new HashSet<>();
        access.targetsInOrder(a).forEach(t -> kpis.add(t.getKpi().getId()));
        Map<UUID, PmsGoalTemplateCompetencyEntity> competencies = templateCompetencies(a);

        for (PmsManagerAppraisalSaveRequest.Line line : request.kras()) {
            if (!kpis.contains(line.kpiId())) {
                throw DomainException.unprocessable("kpi_id " + line.kpiId() + " is not on this goal sheet",
                        "PMS_KPI_NOT_IN_TEMPLATE");
            }
            checkRating(line.rating());
        }
        for (PmsManagerAppraisalSaveRequest.CompetencyLine line : request.competencies()) {
            PmsGoalTemplateCompetencyEntity c = competencies.get(line.id());
            if (c == null) {
                throw DomainException.unprocessable("competency id " + line.id()
                        + " is not part of this goal template", "PMS_COMPETENCY_NOT_IN_TEMPLATE");
            }
            if (competencyType(c) == PmsAppraisalLineType.HR_COMPETENCY) {
                throw DomainException.unprocessable(c.getCompetency().getName()
                        + " is rated by Plant HR, not the manager", "PMS_HR_COMPETENCY");
            }
            checkRating(line.rating());
        }
        checkRating(request.recommendedRating());

        Map<String, PmsAppraisalLineEntity> lines = linesByKey(appraisal);
        for (PmsManagerAppraisalSaveRequest.Line line : request.kras()) {
            PmsAppraisalLineEntity row = lineFor(appraisal, lines, PmsAppraisalLineType.KRA, line.kpiId());
            row.setManagerRating(line.rating());
            row.setComment(blankToNull(line.comment()));
            lineRepository.save(row);
        }
        for (PmsManagerAppraisalSaveRequest.CompetencyLine line : request.competencies()) {
            PmsAppraisalLineEntity row = lineFor(appraisal, lines, PmsAppraisalLineType.COMPETENCY, line.id());
            row.setManagerRating(line.rating());
            row.setComment(blankToNull(line.comment()));
            lineRepository.save(row);
        }
        appraisal.setRecommendedRating(request.recommendedRating());
        appraisal.setManagerComments(blankToNull(request.comments()));
    }

    private static void checkLine(List<String> errors, String name, PmsAppraisalLineEntity line) {
        if (line == null || line.getManagerRating() == null) {
            errors.add("Rate " + name);
            return;
        }
        Integer self = line.getSelfRating();
        if (self != null && Math.abs(line.getManagerRating() - self) >= COMMENT_GAP
                && (line.getComment() == null || line.getComment().trim().length() < MIN_COMMENT)) {
            errors.add("Explain the rating for " + name + " (2 or more points from the self rating)");
        }
    }

    private PmsManagerAppraisalResponse view(IamEmployee employee, PmsGoalAssignmentEntity a,
                                             PmsAppraisalEntity appraisal) {
        Map<String, PmsAppraisalLineEntity> lines = linesByKey(appraisal);
        Map<UUID, PmsGoalProgressEntity> readings = access
                .readings(List.of(a.getId()), PmsProgressPeriod.FY, PmsProgressPeriod.CURRENT, PmsProgressPeriod.H1)
                .getOrDefault(a.getId(), Map.of());

        List<PmsManagerAppraisalResponse.Kra> kras = new ArrayList<>();
        for (PmsGoalAssignmentTargetEntity t : access.targetsInOrder(a)) {
            PmsAppraisalLineEntity line = lines.get(key(PmsAppraisalLineType.KRA, t.getKpi().getId()));
            PmsGoalProgressEntity reading = readings.get(t.getKpi().getId());
            kras.add(new PmsManagerAppraisalResponse.Kra(t.getKpi().getId(), t.getKpi().getName(), t.getTarget(),
                    t.getKpi().getUnit(), reading == null ? null : reading.getActual(),
                    line == null ? null : line.getSelfRating(), line == null ? null : line.getManagerRating(),
                    line == null ? null : line.getAttachmentName(), line == null || line.getComment() == null
                            ? "" : line.getComment()));
        }
        List<PmsManagerAppraisalResponse.Competency> competencies = new ArrayList<>();
        List<PmsManagerAppraisalResponse.HrCompetency> hr = new ArrayList<>();
        for (PmsGoalTemplateCompetencyEntity c : templateCompetencies(a).values()) {
            UUID id = c.getCompetency().getId();
            if (competencyType(c) == PmsAppraisalLineType.HR_COMPETENCY) {
                PmsAppraisalLineEntity line = lines.get(key(PmsAppraisalLineType.HR_COMPETENCY, id));
                hr.add(new PmsManagerAppraisalResponse.HrCompetency(id, c.getCompetency().getName(),
                        line == null ? null : line.getSelfRating()));
            } else {
                PmsAppraisalLineEntity line = lines.get(key(PmsAppraisalLineType.COMPETENCY, id));
                competencies.add(new PmsManagerAppraisalResponse.Competency(id, c.getCompetency().getName(),
                        line == null ? null : line.getSelfRating(), line == null ? null : line.getManagerRating(),
                        line == null || line.getComment() == null ? "" : line.getComment()));
            }
        }
        return new PmsManagerAppraisalResponse(a.getEmployeeUserId(), PmsTeamAccess.fullName(employee),
                employee.empCode(), null, PmsTeamAccess.wire(appraisal.getManagerStatus()), kras, competencies, hr,
                appraisal.getRecommendedRating(),
                appraisal.getManagerComments() == null ? "" : appraisal.getManagerComments());
    }

    /** The template's competencies, in template order, keyed by competency id. */
    private Map<UUID, PmsGoalTemplateCompetencyEntity> templateCompetencies(PmsGoalAssignmentEntity a) {
        Map<UUID, PmsGoalTemplateCompetencyEntity> out = new java.util.LinkedHashMap<>();
        templateCompetencyRepository.findByTemplateIdOrderByDisplayOrderAsc(a.getTemplateId())
                .forEach(c -> out.put(c.getCompetency().getId(), c));
        return out;
    }

    /** HR competencies are rated by Corporate HR; the manager only sees the self rating. */
    static PmsAppraisalLineType competencyType(PmsGoalTemplateCompetencyEntity c) {
        return c.getCompetency().getCompetencyType() == com.sentrifugo.db.enums.PmsCompetencyType.HR
                ? PmsAppraisalLineType.HR_COMPETENCY : PmsAppraisalLineType.COMPETENCY;
    }

    private Map<String, PmsAppraisalLineEntity> linesByKey(PmsAppraisalEntity appraisal) {
        Map<String, PmsAppraisalLineEntity> out = new HashMap<>();
        if (appraisal.getId() != null) {
            lineRepository.findByAppraisalId(appraisal.getId())
                    .forEach(l -> out.put(key(l.getLineType(), l.getRefId()), l));
        }
        return out;
    }

    private PmsAppraisalLineEntity lineFor(PmsAppraisalEntity appraisal, Map<String, PmsAppraisalLineEntity> lines,
                                           PmsAppraisalLineType type, UUID refId) {
        return lines.computeIfAbsent(key(type, refId), k -> PmsAppraisalLineEntity.builder()
                .appraisal(appraisal)
                .lineType(type)
                .refId(refId)
                .build());
    }

    private static String key(PmsAppraisalLineType type, UUID refId) {
        return type.name() + ":" + refId;
    }

    private static void checkRating(Integer rating) {
        if (rating != null && (rating < MIN_RATING || rating > MAX_RATING)) {
            throw DomainException.unprocessable("Ratings must be between " + MIN_RATING + " and " + MAX_RATING,
                    "PMS_RATING_INVALID");
        }
    }

    private static BigDecimal mean(List<Integer> ratings) {
        List<Integer> given = ratings.stream().filter(r -> r != null).toList();
        if (given.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.valueOf(given.stream().mapToInt(Integer::intValue).sum());
        return sum.divide(BigDecimal.valueOf(given.size()), 2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
