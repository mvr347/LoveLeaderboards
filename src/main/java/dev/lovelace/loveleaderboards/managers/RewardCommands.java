package dev.lovelace.loveleaderboards.managers;

import java.util.Locale;
import java.util.regex.Pattern;

/** Pure helpers for reward commands: placeholder rendering and the checks that decide how a reward is delivered. */
public final class RewardCommands {
    private static final Pattern LEGACY_CODE = Pattern.compile("(?i)&[0-9a-fk-or]");
    private static final java.util.Set<String> COMPONENT_COMMANDS = java.util.Set.of("title", "subtitle", "actionbar", "tellraw");

    private RewardCommands() {}

    /** Replaces the reward placeholders; {@code name} is the player name or the clan name. */
    public static String render(String command, String name, double score) {
        return command.replace("%player%", name)
                .replace("%clan%", name)
                .replace("%clan_name%", name)
                .replace("%score%", String.valueOf((long) score));
    }

    /** Player rewards go through commands that need the player online (give, title), so an offline winner waits for the join. */
    public static boolean shouldDefer(String entityType, boolean online) {
        return "player".equalsIgnoreCase(entityType) && !online;
    }

    /**
     * True for a /title-like command that carries '&' colour codes. Since 1.21.5 these commands accept only a text
     * component (JSON/SNBT), so such a command always fails with a syntax error.
     */
    public static boolean usesLegacyCodesInComponentCommand(String command) {
        String trimmed = command.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        if (trimmed.startsWith("minecraft:")) trimmed = trimmed.substring("minecraft:".length());
        int space = trimmed.indexOf(' ');
        if (space < 0 || !COMPONENT_COMMANDS.contains(trimmed.substring(0, space))) return false;
        return LEGACY_CODE.matcher(command).find();
    }
}
