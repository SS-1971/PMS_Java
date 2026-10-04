package com.sentrifugo.pms.service;

import com.sentrifugo.pms.db.enums.PmsCycleType;
import com.sentrifugo.pms.db.repository.PmsCycleRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Month;

/**
 * {@code PMS-{FYstart yy}{FYend yy}-{A|M|C}}, with a numeric suffix
 * ({@code PMS-2627-A2}) when the base code is already taken — per the PMS
 * Cycle contract's "Conventions" section. The financial year is assumed
 * April–March (the contract's own open question #2 flags this as unconfirmed).
 */
@Component
public class CycleCodeGenerator {

    private final PmsCycleRepository cycles;

    public CycleCodeGenerator(PmsCycleRepository cycles) {
        this.cycles = cycles;
    }

    public String generate(PmsCycleType type, LocalDate periodStart) {
        int fyStart = periodStart.getMonthValue() >= Month.APRIL.getValue()
                ? periodStart.getYear() : periodStart.getYear() - 1;
        int fyEnd = fyStart + 1;
        String letter = switch (type) {
            case ANNUAL -> "A";
            case MID_YEAR -> "M";
            case CUSTOM -> "C";
        };
        String base = "PMS-" + yy(fyStart) + yy(fyEnd) + "-" + letter;
        if (!cycles.existsByCycleCode(base)) {
            return base;
        }
        for (int suffix = 2; suffix < 100; suffix++) {
            String candidate = base + suffix;
            if (!cycles.existsByCycleCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Exhausted cycle_code suffixes for " + base);
    }

    private static String yy(int year) {
        return String.format("%02d", year % 100);
    }
}
