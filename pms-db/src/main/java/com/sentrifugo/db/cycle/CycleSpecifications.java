package com.sentrifugo.db.cycle;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.Month;

/**
 * Filters for {@code GET /pms/cycles}. Kept separate from the repository so
 * {@link #search} (all filters, for the page) and {@link #searchIgnoringStatus}
 * (every filter except status, for the stat-card summary per the contract) can
 * share the same building blocks without duplicating each predicate.
 */
public final class CycleSpecifications {

    private CycleSpecifications() {
    }

    public static Specification<Cycle> search(String organisationId, String search, Integer financialYearStart,
                                              CycleType type, String plantId, CycleStatus status) {
        return base(organisationId, search, financialYearStart, type, plantId).and(statusIs(status));
    }

    public static Specification<Cycle> searchIgnoringStatus(String organisationId, String search,
                                                             Integer financialYearStart, CycleType type,
                                                             String plantId) {
        return base(organisationId, search, financialYearStart, type, plantId);
    }

    private static Specification<Cycle> base(String organisationId, String search, Integer financialYearStart,
                                              CycleType type, String plantId) {
        return notDeleted(organisationId)
                .and(matchesSearch(search))
                .and(inFinancialYear(financialYearStart))
                .and(typeIs(type))
                .and(coversPlant(plantId));
    }

    /** An always-true predicate — the neutral element for {@code .and(...)} chains,
     * used instead of {@code Specification.where(null)}, which is ambiguous here
     * between this version's overloads. */
    private static Specification<Cycle> always() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Cycle> notDeleted(String organisationId) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("organisationId"), organisationId),
                cb.isNull(root.get("deletedOn")));
    }

    private static Specification<Cycle> matchesSearch(String search) {
        if (search == null || search.isBlank()) {
            return always();
        }
        String pattern = "%" + search.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("cycleCode")), pattern),
                cb.like(cb.lower(root.get("name")), pattern));
    }

    /** A fiscal year April(Y)–March(Y+1) is "contained" by a cycle when the cycle's
     * period overlaps it at all — matches how the UI's "FY 2026-27" filter is meant
     * to read (any cycle touching that year), not an exact period match. */
    private static Specification<Cycle> inFinancialYear(Integer financialYearStart) {
        if (financialYearStart == null) {
            return always();
        }
        LocalDate fyStart = LocalDate.of(financialYearStart, Month.APRIL, 1);
        LocalDate fyEnd = LocalDate.of(financialYearStart + 1, Month.MARCH, 31);
        return (root, query, cb) -> cb.and(
                cb.lessThanOrEqualTo(root.get("periodStart"), fyEnd),
                cb.greaterThanOrEqualTo(root.get("periodEnd"), fyStart));
    }

    private static Specification<Cycle> typeIs(CycleType type) {
        if (type == null) {
            return always();
        }
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    private static Specification<Cycle> statusIs(CycleStatus status) {
        if (status == null) {
            return always();
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** True when the cycle's applicability covers `plantId` — either it is explicitly
     * listed, or the applicability is empty (drafts default to "every plant" until
     * narrowed; see PmsCycleService#applicableToLabel for the published-cycle case,
     * which requires an explicit, complete plant list). */
    private static Specification<Cycle> coversPlant(String plantId) {
        if (plantId == null || plantId.isBlank()) {
            return always();
        }
        return (root, query, cb) -> {
            Predicate explicit = cb.isTrue(cb.function(
                    "jsonb_exists", Boolean.class, root.get("applicability").get("plantIds"), cb.literal(plantId)));
            Predicate empty = cb.equal(
                    cb.function("jsonb_array_length", Integer.class, root.get("applicability").get("plantIds")), 0);
            return cb.or(explicit, empty);
        };
    }
}
