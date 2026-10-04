package com.sentrifugo.pms.db.util;

import com.sentrifugo.pms.db.entity.PmsCycleEntity;
import com.sentrifugo.pms.db.enums.PmsCycleStatus;
import com.sentrifugo.pms.db.enums.PmsCycleType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.Month;

/**
 * Filters for the cycle list screen. {@link #search} applies every filter;
 * {@link #searchIgnoringStatus} applies all but status (for the stat-card summary,
 * which must stay stable while a status tab is selected). Every specification is
 * organisation-scoped; soft-deleted rows are excluded by the entity's
 * {@code @SQLRestriction}.
 */
public final class PmsCycleSpecifications {

    private PmsCycleSpecifications() {
    }

    public static Specification<PmsCycleEntity> search(String organisationId, String search,
                                                       Integer financialYearStart, PmsCycleType type,
                                                       String plantId, PmsCycleStatus status) {
        return base(organisationId, search, financialYearStart, type, plantId).and(statusIs(status));
    }

    public static Specification<PmsCycleEntity> searchIgnoringStatus(String organisationId, String search,
                                                                      Integer financialYearStart,
                                                                      PmsCycleType type, String plantId) {
        return base(organisationId, search, financialYearStart, type, plantId);
    }

    private static Specification<PmsCycleEntity> base(String organisationId, String search,
                                                      Integer financialYearStart, PmsCycleType type,
                                                      String plantId) {
        return inOrganisation(organisationId)
                .and(matchesSearch(search))
                .and(inFinancialYear(financialYearStart))
                .and(typeIs(type))
                .and(coversPlant(plantId));
    }

    /** An always-true predicate — the neutral element for {@code .and(...)} chains. */
    private static Specification<PmsCycleEntity> always() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<PmsCycleEntity> inOrganisation(String organisationId) {
        return (root, query, cb) -> cb.equal(root.get("organisationId"), organisationId);
    }

    private static Specification<PmsCycleEntity> matchesSearch(String search) {
        if (search == null || search.isBlank()) {
            return always();
        }
        String pattern = "%" + search.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("cycleCode")), pattern),
                cb.like(cb.lower(root.get("name")), pattern));
    }

    /** A fiscal year April(Y)–March(Y+1) matches any cycle whose period overlaps it. */
    private static Specification<PmsCycleEntity> inFinancialYear(Integer financialYearStart) {
        if (financialYearStart == null) {
            return always();
        }
        LocalDate fyStart = LocalDate.of(financialYearStart, Month.APRIL, 1);
        LocalDate fyEnd = LocalDate.of(financialYearStart + 1, Month.MARCH, 31);
        return (root, query, cb) -> cb.and(
                cb.lessThanOrEqualTo(root.get("periodStart"), fyEnd),
                cb.greaterThanOrEqualTo(root.get("periodEnd"), fyStart));
    }

    private static Specification<PmsCycleEntity> typeIs(PmsCycleType type) {
        if (type == null) {
            return always();
        }
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    private static Specification<PmsCycleEntity> statusIs(PmsCycleStatus status) {
        if (status == null) {
            return always();
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** True when the cycle's applicability lists {@code plantId}, or lists no plants at all (an unfinished draft). */
    private static Specification<PmsCycleEntity> coversPlant(String plantId) {
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
