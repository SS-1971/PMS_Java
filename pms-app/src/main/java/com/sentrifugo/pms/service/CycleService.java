package com.sentrifugo.pms.service;

import com.sentrifugo.pms.model.cycle.CycleDtos;
import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.messaging.iam.IamRpcClient;
import com.sentrifugo.messaging.iam.IamUnavailableException;
import com.sentrifugo.pms.db.dto.PmsCycleApplicabilityDto;
import com.sentrifugo.pms.db.dto.PmsCycleDto;
import com.sentrifugo.pms.db.dto.PmsCycleStageDto;
import com.sentrifugo.pms.db.entity.PmsCycleApplicabilityEmbeddable;
import com.sentrifugo.pms.db.entity.PmsCycleEntity;
import com.sentrifugo.pms.db.entity.PmsCycleFinalizeEmbeddable;
import com.sentrifugo.pms.db.entity.PmsCycleStageEntity;
import com.sentrifugo.pms.db.enums.PmsCycleStage;
import com.sentrifugo.pms.db.enums.PmsCycleStatus;
import com.sentrifugo.pms.db.enums.PmsCycleType;
import com.sentrifugo.pms.db.mapper.PmsCycleMapper;
import com.sentrifugo.pms.db.model.PmsCycleNotificationModel;
import com.sentrifugo.pms.db.repository.PmsCycleRepository;
import com.sentrifugo.pms.db.repository.PmsRatingScaleRepository;
import com.sentrifugo.pms.db.util.PmsCycleSpecifications;
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

    private final PmsCycleRepository cycles;
    private final PmsRatingScaleRepository ratingScales;
    private final ApplicableToResolver applicableToResolver;
    private final CycleCodeGenerator codeGenerator;
    private final IamRpcClient iamRpcClient;
    private final ObjectMapper objectMapper;
    private final PmsCycleMapper mapper;

    public CycleService(PmsCycleRepository cycles, PmsRatingScaleRepository ratingScales,
                        ApplicableToResolver applicableToResolver, CycleCodeGenerator codeGenerator,
                        IamRpcClient iamRpcClient, ObjectMapper objectMapper, PmsCycleMapper mapper) {
        this.cycles = cycles;
        this.ratingScales = ratingScales;
        this.applicableToResolver = applicableToResolver;
        this.codeGenerator = codeGenerator;
        this.iamRpcClient = iamRpcClient;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CycleDtos.ListResponse list(String organisationId, String search, Integer financialYearStart,
                                       String typeWire, String plantId, String statusWire, int skip, int limit) {
        PmsCycleType type = typeWire != null && !typeWire.isBlank() ? PmsCycleType.fromWire(typeWire) : null;
        PmsCycleStatus status =
                statusWire != null && !statusWire.isBlank() ? PmsCycleStatus.fromWire(statusWire) : null;

        // One fetch, ignoring status, backs both the (possibly status-filtered)
        // page and the summary — the stat cards must stay stable while a status
        // tab is selected, so `summary` never applies the status filter.
        List<PmsCycleEntity> matchingAnyStatus = cycles.findAll(
                PmsCycleSpecifications.searchIgnoringStatus(organisationId, search, financialYearStart, type,
                        plantId));

        Map<PmsCycleStatus, Long> counts = new EnumMap<>(PmsCycleStatus.class);
        for (PmsCycleStatus s : PmsCycleStatus.values()) {
            counts.put(s, 0L);
        }
        matchingAnyStatus.forEach(c -> counts.merge(c.getStatus(), 1L, Long::sum));
        CycleDtos.Summary summary = new CycleDtos.Summary(matchingAnyStatus.size(),
                counts.get(PmsCycleStatus.DRAFT), counts.get(PmsCycleStatus.ACTIVE),
                counts.get(PmsCycleStatus.CLOSED), counts.get(PmsCycleStatus.CANCELLED));

        List<PmsCycleEntity> matching = status == null ? matchingAnyStatus
                : matchingAnyStatus.stream().filter(c -> c.getStatus() == status).toList();

        List<CycleDtos.ListItem> page = matching.stream()
                .sorted(Comparator.comparing(PmsCycleEntity::getCreatedDate).reversed())
                .skip(Math.max(skip, 0))
                .limit(limit > 0 ? limit : 20)
                .map(c -> toListItem(organisationId, c))
                .toList();

        return new CycleDtos.ListResponse(page, matching.size(), summary);
    }

    @Transactional(readOnly = true)
    public List<CycleDtos.ListItem> listForExport(String organisationId, String search, Integer financialYearStart,
                                                  String typeWire, String plantId, String statusWire) {
        PmsCycleType type = typeWire != null && !typeWire.isBlank() ? PmsCycleType.fromWire(typeWire) : null;
        PmsCycleStatus status =
                statusWire != null && !statusWire.isBlank() ? PmsCycleStatus.fromWire(statusWire) : null;
        return cycles.findAll(PmsCycleSpecifications.search(organisationId, search, financialYearStart, type,
                        plantId, status)).stream()
                .sorted(Comparator.comparing(PmsCycleEntity::getCreatedDate).reversed())
                .map(c -> toListItem(organisationId, c))
                .toList();
    }

    @Transactional(readOnly = true)
    public CycleDtos.CycleDto get(String organisationId, UUID cycleId) {
        return toDto(organisationId, find(organisationId, cycleId));
    }

    // ── Writes ───────────────────────────────────────────────────────────────

    @Transactional
    public CycleDtos.CycleDto create(String organisationId, CycleDtos.UpsertRequest request) {
        validateCommon(request);
        PmsCycleType type = PmsCycleType.fromWire(request.basic().type());
        String cycleCode = codeGenerator.generate(type, request.basic().periodStart());

        PmsCycleEntity cycle = PmsCycleEntity.builder()
                .organisationId(organisationId)
                .cycleCode(cycleCode)
                .name(request.basic().name().trim())
                .description(request.basic().description())
                .type(type)
                .periodStart(request.basic().periodStart())
                .periodEnd(request.basic().periodEnd())
                .status(PmsCycleStatus.DRAFT)
                .build();
        applyMutableFields(cycle, request);
        return toDto(organisationId, cycles.save(cycle));
    }

    @Transactional
    public CycleDtos.CycleDto update(String organisationId, UUID cycleId, CycleDtos.UpsertRequest request) {
        PmsCycleEntity cycle = find(organisationId, cycleId);
        if (cycle.getStatus() != PmsCycleStatus.DRAFT && cycle.getStatus() != PmsCycleStatus.ACTIVE) {
            throw DomainException.conflict(
                    "Only a draft or active cycle can be edited.", "PMS_CYCLE_NOT_EDITABLE");
        }
        validateCommon(request);
        cycle.setName(request.basic().name().trim());
        cycle.setDescription(request.basic().description());
        cycle.setType(PmsCycleType.fromWire(request.basic().type()));
        cycle.setPeriodStart(request.basic().periodStart());
        cycle.setPeriodEnd(request.basic().periodEnd());
        applyMutableFields(cycle, request);
        return toDto(organisationId, cycles.save(cycle));
    }

    @Transactional
    public CycleDtos.ActivationDto publish(String organisationId, UUID cycleId) {
        PmsCycleEntity cycle = find(organisationId, cycleId);
        if (cycle.getStatus() != PmsCycleStatus.DRAFT) {
            throw DomainException.conflict(
                    "Only a draft cycle can be published.", "PMS_CYCLE_NOT_PUBLISHABLE");
        }
        assertStrictlyComplete(organisationId, cycle);

        cycle.setStatus(PmsCycleStatus.ACTIVE);
        cycle.setPublishedOn(LocalDate.now());
        cycle.setNotifications(buildNotifications(organisationId, cycle));
        cycle = cycles.save(cycle);
        return toActivationDto(organisationId, cycle);
    }

    @Transactional(readOnly = true)
    public CycleDtos.ActivationDto activation(String organisationId, UUID cycleId) {
        PmsCycleEntity cycle = find(organisationId, cycleId);
        if (cycle.getStatus() == PmsCycleStatus.DRAFT) {
            throw DomainException.conflict("This cycle has not been published yet.", "PMS_CYCLE_NOT_PUBLISHED");
        }
        return toActivationDto(organisationId, cycle);
    }

    @Transactional
    public CycleDtos.CycleDto cancel(String organisationId, UUID cycleId) {
        PmsCycleEntity cycle = find(organisationId, cycleId);
        if (cycle.getStatus() != PmsCycleStatus.DRAFT && cycle.getStatus() != PmsCycleStatus.ACTIVE) {
            throw DomainException.conflict(
                    "Only a draft or active cycle can be cancelled.", "PMS_CYCLE_NOT_CANCELLABLE");
        }
        cycle.setStatus(PmsCycleStatus.CANCELLED);
        return toDto(organisationId, cycles.save(cycle));
    }

    // ── Eligibility preview (endpoint #9) ───────────────────────────────────

    public CycleDtos.EligibilityPreview previewEligibility(String organisationId,
                                                            PmsCycleApplicabilityDto request) {
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

    /** Applies on every create/update, draft or not. */
    private void validateCommon(CycleDtos.UpsertRequest request) {
        if (!request.basic().periodEnd().isAfter(request.basic().periodStart())) {
            throw DomainException.unprocessable("period_end must be after period_start", "VALIDATION_ERROR");
        }
        PmsCycleType.fromWire(request.basic().type());

        Set<PmsCycleStage> seen = new HashSet<>();
        for (PmsCycleStageDto entry : request.stages()) {
            PmsCycleStage stage = PmsCycleStage.fromWire(entry.stage());
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

    /** The publish-time strict pass: everything {@link #validateCommon} does not already cover. */
    private void assertStrictlyComplete(String organisationId, PmsCycleEntity cycle) {
        PmsCycleApplicabilityEmbeddable applicability = cycle.getApplicability();
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
        UUID ratingScaleId = cycle.getFinalizeSettings().getRatingScaleId();
        if (ratingScaleId == null || ratingScales.findByIdAndOrganisationId(ratingScaleId, organisationId).isEmpty()) {
            throw DomainException.unprocessable("A valid rating scale must be selected.", "VALIDATION_ERROR");
        }

        Map<PmsCycleStage, PmsCycleStageEntity> byStage = new EnumMap<>(PmsCycleStage.class);
        cycle.getStages().forEach(row -> byStage.put(row.getStage(), row));
        for (PmsCycleStage stage : PmsCycleStage.values()) {
            PmsCycleStageEntity row = byStage.get(stage);
            if (row == null || row.getStartDate() == null || row.getEndDate() == null) {
                throw DomainException.unprocessable(
                        "Stage '" + stage.wire() + "' needs both a start and end date before publishing.",
                        "VALIDATION_ERROR");
            }
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private void applyMutableFields(PmsCycleEntity cycle, CycleDtos.UpsertRequest request) {
        cycle.replaceStages(mapper.toStageEntities(request.stages()));
        cycle.setApplicability(mapper.toApplicabilityEmbeddable(request.applicability()));
        cycle.setFinalizeSettings(mapper.toFinalizeEmbeddable(request.finalizeSettings()));
    }

    /**
     * Only {@code employees} is backed by a real count, via the same
     * eligible-employees RPC the preview screen uses. The other audiences would
     * need IAM to resolve distinct managers / HODs for the eligible cohort — no
     * such RPC exists yet, so they are reported at 0 rather than a fabricated number.
     */
    private List<PmsCycleNotificationModel> buildNotifications(String organisationId, PmsCycleEntity cycle) {
        List<PmsCycleNotificationModel> entries = new ArrayList<>();
        PmsCycleFinalizeEmbeddable finalizeSettings = cycle.getFinalizeSettings();
        if (finalizeSettings.isNotifyEmployees()) {
            int sent = 0;
            try {
                sent = previewEligibility(organisationId, mapper.toApplicabilityDto(cycle.getApplicability()))
                        .totalEligible();
            } catch (DomainException e) {
                log.warn("Publish: could not resolve eligible-employee count for notifications ({})",
                        e.getMessage());
            }
            entries.add(new PmsCycleNotificationModel("employees", sent));
        }
        if (finalizeSettings.isNotifyManagers()) {
            entries.add(new PmsCycleNotificationModel("reporting_managers", 0));
        }
        if (finalizeSettings.isNotifyHod()) {
            entries.add(new PmsCycleNotificationModel("hod_reviewers", 0));
        }
        if (finalizeSettings.isNotifyHr()) {
            entries.add(new PmsCycleNotificationModel("hr", 0));
        }
        return entries;
    }

    private PmsCycleEntity find(String organisationId, UUID cycleId) {
        return cycles.findByIdAndOrganisationId(cycleId, organisationId)
                .orElseThrow(() -> DomainException.notFound("Cycle not found", "PMS_CYCLE_NOT_FOUND"));
    }

    private CycleDtos.CycleDto toDto(String organisationId, PmsCycleEntity cycle) {
        PmsCycleDto dto = mapper.toDto(cycle);
        return new CycleDtos.CycleDto(dto.id(), dto.cycleCode(), dto.status(), dto.createdDate().toLocalDate(),
                dto.publishedOn(), applicableToResolver.resolve(organisationId, dto.applicability()),
                new CycleDtos.Basic(dto.name(), dto.description(), dto.type(), dto.periodStart(), dto.periodEnd()),
                dto.stages(), dto.applicability(), dto.finalizeSettings());
    }

    private CycleDtos.ListItem toListItem(String organisationId, PmsCycleEntity cycle) {
        return new CycleDtos.ListItem(cycle.getId(), cycle.getCycleCode(), cycle.getName(), cycle.getType().wire(),
                cycle.getPeriodStart(), cycle.getPeriodEnd(),
                applicableToResolver.resolve(organisationId, mapper.toApplicabilityDto(cycle.getApplicability())),
                cycle.getStatus().wire(), cycle.getCreatedDate().toLocalDate());
    }

    private CycleDtos.ActivationDto toActivationDto(String organisationId, PmsCycleEntity cycle) {
        PmsCycleDto dto = mapper.toDto(cycle);
        return new CycleDtos.ActivationDto(toDto(organisationId, cycle), cycle.getPublishedOn(),
                dto.notifications());
    }
}
