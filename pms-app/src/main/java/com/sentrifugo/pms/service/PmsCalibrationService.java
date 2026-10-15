package com.sentrifugo.pms.service;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.db.entity.PmsAppraisalEntity;
import com.sentrifugo.db.entity.PmsAppraisalLineEntity;
import com.sentrifugo.db.entity.PmsCalibrationDistributionEntity;
import com.sentrifugo.db.enums.PmsAppraisalLineType;
import com.sentrifugo.db.repository.PmsAppraisalLineRepository;
import com.sentrifugo.db.repository.PmsAppraisalRepository;
import com.sentrifugo.db.repository.PmsCalibrationDistributionRepository;
import com.sentrifugo.pms.model.PmsCalibrationResponse;
import com.sentrifugo.pms.model.PmsCalibrationSaveRequest;
import com.sentrifugo.security.context.PmsUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calibration &amp; normalization (screen 1.9): HR compares the rating distribution of the employees sent
 * from screen 1.7 with the normalization guideline, sets its own target, adjusts individual ratings with a
 * reason, and publishes once the targets add up to 100%.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmsCalibrationService {

    private static final int[] RATINGS = {5, 4, 3, 2, 1};
    private static final Map<Integer, String> LABEL = Map.of(
            5, "Outstanding", 4, "Exceeds Expectations", 3, "Meets Expectations",
            2, "Needs Improvement", 1, "Unsatisfactory");
    /** The standard guideline distribution, by rating (sums to 100). */
    private static final Map<Integer, Integer> GUIDELINE = Map.of(5, 5, 4, 20, 3, 60, 2, 10, 1, 5);
    private static final int MIN_REASON = 5;

    private final PmsHrAppraisalService hrService;
    private final PmsAppraisalRepository appraisalRepository;
    private final PmsAppraisalLineRepository lineRepository;
    private final PmsCalibrationDistributionRepository distributionRepository;

    @Transactional(readOnly = true)
    public PmsCalibrationResponse get(PmsUserPrincipal hr, String financialYear) {
        return view(hr, financialYear, sent(hr, financialYear));
    }

    /** Stores HR's targets and each employee's calibrated rating. Nothing is published. */
    @Transactional
    public PmsCalibrationResponse save(PmsUserPrincipal hr, PmsCalibrationSaveRequest request) {
        List<PmsAppraisalEntity> sent = sent(hr, request.financialYear());
        assertNotPublished(sent);

        Map<String, PmsAppraisalEntity> byEmployee = new HashMap<>();
        sent.forEach(ap -> byEmployee.put(ap.getAssignment().getEmployeeUserId(), ap));
        for (PmsCalibrationSaveRequest.Employee e : request.employees()) {
            PmsAppraisalEntity ap = byEmployee.get(e.employeeUserId());
            if (ap == null) {
                throw DomainException.unprocessable("Employee " + e.employeeUserId()
                        + " has not been sent to calibration", "PMS_NOT_IN_CALIBRATION");
            }
            ap.setCalibratedRating(e.calibratedRating());
            ap.setCalibrationReason(e.reason() == null || e.reason().isBlank() ? null : e.reason().trim());
        }
        appraisalRepository.saveAll(byEmployee.values());

        Map<Integer, PmsCalibrationDistributionEntity> stored = new HashMap<>();
        distributionRepository.findByOrganisationIdAndFinancialYear(hr.organisationId(), request.financialYear())
                .forEach(d -> stored.put(d.getRating(), d));
        for (PmsCalibrationSaveRequest.Level level : request.distribution()) {
            PmsCalibrationDistributionEntity row = stored.computeIfAbsent(level.rating(),
                    r -> PmsCalibrationDistributionEntity.builder()
                            .organisationId(hr.organisationId())
                            .financialYear(request.financialYear())
                            .rating(r)
                            .build());
            row.setHrPercent(level.hrPercent());
            distributionRepository.save(row);
        }
        return view(hr, request.financialYear(), sent);
    }

    /**
     * Screen 1.9 "Approve &amp; Publish Ratings". Refused unless HR's targets add up to 100% and every
     * employee has a reason wherever the calibrated rating moved.
     */
    @Transactional
    public PmsCalibrationResponse publish(PmsUserPrincipal hr, String financialYear) {
        List<PmsAppraisalEntity> sent = sent(hr, financialYear);
        assertNotPublished(sent);
        if (sent.isEmpty()) {
            throw DomainException.conflict("No employees have been sent to calibration.", "PMS_NOT_IN_CALIBRATION");
        }
        Map<Integer, Integer> hrPercent = hrPercent(hr, financialYear);
        int total = hrPercent.values().stream().mapToInt(Integer::intValue).sum();
        if (total != 100) {
            throw DomainException.unprocessable("HR normalization total is " + total + "% - "
                    + Math.abs(100 - total) + "% " + (total < 100 ? "more required to reach" : "over")
                    + " 100%. Ratings cannot be published until the total is 100%.", "PMS_CALIBRATION_TOTAL");
        }
        List<String> errors = new ArrayList<>();
        for (PmsAppraisalEntity ap : sent) {
            Integer current = currentRating(ap);
            Integer finalRating = finalRating(ap);
            String name = ap.getAssignment().getEmployeeName();
            if (finalRating == null) {
                errors.add("Choose a rating for " + name);
            } else if (current != null && !finalRating.equals(current)
                    && (ap.getCalibrationReason() == null || ap.getCalibrationReason().trim().length() < MIN_REASON)) {
                errors.add("Give a reason for changing the rating of " + name);
            }
        }
        if (!errors.isEmpty()) {
            throw DomainException.unprocessable(String.join("; ", errors), "PMS_CALIBRATION_INVALID");
        }
        LocalDateTime now = LocalDateTime.now();
        for (PmsAppraisalEntity ap : sent) {
            ap.setCalibratedRating(finalRating(ap));
            ap.setPublishedOn(now);
        }
        appraisalRepository.saveAll(sent);
        log.info("HR {} published {} calibrated ratings for {}", hr.id(), sent.size(), financialYear);
        return view(hr, financialYear, sent);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private List<PmsAppraisalEntity> sent(PmsUserPrincipal hr, String financialYear) {
        return hrService.forHr(hr, financialYear).stream()
                .filter(ap -> ap.getSentToCalibrationOn() != null)
                .sorted(Comparator.comparing(ap -> String.valueOf(ap.getAssignment().getEmployeeName())))
                .toList();
    }

    private static void assertNotPublished(List<PmsAppraisalEntity> sent) {
        if (sent.stream().anyMatch(ap -> ap.getPublishedOn() != null)) {
            throw DomainException.conflict("These ratings are already published.", "PMS_CALIBRATION_PUBLISHED");
        }
    }

    private PmsCalibrationResponse view(PmsUserPrincipal hr, String financialYear, List<PmsAppraisalEntity> sent) {
        Map<Integer, Integer> current = currentDistribution(sent);
        Map<Integer, Integer> hrPercent = hrPercent(hr, financialYear);
        List<PmsCalibrationResponse.Level> levels = new ArrayList<>();
        for (int rating : RATINGS) {
            levels.add(new PmsCalibrationResponse.Level(rating, LABEL.get(rating), current.get(rating),
                    GUIDELINE.get(rating), hrPercent.get(rating)));
        }

        Map<java.util.UUID, List<Integer>> hrRatings = new HashMap<>();
        for (PmsAppraisalEntity ap : sent) {
            List<Integer> given = new ArrayList<>();
            for (PmsAppraisalLineEntity l : lineRepository.findByAppraisalId(ap.getId())) {
                if (l.getLineType() == PmsAppraisalLineType.HR_COMPETENCY && l.getHrRating() != null) {
                    given.add(l.getHrRating());
                }
            }
            hrRatings.put(ap.getId(), given);
        }
        List<PmsCalibrationResponse.Employee> employees = sent.stream().map(ap -> {
            List<Integer> given = hrRatings.get(ap.getId());
            BigDecimal avg = given.isEmpty() ? null : BigDecimal.valueOf(given.stream()
                    .mapToInt(Integer::intValue).sum()).divide(BigDecimal.valueOf(given.size()), 2,
                    RoundingMode.HALF_UP);
            return new PmsCalibrationResponse.Employee(ap.getAssignment().getEmployeeUserId(),
                    ap.getAssignment().getEmployeeName(), ap.getAssignment().getEmpCode(),
                    ap.getRecommendedRating(), ap.getHodRating(), avg, currentRating(ap), finalRating(ap),
                    ap.getCalibrationReason() == null ? "" : ap.getCalibrationReason());
        }).toList();

        boolean published = !sent.isEmpty() && sent.stream().allMatch(ap -> ap.getPublishedOn() != null);
        return new PmsCalibrationResponse(financialYear, published, levels, employees);
    }

    /** Stored targets, or the guideline for any level HR has not set. */
    private Map<Integer, Integer> hrPercent(PmsUserPrincipal hr, String financialYear) {
        Map<Integer, Integer> out = new HashMap<>(GUIDELINE);
        distributionRepository.findByOrganisationIdAndFinancialYear(hr.organisationId(), financialYear)
                .forEach(d -> out.put(d.getRating(), d.getHrPercent()));
        return out;
    }

    /** Share of employees at each rating (final rating), as whole percents that add up to 100. */
    private static Map<Integer, Integer> currentDistribution(List<PmsAppraisalEntity> sent) {
        Map<Integer, Integer> counts = new HashMap<>();
        int rated = 0;
        for (PmsAppraisalEntity ap : sent) {
            Integer r = finalRating(ap);
            if (r != null) {
                counts.merge(r, 1, Integer::sum);
                rated++;
            }
        }
        Map<Integer, Integer> out = new HashMap<>();
        if (rated == 0) {
            for (int r : RATINGS) {
                out.put(r, 0);
            }
            return out;
        }
        // Largest remainder, so the displayed shares always total 100.
        int assigned = 0;
        Map<Integer, Double> remainder = new HashMap<>();
        for (int r : RATINGS) {
            double exact = counts.getOrDefault(r, 0) * 100.0 / rated;
            int floor = (int) Math.floor(exact);
            out.put(r, floor);
            remainder.put(r, exact - floor);
            assigned += floor;
        }
        List<Integer> order = new ArrayList<>(remainder.keySet());
        order.sort((a, b) -> Double.compare(remainder.get(b), remainder.get(a)));
        for (int i = 0; assigned < 100 && i < order.size(); i++, assigned++) {
            out.merge(order.get(i), 1, Integer::sum);
        }
        return out;
    }

    /** The rating before calibration: the HOD's, else the manager's recommendation. */
    private static Integer currentRating(PmsAppraisalEntity ap) {
        return ap.getHodRating() != null ? ap.getHodRating() : ap.getRecommendedRating();
    }

    private static Integer finalRating(PmsAppraisalEntity ap) {
        return ap.getCalibratedRating() != null ? ap.getCalibratedRating() : currentRating(ap);
    }
}
