package com.sentrifugo.pms.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Achievement and red / amber / green status for the manager dashboards (screens 3.9 - 3.11).
 *
 * <p>Achievement is {@code actual / target * 100}. A zero target (e.g. "zero LTI") is met only by a zero
 * actual. The KPI master has no "lower is better" direction yet, so a KPI that should come in under its
 * target is scored as if higher were better; add a direction to the KPI master to fix that.
 */
public final class PmsAchievement {

    public static final String ON_TRACK = "on_track";
    public static final String AT_RISK = "at_risk";
    public static final String OFF_TRACK = "off_track";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ON_TRACK_FROM = BigDecimal.valueOf(95);
    private static final BigDecimal AT_RISK_FROM = BigDecimal.valueOf(90);

    private PmsAchievement() {
    }

    /** Achievement in percent to one decimal place, or null when there is no actual or no target. */
    public static BigDecimal percent(BigDecimal actual, BigDecimal target) {
        if (actual == null || target == null) {
            return null;
        }
        if (target.signum() == 0) {
            return actual.signum() == 0 ? HUNDRED.setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(1);
        }
        return actual.multiply(HUNDRED).divide(target, 1, RoundingMode.HALF_UP);
    }

    /** {@code on_track} from 95%, {@code at_risk} from 90%, otherwise {@code off_track}; null when unknown. */
    public static String rag(BigDecimal percent) {
        if (percent == null) {
            return null;
        }
        if (percent.compareTo(ON_TRACK_FROM) >= 0) {
            return ON_TRACK;
        }
        return percent.compareTo(AT_RISK_FROM) >= 0 ? AT_RISK : OFF_TRACK;
    }

    /** One KPI's achievement with the weight it carries. */
    public record Weighted(BigDecimal weight, BigDecimal percent) {
    }

    /** Weighted mean of the KPIs that have an achievement; null when none do. */
    public static BigDecimal weightedAverage(List<Weighted> items) {
        BigDecimal weightSum = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (Weighted w : items) {
            if (w.percent() == null || w.weight() == null) {
                continue;
            }
            weightSum = weightSum.add(w.weight());
            total = total.add(w.percent().multiply(w.weight()));
        }
        if (weightSum.signum() == 0) {
            return null;
        }
        return total.divide(weightSum, 1, RoundingMode.HALF_UP);
    }
}
