package dev.lovelace.loveleaderboards.integrations;

import java.util.Locale;

/**
 * Pure parsing of {@code integrations.npc-provider}. Kept free of Bukkit and LoveCore types so it can be
 * unit-tested; an unknown value falls back to the default instead of silently disabling the stands' NPCs.
 */
public final class NpcProviderChoice {

    public static final String CITIZENS = "citizens";
    public static final String LOVEESSENTIAL = "loveessential";
    public static final String DEFAULT = CITIZENS;
    /** Config value that follows {@code integrations.provider} of LoveCore; {@code StandNpcs} resolves it. */
    public static final String GLOBAL = "global";

    /** @param id the provider id to use; @param recognized false if the raw value was unknown and the default was used */
    public record Result(String id, boolean recognized) {}

    private NpcProviderChoice() {}

    /** Blank, missing and {@code global} give {@link #GLOBAL}; an unknown value falls back to {@link #DEFAULT}. */
    public static Result parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Result(GLOBAL, true);
        }
        String id = raw.trim().toLowerCase(Locale.ROOT);
        if (id.equals(GLOBAL) || id.equals(CITIZENS) || id.equals(LOVEESSENTIAL)) {
            return new Result(id, true);
        }
        return new Result(DEFAULT, false);
    }
}
