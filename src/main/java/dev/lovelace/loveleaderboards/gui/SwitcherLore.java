package dev.lovelace.loveleaderboards.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Pure helper for header switch buttons (period / category / mode). The lore lists every
 * option, marks the current one with a check mark and ends with the click block from
 * {@link LoreStyle}. No Bukkit dependency, so it is unit-testable.
 */
public final class SwitcherLore {
    /** Longer option lists are shown as a window of +-WINDOW_RADIUS around the current option. */
    public static final int MAX_FULL_OPTIONS = 7;
    public static final int WINDOW_RADIUS = 3;

    public static final String SUBTITLE = "<dark_gray>Переключатель";
    public static final String OPTIONS_TITLE = "<dark_gray>▪ <gray>Варианты:";
    /** The check mark marks the active option; its name inherits the green colour. */
    public static final String CURRENT_PREFIX = "  <green>✓ ";
    public static final String OTHER_PREFIX = "    <gray>";
    public static final String ELLIPSIS = "    <dark_gray>…";
    public static final String NEXT_ACTION = "следующий вариант";
    public static final String PREVIOUS_ACTION = "предыдущий вариант";

    private static final Pattern COLOR_CODES = Pattern.compile("(?i)[&§][0-9a-fk-or]");

    private SwitcherLore() {
    }

    /** Index after {@code current}, wrapping around. */
    public static int next(int current, int size) {
        if (size <= 0) return 0;
        return Math.floorMod(current + 1, size);
    }

    /** Index before {@code current}, wrapping around. */
    public static int previous(int current, int size) {
        if (size <= 0) return 0;
        return Math.floorMod(current - 1, size);
    }

    /** Left click goes forward, right click goes back. */
    public static int cycle(int current, int size, boolean rightClick) {
        return rightClick ? previous(current, size) : next(current, size);
    }

    /**
     * Returns the [start, end) range of options to show. Lists up to {@link #MAX_FULL_OPTIONS}
     * are shown in full; longer ones show a window of 7 centred on the current option,
     * shifted inwards near the edges so it always holds 7 entries.
     */
    public static int[] window(int current, int size) {
        if (size <= MAX_FULL_OPTIONS) return new int[] {0, size};
        int width = WINDOW_RADIUS * 2 + 1;
        int c = Math.max(0, Math.min(current, size - 1));
        int start = Math.max(0, Math.min(c - WINDOW_RADIUS, size - width));
        return new int[] {start, start + width};
    }

    /** Subtitle, option list and the LMB/RMB click block. */
    public static List<String> build(List<String> options, int current) {
        List<String> lore = header(options, current);
        lore.addAll(LoreStyle.clicks(
            LoreStyle.click(LoreStyle.Button.LEFT, NEXT_ACTION),
            LoreStyle.click(LoreStyle.Button.RIGHT, PREVIOUS_ACTION)));
        return lore;
    }

    /** Same option list, but the switch is unavailable: separator, status, reason, blocked click. */
    public static List<String> buildBlocked(List<String> options, int current, String reason, String action) {
        List<String> lore = header(options, current);
        lore.addAll(LoreStyle.blocked(reason, LoreStyle.Button.LEFT, action));
        return lore;
    }

    private static List<String> header(List<String> options, int current) {
        List<String> lore = new ArrayList<>();
        lore.add(SUBTITLE);
        lore.add("");
        lore.add(OPTIONS_TITLE);
        int[] range = window(current, options.size());
        if (range[0] > 0) lore.add(ELLIPSIS);
        for (int i = range[0]; i < range[1]; i++) {
            String name = strip(options.get(i));
            lore.add((i == current ? CURRENT_PREFIX : OTHER_PREFIX) + name);
        }
        if (range[1] < options.size()) lore.add(ELLIPSIS);
        return lore;
    }

    /** Removes legacy colour codes so the option colour is driven only by current/other state. */
    public static String strip(String text) {
        if (text == null) return "";
        return COLOR_CODES.matcher(text).replaceAll("");
    }
}
