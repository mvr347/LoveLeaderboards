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

    private static final Pattern LEGACY_SPLIT = Pattern.compile("(?i)&(#[0-9a-f]{6}|[0-9a-fk-or])");
    private static final String[] COLOR_NAMES = {
        "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
        "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};

    /**
     * Makes a /title-like command that still carries '&' codes work: the text after the target (and after
     * title/subtitle/actionbar for /title) becomes a JSON component. 2026-10-25: configs written before 1.21.5
     * keep such commands, which the server rejects; converting them at run time fixes the reward without
     * touching the admin's file. Any other command is returned unchanged.
     */
    public static String withJsonText(String command) {
        if (!usesLegacyCodesInComponentCommand(command)) return command;
        String[] parts = command.trim().split(" ", -1);
        int at = command.trim().startsWith("/") ? 1 : 0;
        String name = parts[0].toLowerCase(Locale.ROOT).replaceFirst("^/?(minecraft:)?", "");
        int textFrom;
        if (name.equals("title")) {
            if (parts.length < 4 || !java.util.Set.of("title", "subtitle", "actionbar").contains(parts[2].toLowerCase(Locale.ROOT)))
                return command;
            textFrom = 3;
        } else textFrom = 2;
        if (parts.length <= textFrom) return command;
        String text = String.join(" ", java.util.Arrays.copyOfRange(parts, textFrom, parts.length));
        if (text.startsWith("{") || text.startsWith("[") || text.startsWith("\"")) return command;
        return String.join(" ", java.util.Arrays.copyOfRange(parts, 0, textFrom)) + " " + toJson(text);
    }

    /** Legacy '&' text as a JSON array of styled parts; a colour code resets bold/italic/... like legacy formatting. */
    static String toJson(String legacy) {
        var out = new StringBuilder("[{\"text\":\"\"}");
        var matcher = LEGACY_SPLIT.matcher(legacy);
        String color = null;
        boolean bold = false, italic = false, underlined = false, strike = false, obfuscated = false;
        int last = 0;
        while (true) {
            boolean found = matcher.find();
            int end = found ? matcher.start() : legacy.length();
            if (end > last) {
                out.append(",{\"text\":\"").append(escape(legacy.substring(last, end))).append('"');
                if (color != null) out.append(",\"color\":\"").append(color).append('"');
                if (bold) out.append(",\"bold\":true");
                if (italic) out.append(",\"italic\":true");
                if (underlined) out.append(",\"underlined\":true");
                if (strike) out.append(",\"strikethrough\":true");
                if (obfuscated) out.append(",\"obfuscated\":true");
                out.append('}');
            }
            if (!found) break;
            String code = matcher.group(1).toLowerCase(Locale.ROOT);
            if (code.startsWith("#")) {
                color = code;
                bold = italic = underlined = strike = obfuscated = false;
            } else {
                char c = code.charAt(0);
                int index = "0123456789abcdef".indexOf(c);
                if (index >= 0) {
                    color = COLOR_NAMES[index];
                    bold = italic = underlined = strike = obfuscated = false;
                } else
                    switch (c) {
                        case 'l' -> bold = true;
                        case 'o' -> italic = true;
                        case 'n' -> underlined = true;
                        case 'm' -> strike = true;
                        case 'k' -> obfuscated = true;
                        default -> {
                            color = null;
                            bold = italic = underlined = strike = obfuscated = false;
                        }
                    }
            }
            last = matcher.end();
        }
        return out.append(']').toString();
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
