package dev.lovelace.loveleaderboards.models;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.IsoFields;

public enum TimePeriod {
    TODAY("today", "&aСегодня", "&7Статистика за текущий день"),
    WEEKLY("weekly", "&bНеделя", "&7Статистика за текущую неделю"),
    MONTHLY("monthly", "&eМесяц", "&7Статистика за текущий месяц"),
    ALL_TIME("alltime", "&6Всё время", "&7Статистика за всё время");

    private final String key;
    private final String displayName;
    private final String description;

    TimePeriod(String key, String displayName, String description) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public TimePeriod next() {
        return switch (this) {
            case TODAY -> WEEKLY;
            case WEEKLY -> MONTHLY;
            case MONTHLY -> ALL_TIME;
            case ALL_TIME -> TODAY;
        };
    }

    public String getDbKey() {
        LocalDate now = LocalDate.now();
        return switch (this) {
            case TODAY -> "today:" + now.toString();
            case WEEKLY -> "week:" + now.get(IsoFields.WEEK_BASED_YEAR) + "-" + String.format("%02d", now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTHLY -> monthKey(YearMonth.from(now));
            case ALL_TIME -> "alltime";
        };
    }

    /** DB key of a month bucket, e.g. "2026-09". */
    public static String monthKey(YearMonth ym) {
        return String.format("%04d-%02d", ym.getYear(), ym.getMonthValue());
    }

    /** DB key of the month that ended before {@code today}'s month (rewards are paid for it). */
    public static String previousMonthKey(YearMonth current) {
        return monthKey(current.minusMonths(1));
    }

    public static TimePeriod fromString(String text) {
        if (text == null) return TODAY;
        for (TimePeriod tp : values()) {
            if (tp.key.equalsIgnoreCase(text) || tp.name().equalsIgnoreCase(text)) {
                return tp;
            }
        }
        if (text.startsWith("today")) return TODAY;
        if (text.startsWith("week")) return WEEKLY;
        if (text.contains("-") || text.startsWith("month")) return MONTHLY;
        return ALL_TIME;
    }
}
