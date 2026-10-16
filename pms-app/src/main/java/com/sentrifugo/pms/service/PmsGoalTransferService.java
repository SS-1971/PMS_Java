package com.sentrifugo.pms.service;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.db.entity.PmsGoalTemplateEntity;
import com.sentrifugo.db.entity.PmsGoalTransferEntity;
import com.sentrifugo.db.entity.PmsGoalTransferKraEntity;
import com.sentrifugo.db.enums.PmsTemplateStatus;
import com.sentrifugo.db.enums.PmsTransferKraSource;
import com.sentrifugo.db.enums.PmsTransferStatus;
import com.sentrifugo.db.repository.PmsGoalTemplateKpiRepository;
import com.sentrifugo.db.repository.PmsGoalTemplateRepository;
import com.sentrifugo.db.repository.PmsGoalTransferKraRepository;
import com.sentrifugo.db.repository.PmsGoalTransferRepository;
import com.sentrifugo.integration.IamEmployeeClient;
import com.sentrifugo.integration.model.IamEmployee;
import com.sentrifugo.pms.model.PmsTransferNoticeResponse;
import com.sentrifugo.pms.model.PmsTransferRecordRequest;
import com.sentrifugo.pms.model.PmsTransferSheetRequest;
import com.sentrifugo.pms.model.PmsTransferSheetResponse;
import com.sentrifugo.security.context.PmsUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An employee who moved into the manager's team mid-year (screens 3.6 banner and 3.8): HR records the
 * transfer, the earlier KRAs stay frozen as period 1, and the new manager sets the period 2 KRAs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmsGoalTransferService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_MONTH_YEAR =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final IamEmployeeClient iam;
    private final PmsTeamAccess access;
    private final PmsGoalTransferRepository transferRepository;
    private final PmsGoalTransferKraRepository kraRepository;
    private final PmsGoalTemplateRepository templateRepository;
    private final PmsGoalTemplateKpiRepository templateKpiRepository;

    /** Screen 3.6 banner: transfers into the caller's team whose period 2 KRAs are not sent yet. */
    @Transactional(readOnly = true)
    public List<PmsTransferNoticeResponse> notices(PmsUserPrincipal manager, String financialYear) {
        return transferRepository
                .findByOrganisationIdAndFinancialYearAndNewManagerUserId(manager.organisationId(), financialYear,
                        manager.id())
                .stream()
                .filter(t -> t.getStatus() != PmsTransferStatus.SENT)
                .map(t -> new PmsTransferNoticeResponse(t.getEmployeeUserId(), t.getEmployeeName(), t.getJoinedOn(),
                        t.getFromDepartment(), t.getPreviousManagerName(), t.getRecordedBy()))
                .toList();
    }

    /**
     * HR records a transfer. The employee has to be in the new manager's team in IAM already, which is
     * where the name and code are read from; the earlier KRAs become the frozen period 1 rows.
     */
    @Transactional
    public PmsTransferNoticeResponse record(PmsUserPrincipal hr, PmsTransferRecordRequest request) {
        IamEmployee employee = iam.directReports(hr.organisationId(), request.newManagerUserId()).stream()
                .filter(e -> request.employeeUserId().equals(e.userId()))
                .findFirst()
                .orElseThrow(() -> DomainException.unprocessable(
                        "This employee does not report to the new manager in the employee directory yet.",
                        "PMS_NOT_IN_TEAM"));
        if (transferRepository.findByOrganisationIdAndEmployeeUserIdAndFinancialYear(hr.organisationId(),
                request.employeeUserId(), request.financialYear()).isPresent()) {
            throw DomainException.conflict("A transfer is already recorded for this employee in "
                    + request.financialYear(), "PMS_TRANSFER_EXISTS");
        }
        PmsGoalTransferEntity transfer = transferRepository.save(PmsGoalTransferEntity.builder()
                .organisationId(hr.organisationId())
                .employeeUserId(request.employeeUserId())
                .financialYear(request.financialYear())
                .employeeName(PmsTeamAccess.fullName(employee))
                .empCode(employee.empCode())
                .newManagerUserId(request.newManagerUserId())
                .previousManagerName(request.previousManagerName())
                .fromDepartment(request.fromDepartment())
                .toDepartment(request.toDepartment())
                .previousPeriodLabel(request.previousPeriodLabel())
                .joinedOn(request.joinedOn())
                .recordedBy(hr.fullName())
                .recordedOn(LocalDate.now())
                .status(PmsTransferStatus.PENDING)
                .build());
        int order = 0;
        for (PmsTransferRecordRequest.Period1Kra k : request.period1Kras() == null
                ? List.<PmsTransferRecordRequest.Period1Kra>of() : request.period1Kras()) {
            kraRepository.save(PmsGoalTransferKraEntity.builder()
                    .transfer(transfer)
                    .period(1)
                    .kraName(k.kraName().trim())
                    .source(PmsTransferKraSource.TEMPLATE)
                    .sourceLabel("Template · " + blankTo(request.fromDepartment(), "Previous role"))
                    .weight(k.weight())
                    .target(k.target())
                    .unit(k.unit())
                    .displayOrder(order++)
                    .build());
        }
        log.info("Recorded transfer of employee {} into team of {}", request.employeeUserId(),
                request.newManagerUserId());
        return new PmsTransferNoticeResponse(transfer.getEmployeeUserId(), transfer.getEmployeeName(),
                transfer.getJoinedOn(), transfer.getFromDepartment(), transfer.getPreviousManagerName(),
                transfer.getRecordedBy());
    }

    /** Screen 3.8: the frozen period 1 KRAs and the editable period 2 KRAs (seeded from the role template). */
    @Transactional(readOnly = true)
    public PmsTransferSheetResponse sheet(PmsUserPrincipal manager, String employeeUserId, String financialYear) {
        PmsGoalTransferEntity transfer = requireTransfer(manager, employeeUserId, financialYear);
        return view(manager, transfer, financialYear);
    }

    /** Screen 3.8 Save Draft: replaces the period 2 rows; incomplete drafts are allowed. */
    @Transactional
    public PmsTransferSheetResponse saveDraft(PmsUserPrincipal manager, PmsTransferSheetRequest request) {
        PmsGoalTransferEntity transfer = requireTransfer(manager, request.employeeUserId(),
                request.financialYear());
        requireEditable(transfer);
        for (PmsTransferSheetRequest.Kra k : request.kras()) {
            if (k.weight().signum() < 0 || k.weight().compareTo(HUNDRED) > 0) {
                throw DomainException.unprocessable("Weight for " + k.kraName() + " must be between 0 and 100",
                        "PMS_TRANSFER_INVALID");
            }
        }
        replacePeriodTwo(transfer, request);
        transfer.setReason(blankToNull(request.reason()));
        transfer.setRemarks(blankToNull(request.remarks()));
        transfer.setStatus(PmsTransferStatus.DRAFT);
        transferRepository.save(transfer);
        return view(manager, transfer, request.financialYear());
    }

    /** Screen 3.8 Send to Employee: refuses with 422 PMS_TRANSFER_INVALID unless the sheet is complete. */
    @Transactional
    public PmsTransferSheetResponse send(PmsUserPrincipal manager, PmsTransferSheetRequest request) {
        PmsGoalTransferEntity transfer = requireTransfer(manager, request.employeeUserId(),
                request.financialYear());
        requireEditable(transfer);
        List<String> errors = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PmsTransferSheetRequest.Kra k : request.kras()) {
            total = total.add(k.weight());
            if (k.weight().signum() <= 0) {
                errors.add("Weight for " + k.kraName() + " must be above 0");
            }
            if (k.target() == null) {
                errors.add("Target missing for " + k.kraName());
            }
        }
        if (request.kras().isEmpty()) {
            errors.add("Add at least one period 2 KRA");
        } else if (total.subtract(HUNDRED).abs().compareTo(TOLERANCE) > 0) {
            errors.add("Period 2 weightage is " + total.stripTrailingZeros().toPlainString()
                    + "% (must be 100%)");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            errors.add("Select a reason for the change");
        }
        if (request.remarks() == null || request.remarks().trim().length() < 10) {
            errors.add("Add at least 10 characters of remarks");
        }
        if (!errors.isEmpty()) {
            throw new DomainException(String.join("; ", errors), "PMS_TRANSFER_INVALID",
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }
        replacePeriodTwo(transfer, request);
        transfer.setReason(request.reason().trim());
        transfer.setRemarks(request.remarks().trim());
        transfer.setStatus(PmsTransferStatus.SENT);
        transferRepository.save(transfer);
        log.info("Sent period 2 KRAs of transferred employee {}", request.employeeUserId());
        return view(manager, transfer, request.financialYear());
    }

    // ── internals ────────────────────────────────────────────────────────────

    private PmsGoalTransferEntity requireTransfer(PmsUserPrincipal manager, String employeeUserId,
                                                  String financialYear) {
        PmsGoalTransferEntity t = transferRepository
                .findByOrganisationIdAndEmployeeUserIdAndFinancialYear(manager.organisationId(), employeeUserId,
                        financialYear)
                .orElseThrow(() -> DomainException.notFound("No transfer is recorded for this employee in "
                        + financialYear, "PMS_TRANSFER_NOT_FOUND"));
        if (!manager.id().equals(t.getNewManagerUserId())) {
            throw DomainException.forbidden("This employee did not transfer into your team.", "PMS_NOT_IN_TEAM");
        }
        return t;
    }

    private static void requireEditable(PmsGoalTransferEntity transfer) {
        if (transfer.getStatus() == PmsTransferStatus.SENT) {
            throw DomainException.unprocessable("The period 2 KRAs were already sent to the employee.",
                    "PMS_INVALID_GOAL_STATE");
        }
    }

    private void replacePeriodTwo(PmsGoalTransferEntity transfer, PmsTransferSheetRequest request) {
        kraRepository.deleteByTransferIdAndPeriod(transfer.getId(), 2);
        kraRepository.flush();
        int order = 0;
        for (PmsTransferSheetRequest.Kra k : request.kras()) {
            boolean fromTemplate = "template".equalsIgnoreCase(k.source());
            kraRepository.save(PmsGoalTransferKraEntity.builder()
                    .transfer(transfer)
                    .period(2)
                    .kraName(k.kraName().trim())
                    .source(fromTemplate ? PmsTransferKraSource.TEMPLATE : PmsTransferKraSource.MANAGER)
                    .sourceLabel(blankTo(k.sourceLabel(), fromTemplate ? "Template" : "Added by manager"))
                    .weight(k.weight())
                    .target(k.target())
                    .unit(k.unit())
                    .displayOrder(order++)
                    .build());
        }
    }

    private PmsTransferSheetResponse view(PmsUserPrincipal manager, PmsGoalTransferEntity transfer,
                                          String financialYear) {
        List<PmsGoalTransferKraEntity> rows =
                kraRepository.findByTransferIdOrderByPeriodAscDisplayOrderAsc(transfer.getId());
        String p1Label = "Period 1" + (transfer.getPreviousPeriodLabel() == null ? ""
                : " · " + transfer.getPreviousPeriodLabel());
        String p2Label = periodTwoLabel(transfer.getJoinedOn(), financialYear);

        List<PmsTransferSheetResponse.Kra> kras = new ArrayList<>();
        boolean hasPeriodTwo = false;
        for (PmsGoalTransferKraEntity r : rows) {
            boolean periodOne = r.getPeriod() == 1;
            hasPeriodTwo |= !periodOne;
            kras.add(new PmsTransferSheetResponse.Kra(r.getId(), r.getKraName(),
                    r.getSource().name().toLowerCase(Locale.ROOT), r.getSourceLabel(), r.getPeriod(),
                    periodOne ? p1Label : p2Label, r.getWeight(), r.getTarget(), r.getUnit(), periodOne));
        }
        if (!hasPeriodTwo) {
            kras.addAll(templateRows(manager, transfer, financialYear, p2Label));
        }
        return new PmsTransferSheetResponse(
                transfer.getEmployeeUserId(),
                transfer.getEmployeeName(),
                transfer.getEmpCode(),
                new PmsTransferSheetResponse.Placement(transfer.getFromDepartment(),
                        transfer.getPreviousManagerName(), transfer.getPreviousPeriodLabel()),
                new PmsTransferSheetResponse.Placement(transfer.getToDepartment(), manager.fullName(),
                        "from " + transfer.getJoinedOn().format(DAY_MONTH_YEAR)),
                transfer.getRecordedBy(),
                transfer.getRecordedOn(),
                transfer.getStatus().name().toLowerCase(Locale.ROOT),
                kras,
                transfer.getReason(),
                transfer.getRemarks());
    }

    /** Period 2 starting point: the KPIs of the active template for the employee's new role. */
    private List<PmsTransferSheetResponse.Kra> templateRows(PmsUserPrincipal manager, PmsGoalTransferEntity transfer,
                                                            String financialYear, String p2Label) {
        IamEmployee employee = access.requireTeamMember(manager, transfer.getEmployeeUserId());
        if (employee.designationId() == null) {
            return List.of();
        }
        PmsGoalTemplateEntity template = templateRepository
                .findFirstByOrganisationIdAndRoleIdAndFinancialYearAndStatus(manager.organisationId(),
                        employee.designationId(), financialYear, PmsTemplateStatus.ACTIVE)
                .orElse(null);
        if (template == null) {
            return List.of();
        }
        List<PmsTransferSheetResponse.Kra> rows = new ArrayList<>();
        templateKpiRepository.findByTemplateIdOrderByDisplayOrderAsc(template.getId()).forEach(row ->
                rows.add(new PmsTransferSheetResponse.Kra(row.getKpi().getId(), row.getKpi().getName(), "template",
                        "Template · " + template.getTemplateName(), 2, p2Label, row.getWeightage(), null,
                        row.getKpi().getUnit(), false)));
        return rows;
    }

    /** {@code Period 2 · Oct 2026 – Mar 2027}. */
    private static String periodTwoLabel(LocalDate joinedOn, String financialYear) {
        Integer start = PmsGoalAssignmentService.startYear(financialYear);
        String end = start == null ? "" : " – Mar " + (start + 1);
        return "Period 2 · " + joinedOn.format(MONTH_YEAR) + end;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
