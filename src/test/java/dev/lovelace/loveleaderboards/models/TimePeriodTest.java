package dev.lovelace.loveleaderboards.models;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimePeriodTest {
    @Test
    void previousMonthKeyWithinYear() {
        assertEquals("2026-09", TimePeriod.previousMonthKey(YearMonth.of(2026, 10)));
    }

    @Test
    void previousMonthKeyCrossesYearBoundary() {
        assertEquals("2025-12", TimePeriod.previousMonthKey(YearMonth.of(2026, 1)));
    }

    @Test
    void monthlyDbKeyMatchesMonthKey() {
        assertEquals(TimePeriod.monthKey(YearMonth.now()), TimePeriod.MONTHLY.getDbKey());
    }
}
