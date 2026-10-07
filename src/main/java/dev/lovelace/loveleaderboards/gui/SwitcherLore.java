package dev.lovelace.loveleaderboards.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Pure helper for header switch buttons (period / category / mode) in the
 * "members menu" style: the lore lists every option, marks the current one
 * and ends with the LMB/RMB hint. No Bukkit dependency, so it is unit-testable.
 */
public final class SwitcherLore {
    /** Longer option lists are shown as a window of +-WINDOW_RADIUS around the current option. */
    public static final int MAX_FULL_OPTIONS = 7;
    public static final int WINDOW_RADIUS = 3;

    public static final String CURRENT_PREFIX = "&a▶ ";
    public static final String OTHER_PREFIX = "&7  ";
    public static final String ELLIPSIS = "&7  …";
    public static final String HINT = "&eЛКМ &7- дальше, &eПКМ &7- назад";

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

    /** Builds the option lines (with "…" markers when windowed), an empty line and the click hint. */
    public static List<String> build(List<String> options, int current) {
        List<String> lore = new ArrayList<>();
        int[] range = window(current, options.size());
        if (range[0] > 0) lore.add(ELLIPSIS);
        for (int i = range[0]; i < range[1]; i++) {
            String name = strip(options.get(i));
            lore.add((i == current ? CURRENT_PREFIX : OTHER_PREFIX) + name);
        }
        if (range[1] < options.size()) lore.add(ELLIPSIS);
        lore.add("");
        lore.add(HINT);
        return lore;
    }

    /** Removes legacy colour codes so the option colour is driven only by current/other state. */
    public static String strip(String text) {
        if (text == null) return "";
        return COLOR_CODES.matcher(text).replaceAll("");
    }
}
