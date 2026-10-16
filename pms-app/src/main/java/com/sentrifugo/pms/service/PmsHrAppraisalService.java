package com.sentrifugo.pms.service;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.db.entity.PmsAppraisalEntity;
import com.sentrifugo.db.entity.PmsAppraisalLineEntity;
import com.sentrifugo.db.entity.PmsGoalAssignmentEntity;
import com.sentrifugo.db.entity.PmsGoalTemplateCompetencyEntity;
import com.sentrifugo.db.enums.PmsAppraisalLineType;
import com.sentrifugo.db.enums.PmsHrRatingStatus;
import com.sentrifugo.db.enums.PmsManagerAppraisalStatus;
import com.sentrifugo.db.repository.PmsAppraisalLineRepository;
import com.sentrifugo.db.repository.PmsAppraisalRepository;
import com.sentrifugo.db.repository.PmsGoalAssignmentRepository;
import com.sentrifugo.db.repository.PmsGoalTemplateCompetencyRepository;
import com.sentrifugo.pms.model.PmsHrAppraisalResponse;
import com.sentrifugo.pms.model.PmsHrAppraisalSaveRequest;
import com.sentrifugo.pms.model.PmsHrQueueRow;
import com.sentrifugo.pms.model.PmsSendToCalibrationRequest;
import com.sentrifugo.security.context.PmsUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Corporate HR's competency rating (screens 1.7 and 1.8). An employee reaches HR once the manager has
 * submitted the appraisal; HR rates the HR competencies, submits, and then sends the rated employees on to
 * calibration ({@link PmsCalibrationService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmsHrAppraisalService {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;

    private final PmsAppraisalRepository appraisalRepository;
    private final PmsAppraisalLineRepository lineRepository;
    private final PmsGoalAssignmentRepository assignmentRepository;
    private final PmsGoalTemplateCompetencyRepository templateCompetencyRepository;

    // ── 1.7 queue ────────────────────────────────────────────────────────────

    /** Employees whose manager appraisal is in, optionally narrowed by department, HR status and name / code. */
    @Transactional(readOnly = true)
    public List<PmsHrQueueRow> queue(PmsUserPrincipal hr, String financialYear, String departmentId,
                                     String status, String search) {
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return forHr(hr, financialYear).stream()
                .filter(ap -> departmentId == null || departmentId.isBlank()
                        || departmentId.equals(ap.getAssignment().getDepartmentId()))
                .filter(ap -> status == null || status.isBlank() || "all".equalsIgnoreCase(status)
                        || wireStatus(ap).equals(status.toLowerCase(Locale.ROOT)))
                .filter(ap -> needle.isEmpty() || contains(ap.getAssignment().getEmployeeName(), needle)
                        || contains(ap.getAssignment().getEmpCode(), needle))
                .sorted(Comparator.comparing((PmsAppraisalEntity ap) -> ap.getHodRatedOn() == null
                                ? LocalDateTime.MAX : ap.getHodRatedOn())
                        .thenComparing(ap -> String.valueOf(ap.getAssignment().getEmployeeName())))
                .map(ap -> {
                    PmsGoalAssignmentEntity a = ap.getAssignment();
                    return new PmsHrQueueRow(a.getEmployeeUserId(), a.getEmployeeName(), a.getEmpCode(),
                            a.getDepartmentId(), a.getManagerName(),
                            ap.getHodRatedOn() == null ? null : ap.getHodRatedOn().toLocalDate(), wireStatus(ap),
                            ap.getSentToCalibrationOn() != null);
                })
                .toList();
    }

    // ── 1.8 one employee ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PmsHrAppraisalResponse detail(PmsUserPrincipal hr, String employeeUserId, String financialYear) {
        PmsAppraisalEntity appraisal = requireReachedHr(hr, employeeUserId, financialYear);
        return view(appraisal);
    }

    /** Screen 1.8 Save Draft: any rating may still be empty. */
    @Transactional
    public PmsHrAppraisalResponse save(PmsUserPrincipal hr, PmsHrAppraisalSaveRequest request) {
        PmsAppraisalEntity appraisal = requireEditable(hr, request.employeeUserId(), request.financialYear());
        store(appraisal, request);
        appraisal.setHrStatus(PmsHrRatingStatus.DRAFT);
        appraisalRepository.save(appraisal);
        return view(appraisal);
    }

    /** Screen 1.8 Submit HR Ratings: every HR competency rated 1-5. */
    @Transactional
    public PmsHrAppraisalResponse submit(PmsUserPrincipal hr, PmsHrAppraisalSaveRequest request) {
        PmsAppraisalEntity appraisal = requireEditable(hr, request.employeeUserId(), request.financialYear());
        store(appraisal, request);

        Map<String, PmsAppraisalLineEntity> lines = hrLines(appraisal);
        List<String> missing = new ArrayList<>();
        for (PmsGoalTemplateCompetencyEntity c : hrCompetencies(appraisal.getAssignment()).values()) {
            PmsAppraisalLineEntity line = lines.get(c.getCompetency().getId().toString());
            if (line == null || line.getHrRating() == null) {
                missing.add(c.getCompetency().getName());
            }
        }
        if (!missing.isEmpty()) {
            throw DomainException.unprocessable("Rate every HR competency before submitting: "
                    + String.join(", ", missing), "PMS_HR_RATING_INCOMPLETE");
        }
        appraisal.setHrStatus(PmsHrRatingStatus.SUBMITTED);
        appraisal.setHrSubmittedOn(LocalDateTime.now());
        appraisalRepository.save(appraisal);
        log.info("HR {} submitted the HR competency ratings of {}", hr.id(), request.employeeUserId());
        return view(appraisal);
    }

    /** Screen 1.7 "Send Rated to Calibration": only employees HR has already rated. */
    @Transactional
    public int sendToCalibration(PmsUserPrincipal hr, PmsSendToCalibrationRequest request) {
        Map<String, PmsAppraisalEntity> byEmployee = new HashMap<>();
        forHr(hr, request.financialYear()).forEach(ap -> byEmployee.put(ap.getAssignment().getEmployeeUserId(), ap));

        List<PmsAppraisalEntity> toSend = new ArrayList<>();
        for (String employeeUserId : request.employeeUserIds()) {
            PmsAppraisalEntity ap = byEmployee.get(employeeUserId);
            if (ap == null) {
                throw DomainException.notFound("No appraisal found for employee " + employeeUserId,
                        "PMS_APPRAISAL_NOT_FOUND");
            }
            if (ap.getHrStatus() != PmsHrRatingStatus.SUBMITTED) {
                throw DomainException.conflict(ap.getAssignment().getEmployeeName()
                        + " has not been rated yet.", "PMS_HR_RATING_PENDING");
            }
            if (ap.getPublishedOn() != null) {
                throw DomainException.conflict(ap.getAssignment().getEmployeeName()
                        + "'s rating is already published.", "PMS_INVALID_GOAL_STATE");
            }
            toSend.add(ap);
        }
        LocalDateTime now = LocalDateTime.now();
        toSend.stream().filter(ap -> ap.getSentToCalibrationOn() == null)
                .forEach(ap -> ap.setSentToCalibrationOn(now));
        appraisalRepository.saveAll(toSend);
        log.info("HR {} sent {} employees to calibration for {}", hr.id(), toSend.size(), request.financialYear());
        return toSend.size();
    }

    // ── internals ────────────────────────────────────────────────────────────

    /** Appraisals that have left the manager. */
    List<PmsAppraisalEntity> forHr(PmsUserPrincipal hr, String financialYear) {
        return appraisalRepository
                .findByAssignment_OrganisationIdAndAssignment_FinancialYear(hr.organisationId(), financialYear)
                .stream()
                .filter(ap -> ap.getManagerStatus() == PmsManagerAppraisalStatus.SUBMITTED_TO_HOD)
                .toList();
    }

    private PmsAppraisalEntity requireReachedHr(PmsUserPrincipal hr, String employeeUserId, String financialYear) {
        PmsGoalAssignmentEntity a = assignmentRepository
                .findByOrganisationIdAndEmployeeUserIdAndFinancialYear(hr.organisationId(), employeeUserId,
                        financialYear)
                .orElseThrow(() -> DomainException.notFound("No goals have been set for " + financialYear,
                        "PMS_GOALS_NOT_FOUND"));
        PmsAppraisalEntity appraisal = appraisalRepository.findByAssignmentId(a.getId()).orElse(null);
        if (appraisal == null || appraisal.getManagerStatus() != PmsManagerAppraisalStatus.SUBMITTED_TO_HOD) {
            throw DomainException.conflict("The manager has not submitted this appraisal yet.",
                    "PMS_MANAGER_APPRAISAL_PENDING");
        }
        return appraisal;
    }

    private PmsAppraisalEntity requireEditable(PmsUserPrincipal hr, String employeeUserId, String financialYear) {
        PmsAppraisalEntity appraisal = requireReachedHr(hr, employeeUserId, financialYear);
        if (appraisal.getSentToCalibrationOn() != null) {
            throw DomainException.conflict("This employee is already with calibration.", "PMS_INVALID_GOAL_STATE");
        }
        return appraisal;
    }

    private void store(PmsAppraisalEntity appraisal, PmsHrAppraisalSaveRequest request) {
        Map<UUID, PmsGoalTemplateCompetencyEntity> hr = hrCompetencies(appraisal.getAssignment());
        for (PmsHrAppraisalSaveRequest.Line line : request.competencies()) {
            if (!hr.containsKey(line.id())) {
                throw DomainException.unprocessable("competency id " + line.id() + " is not an HR competency "
                        + "of this goal template", "PMS_COMPETENCY_NOT_IN_TEMPLATE");
            }
            if (line.rating() != null && (line.rating() < MIN_RATING || line.rating() > MAX_RATING)) {
                throw DomainException.unprocessable("Ratings must be between " + MIN_RATING + " and " + MAX_RATING,
                        "PMS_RATING_INVALID");
            }
        }
        Map<String, PmsAppraisalLineEntity> lines = hrLines(appraisal);
        for (PmsHrAppraisalSaveRequest.Line line : request.competencies()) {
            PmsAppraisalLineEntity row = lines.computeIfAbsent(line.id().toString(),
                    k -> PmsAppraisalLineEntity.builder()
                            .appraisal(appraisal)
                            .lineType(PmsAppraisalLineType.HR_COMPETENCY)
                            .refId(line.id())
                            .build());
            row.setHrRating(line.rating());
            row.setComment(line.comment() == null || line.comment().isBlank() ? null : line.comment().trim());
            lineRepository.save(row);
        }
        appraisal.setHrRemarks(request.remarks() == null || request.remarks().isBlank()
                ? null : request.remarks().trim());
    }

    private PmsHrAppraisalResponse view(PmsAppraisalEntity appraisal) {
        PmsGoalAssignmentEntity a = appraisal.getAssignment();
        Map<UUID, PmsAppraisalLineEntity> lines = new HashMap<>();
        lineRepository.findByAppraisalId(appraisal.getId()).forEach(l -> lines.put(l.getRefId(), l));

        List<PmsHrAppraisalResponse.HrCompetency> hr = new ArrayList<>();
        List<PmsHrAppraisalResponse.ManagerCompetency> manager = new ArrayList<>();
        templateCompetencies(a).values().forEach(c -> {
            UUID id = c.getCompetency().getId();
            PmsAppraisalLineEntity line = lines.get(id);
            if (PmsAppraisalService.competencyType(c) == PmsAppraisalLineType.HR_COMPETENCY) {
                hr.add(new PmsHrAppraisalResponse.HrCompetency(id, c.getCompetency().getName(),
                        line == null ? null : line.getSelfRating(), line == null ? null : line.getHrRating(),
                        line == null || line.getComment() == null ? "" : line.getComment()));
            } else {
                manager.add(new PmsHrAppraisalResponse.ManagerCompetency(id, c.getCompetency().getName(),
                        line == null ? null : line.getSelfRating(), line == null ? null : line.getManagerRating(),
                        line == null ? null : line.getHodRating()));
            }
        });
        return new PmsHrAppraisalResponse(a.getEmployeeUserId(), a.getEmployeeName(), a.getEmpCode(),
                a.getDesignationName(), a.getDepartmentId(), a.getManagerName(), wireStatus(appraisal),
                appraisal.getSentToCalibrationOn() != null, hr, manager,
                appraisal.getHrRemarks() == null ? "" : appraisal.getHrRemarks());
    }

    private Map<UUID, PmsGoalTemplateCompetencyEntity> templateCompetencies(PmsGoalAssignmentEntity a) {
        Map<UUID, PmsGoalTemplateCompetencyEntity> out = new LinkedHashMap<>();
        templateCompetencyRepository.findByTemplateIdOrderByDisplayOrderAsc(a.getTemplateId())
                .forEach(c -> out.put(c.getCompetency().getId(), c));
        return out;
    }

    private Map<UUID, PmsGoalTemplateCompetencyEntity> hrCompetencies(PmsGoalAssignmentEntity a) {
        Map<UUID, PmsGoalTemplateCompetencyEntity> out = new LinkedHashMap<>();
        templateCompetencies(a).forEach((id, c) -> {
            if (PmsAppraisalService.competencyType(c) == PmsAppraisalLineType.HR_COMPETENCY) {
                out.put(id, c);
            }
        });
        return out;
    }

    /** HR lines of one appraisal keyed by competency id (as a string, so the map can be mutated freely). */
    private Map<String, PmsAppraisalLineEntity> hrLines(PmsAppraisalEntity appraisal) {
        Map<String, PmsAppraisalLineEntity> out = new HashMap<>();
        if (appraisal.getId() != null) {
            lineRepository.findByAppraisalId(appraisal.getId()).stream()
                    .filter(l -> l.getLineType() == PmsAppraisalLineType.HR_COMPETENCY)
                    .forEach(l -> out.put(l.getRefId().toString(), l));
        }
        return out;
    }

    static String wireStatus(PmsAppraisalEntity ap) {
        PmsHrRatingStatus s = ap.getHrStatus() == null ? PmsHrRatingStatus.PENDING : ap.getHrStatus();
        return switch (s) {
            case PENDING -> "pending";
            case DRAFT -> "draft";
            case SUBMITTED -> "rated";
        };
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
