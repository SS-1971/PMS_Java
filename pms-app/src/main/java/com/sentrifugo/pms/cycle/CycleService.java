package com.sentrifugo.pms.cycle;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.db.config.RatingScaleRepository;
import com.sentrifugo.db.cycle.Cycle;
import com.sentrifugo.db.cycle.CycleApplicability;
import com.sentrifugo.db.cycle.CycleFinalize;
import com.sentrifugo.db.cycle.CycleNotificationEntry;
import com.sentrifugo.db.cycle.CycleRepository;
import com.sentrifugo.db.cycle.CycleSpecifications;
import com.sentrifugo.db.cycle.CycleStage;
import com.sentrifugo.db.cycle.CycleStageRow;
import com.sentrifugo.db.cycle.CycleStatus;
import com.sentrifugo.db.cycle.CycleType;
import com.sentrifugo.messaging.iam.IamRpcClient;
import com.sentrifugo.messaging.iam.IamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CycleService {

    private static final Logger log = LoggerFactory.getLogger(CycleService.class);

    private final CycleRepository cycles;
    private final RatingScaleRepository ratingScales;
    private final ApplicableToResolver applicableToResolver;
    private final CycleCodeGenerator codeGenerator;
    private final IamRpcClient iamRpcClient;
    private final ObjectMapper objectMapper;

    public CycleService(CycleRepository cycles, RatingScaleRepository ratingScales,
                        ApplicableToResolver applicableToResolver, CycleCodeGenerator codeGenerator,
                        IamRpcClient iamRpcClient, ObjectMapper objectMapper) {
        this.cycles = cycles;
        this.ratingScales = ratingScales;
        this.applicableToResolver = applicableToResolver;
        this.codeGenerator = codeGenerator;
        this.iamRpcClient = iamRpcClient;
        this.objectMapper = objectMapper;
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    public CycleDtos.ListResponse list(String organisationId, String search, Integer financialYearStart,
                                       String typeWire, String plantId, String statusWire, int skip, int limit) {
        CycleType type = typeWire != null && !typeWire.isBlank() ? CycleType.fromWire(typeWire) : null;
        CycleStatus status = statusWire != null && !statusWire.isBlank() ? CycleStatus.fromWire(statusWire) : null;

        // One fetch, ignoring status, backs both the (possibly status-filtered)
        // page and the summary — the contract requires the stat cards to stay
        // stable while a status tab is selected, so `summary` must never apply
        // the status filter even though `items`/`total` do.
        List<Cycle> matchingAnyStatus = cycles.findAll(
                CycleSpecifications.searchIgnoringStatus(organisationId, search, financialYearStart, type, plantId));

        Map<CycleStatus, Long> counts = new EnumMap<>(CycleStatus.class);
        for (CycleStatus s : CycleStatus.values()) {
            counts.put(s, 0L);
        }
        matchingAnyStatus.forEach(c -> counts.merge(c.getStatus(), 1L, Long::sum));
        CycleDtos.Summary summary = new CycleDtos.Summary(matchingAnyStatus.size(), counts.get(CycleStatus.DRAFT),
                counts.get(CycleStatus.ACTIVE), counts.get(CycleStatus.CLOSED), counts.get(CycleStatus.CANCELLED));

        List<Cycle> matching = status == null ? matchingAnyStatus
                : matchingAnyStatus.stream().filter(c -> c.getStatus() == status).toList();

        List<CycleDtos.ListItem> page = matching.stream()
                .sorted(Comparator.comparing(Cycle::getCreatedOn).reversed())
                .skip(Math.max(skip, 0))
                .limit(limit > 0 ? limit : 20)
                .map(c -> toListItem(organisationId, c))
                .toList();

        return new CycleDtos.ListResponse(page, matching.size(), summary);
    }

    public List<CycleDtos.ListItem> listForExport(String organisationId, String search, Integer financialYearStart,
                                                  String typeWire, String plantId, String statusWire) {
        CycleType type = typeWire != null && !typeWire.isBlank() ? CycleType.fromWire(typeWire) : null;
        CycleStatus status = statusWire != null && !statusWire.isBlank() ? CycleStatus.fromWire(statusWire) : null;
        return cycles.findAll(CycleSpecifications.search(organisationId, search, financialYearStart, type, plantId,
                        status)).stream()
                .sorted(Comparator.comparing(Cycle::getCreatedOn).reversed())
                .map(c -> toListItem(organisationId, c))
                .toList();
    }

    public CycleDtos.CycleDto get(String organisationId, UUID cycleId) {
        return toDto(organisationId, find(organisationId, cycleId));
    }

    // ── Writes ───────────────────────────────────────────────────────────────

    @Transactional
    public CycleDtos.CycleDto create(String organisationId, CycleDtos.UpsertRequest request) {
        validateCommon(request);
        CycleType type = CycleType.fromWire(request.basic().type());
        String cycleCode = codeGenerator.generate(type, request.basic().periodStart());

        Cycle cycle = new Cycle(organisationId, cycleCode, request.basic().name().trim(),
                request.basic().description(), type, request.basic().periodStart(), request.basic().periodEnd(),
                CycleStatus.DRAFT);
        applyMutableFields(cycle, request);
        return toDto(organisationId, cycles.save(cycle));
    }

    @Transactional
    public CycleDtos.CycleDto update(String organisationId, UUID cycleId, CycleDtos.UpsertRequest request) {
        Cycle cycle = find(organisationId, cycleId);
        if (cycle.getStatus() != CycleStatus.DRAFT && cycle.getStatus() != CycleStatus.ACTIVE) {
            throw DomainException.conflict(
                    "Only a draft or active cycle can be edited.", "PMS_CYCLE_NOT_EDITABLE");
        }
        validateCommon(request);
        cycle.setName(request.basic().name().trim());
        cycle.setDescription(request.basic().description());
        cycle.setType(CycleType.fromWire(request.basic().type()));
        cycle.setPeriodStart(request.basic().periodStart());
        cycle.setPeriodEnd(request.basic().periodEnd());
        applyMutableFields(cycle, request);
        return toDto(organisationId, cycles.save(cycle));
    }

    @Transactional
    public CycleDtos.ActivationDto publish(String organisationId, UUID cycleId) {
        Cycle cycle = find(organisationId, cycleId);
        if (cycle.getStatus() != CycleStatus.DRAFT) {
            throw DomainException.conflict(
                    "Only a draft cycle can be published.", "PMS_CYCLE_NOT_PUBLISHABLE");
        }
        assertStrictlyComplete(organisationId, cycle);

        cycle.setStatus(CycleStatus.ACTIVE);
        cycle.setPublishedOn(LocalDate.now());
        cycle.setNotifications(buildNotifications(organisationId, cycle));
        cycle = cycles.save(cycle);
        return toActivationDto(organisationId, cycle);
    }

    public CycleDtos.ActivationDto activation(String organisationId, UUID cycleId) {
        Cycle cycle = find(organisationId, cycleId);
        if (cycle.getStatus() == CycleStatus.DRAFT) {
            throw DomainException.conflict("This cycle has not been published yet.", "PMS_CYCLE_NOT_PUBLISHED");
        }
        return toActivationDto(organisationId, cycle);
    }

    @Transactional
    public CycleDtos.CycleDto cancel(String organisationId, UUID cycleId) {
        Cycle cycle = find(organisationId, cycleId);
        if (cycle.getStatus() != CycleStatus.DRAFT && cycle.getStatus() != CycleStatus.ACTIVE) {
            throw DomainException.conflict(
                    "Only a draft or active cycle can be cancelled.", "PMS_CYCLE_NOT_CANCELLABLE");
        }
        cycle.setStatus(CycleStatus.CANCELLED);
        return toDto(organisationId, cycles.save(cycle));
    }

    // ── Eligibility preview (endpoint #9) ───────────────────────────────────

    public CycleDtos.EligibilityPreview previewEligibility(String organisationId, CycleDtos.Applicability request) {
        EligibilityRpcRequest payload = new EligibilityRpcRequest(organisationId, request.plantIds(),
                request.allDepartments(), request.departmentIds(), request.employmentTypes(),
                request.minServiceMonths(), request.serviceAsOn(), request.excludeProbation(),
                request.excludeNoticePeriod());
        try {
            JsonNode data = iamRpcClient.call("eligible_employees_preview", payload);
            return objectMapper.treeToValue(data, CycleDtos.EligibilityPreview.class);
        } catch (IamUnavailableException e) {
            log.warn("Eligible-employees preview: IAM unreachable ({})", e.getMessage());
            throw new DomainException(
                    "Could not reach the eligibility service. Please try again.", "ELIGIBILITY_SERVICE_UNAVAILABLE",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private record EligibilityRpcRequest(String organisationId, List<String> plantIds, boolean allDepartments,
                                         List<String> departmentIds, List<String> employmentTypes,
                                         int minServiceMonths, LocalDate serviceAsOn, boolean excludeProbation,
                                         boolean excludeNoticePeriod) {
    }

    // ── Validation ───────────────────────────────────────────────────────────

    /** Applies on every create/update, draft or not: the parts of the contract's
     * "Validation" list that are not gated behind "Draft saves are partial". */
    private void validateCommon(CycleDtos.UpsertRequest request) {
        if (!request.basic().periodEnd().isAfter(request.basic().periodStart())) {
            throw DomainException.unprocessable("period_end must be after period_start", "VALIDATION_ERROR");
        }
        CycleType.fromWire(request.basic().type());

        Set<CycleStage> seen = new HashSet<>();
        for (CycleDtos.StageEntry entry : request.stages()) {
            CycleStage stage = CycleStage.fromWire(entry.stage());
            if (!seen.add(stage)) {
                throw DomainException.unprocessable(
                        "Stage '" + entry.stage() + "' is listed more than once.", "VALIDATION_ERROR");
            }
            if (entry.startDate() != null && entry.endDate() != null && entry.endDate().isBefore(entry.startDate())) {
                throw DomainException.unprocessable(
                        "Stage '" + entry.stage() + "': end_date must not be before start_date",
                        "VALIDATION_ERROR");
            }
        }
    }

    /** The publish-time strict pass: everything {@link #validateCommon} does not
     * already cover, per the contract's publish description. */
    private void assertStrictlyComplete(String organisationId, Cycle cycle) {
        CycleApplicability applicability = cycle.getApplicability();
        if (applicability.getPlantIds().isEmpty()) {
            throw DomainException.unprocessable("At least one plant must be selected.", "VALIDATION_ERROR");
        }
        if (applicability.getEmploymentTypes().isEmpty()) {
            throw DomainException.unprocessable("At least one employment type must be selected.",
                    "VALIDATION_ERROR");
        }
        if (!applicability.isAllDepartments() && applicability.getDepartmentIds().isEmpty()) {
            throw DomainException.unprocessable(
                    "At least one department must be selected when not applying to all departments.",
                    "VALIDATION_ERROR");
        }
        UUID ratingScaleId = cycle.getFinalize().getRatingScaleId();
        if (ratingScaleId == null
                || ratingScales.findByIdAndOrganisationIdAndDeletedOnIsNull(ratingScaleId, organisationId).isEmpty()) {
            throw DomainException.unprocessable("A valid rating scale must be selected.", "VALIDATION_ERROR");
        }

        Map<CycleStage, CycleStageRow> byStage = new EnumMap<>(CycleStage.class);
        cycle.getStages().forEach(row -> byStage.put(row.getStage(), row));
        for (CycleStage stage : CycleStage.values()) {
            CycleStageRow row = byStage.get(stage);
            if (row == null || row.getStartDate() == null || row.getEndDate() == null) {
                throw DomainException.unprocessable(
                        "Stage '" + stage.wire() + "' needs both a start and end date before publishing.",
                        "VALIDATION_ERROR");
            }
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private void applyMutableFields(Cycle cycle, CycleDtos.UpsertRequest request) {
        List<CycleStageRow> stageRows = new ArrayList<>();
        for (CycleDtos.StageEntry entry : request.stages()) {
            stageRows.add(new CycleStageRow(CycleStage.fromWire(entry.stage()), entry.startDate(), entry.endDate(),
                    entry.notifyEnabled()));
        }
        cycle.replaceStages(stageRows);

        CycleApplicability applicability = new CycleApplicability();
        applicability.setPlantIds(request.applicability().plantIds());
        applicability.setAllDepartments(request.applicability().allDepartments());
        applicability.setDepartmentIds(request.applicability().departmentIds());
        applicability.setEmploymentTypes(request.applicability().employmentTypes());
        applicability.setMinServiceMonths(request.applicability().minServiceMonths());
        applicability.setServiceAsOn(request.applicability().serviceAsOn());
        applicability.setExcludeProbation(request.applicability().excludeProbation());
        applicability.setExcludeNoticePeriod(request.applicability().excludeNoticePeriod());
        cycle.setApplicability(applicability);

        CycleFinalize finalize = new CycleFinalize();
        finalize.setRatingScaleId(request.finalizeSettings().ratingScaleId());
        finalize.setNotifyManagers(request.finalizeSettings().notifyManagers());
        finalize.setNotifyEmployees(request.finalizeSettings().notifyEmployees());
        finalize.setNotifyHod(request.finalizeSettings().notifyHod());
        finalize.setNotifyHr(request.finalizeSettings().notifyHr());
        cycle.setFinalize(finalize);
    }

    /**
     * Only {@code employees} is backed by a real count, via the same
     * eligible-employees RPC the preview screen uses. {@code reporting_managers}
     * / {@code hod_reviewers} / {@code hr} would need IAM to resolve distinct
     * managers / HODs for the eligible cohort — no such RPC exists yet, so
     * those audiences are reported at 0 rather than a fabricated number; the
     * UI still gets every ticked audience, just an honest count for the ones
     * this service can't yet determine.
     */
    private List<CycleNotificationEntry> buildNotifications(String organisationId, Cycle cycle) {
        List<CycleNotificationEntry> entries = new ArrayList<>();
        CycleFinalize finalize = cycle.getFinalize();
        if (finalize.isNotifyEmployees()) {
            int sent = 0;
            try {
                CycleDtos.Applicability applicabilityDto = toApplicabilityDto(cycle.getApplicability());
                sent = previewEligibility(organisationId, applicabilityDto).totalEligible();
            } catch (DomainException e) {
                log.warn("Publish: could not resolve eligible-employee count for notifications ({})",
                        e.getMessage());
            }
            entries.add(new CycleNotificationEntry("employees", sent));
        }
        if (finalize.isNotifyManagers()) {
            entries.add(new CycleNotificationEntry("reporting_managers", 0));
        }
        if (finalize.isNotifyHod()) {
            entries.add(new CycleNotificationEntry("hod_reviewers", 0));
        }
        if (finalize.isNotifyHr()) {
            entries.add(new CycleNotificationEntry("hr", 0));
        }
        return entries;
    }

    private Cycle find(String organisationId, UUID cycleId) {
        return cycles.findByIdAndOrganisationIdAndDeletedOnIsNull(cycleId, organisationId)
                .orElseThrow(() -> DomainException.notFound("Cycle not found", "PMS_CYCLE_NOT_FOUND"));
    }

    private CycleDtos.CycleDto toDto(String organisationId, Cycle cycle) {
        return new CycleDtos.CycleDto(cycle.getId(), cycle.getCycleCode(), cycle.getStatus().wire(),
                cycle.getCreatedOn().atZone(java.time.ZoneOffset.UTC).toLocalDate(), cycle.getPublishedOn(),
                applicableToResolver.resolve(organisationId, cycle.getApplicability()), toBasicDto(cycle),
                toStageDtos(cycle), toApplicabilityDto(cycle.getApplicability()),
                toFinalizeDto(cycle.getFinalize()));
    }

    private CycleDtos.ListItem toListItem(String organisationId, Cycle cycle) {
        return new CycleDtos.ListItem(cycle.getId(), cycle.getCycleCode(), cycle.getName(), cycle.getType().wire(),
                cycle.getPeriodStart(), cycle.getPeriodEnd(),
                applicableToResolver.resolve(organisationId, cycle.getApplicability()), cycle.getStatus().wire(),
                cycle.getCreatedOn().atZone(java.time.ZoneOffset.UTC).toLocalDate());
    }

    private CycleDtos.ActivationDto toActivationDto(String organisationId, Cycle cycle) {
        List<CycleDtos.NotificationEntry> notifications = cycle.getNotifications().stream()
                .map(n -> new CycleDtos.NotificationEntry(n.audience(), n.sent()))
                .toList();
        return new CycleDtos.ActivationDto(toDto(organisationId, cycle), cycle.getPublishedOn(), notifications);
    }

    private CycleDtos.Basic toBasicDto(Cycle cycle) {
        return new CycleDtos.Basic(cycle.getName(), cycle.getDescription(), cycle.getType().wire(),
                cycle.getPeriodStart(), cycle.getPeriodEnd());
    }

    private List<CycleDtos.StageEntry> toStageDtos(Cycle cycle) {
        return cycle.getStages().stream()
                .sorted(Comparator.comparing(row -> row.getStage().ordinal()))
                .map(row -> new CycleDtos.StageEntry(row.getStage().wire(), row.getStartDate(), row.getEndDate(),
                        row.isNotify()))
                .toList();
    }

    private CycleDtos.Applicability toApplicabilityDto(CycleApplicability applicability) {
        return new CycleDtos.Applicability(applicability.getPlantIds(), applicability.isAllDepartments(),
                applicability.getDepartmentIds(), applicability.getEmploymentTypes(),
                applicability.getMinServiceMonths(), applicability.getServiceAsOn(),
                applicability.isExcludeProbation(), applicability.isExcludeNoticePeriod());
    }

    private CycleDtos.Finalize toFinalizeDto(CycleFinalize finalize) {
        return new CycleDtos.Finalize(finalize.getRatingScaleId(), finalize.isNotifyManagers(),
                finalize.isNotifyEmployees(), finalize.isNotifyHod(), finalize.isNotifyHr());
    }
}
