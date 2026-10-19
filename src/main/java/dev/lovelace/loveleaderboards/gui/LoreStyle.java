package dev.lovelace.loveleaderboards.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared MiniMessage lore pieces for GUI buttons: the separator, click lines and the
 * blocked-button block. Pure strings with no Bukkit dependency, so the style is unit-testable.
 *
 * Colours are fixed by the GUI style standard: LMB green, RMB gold, Shift+click yellow,
 * the dash and the separator dark gray, the action text gray.
 */
public final class LoreStyle {

    /** Separator placed directly before a click block or a blocked block. */
    public static final String SEPARATOR = "<dark_gray><st>─────────────────────</st>";

    /** Status line of a button that cannot be used right now. */
    public static final String UNAVAILABLE = "<red>✕ Действие недоступно";

    /** Mouse input of a click line. {@code colour} is the MiniMessage colour tag name. */
    public enum Button {
        LEFT("green", "ЛКМ"),
        RIGHT("gold", "ПКМ"),
        SHIFT_LEFT("yellow", "Shift+ЛКМ"),
        SHIFT_RIGHT("yellow", "Shift+ПКМ");

        private final String colour;
        private final String label;

        Button(String colour, String label) {
            this.colour = colour;
            this.label = label;
        }

        public String colour() {
            return colour;
        }

        public String label() {
            return label;
        }
    }

    private LoreStyle() {
    }

    /** One click line, e.g. {@code <green>ЛКМ <dark_gray>— <gray>статистика игрока}. */
    public static String click(Button button, String action) {
        return "<" + button.colour() + ">" + button.label() + " <dark_gray>— <gray>" + action;
    }

    /** A click that is not available: dark gray, with the "(заблокировано)" suffix. */
    public static String blockedClick(Button button, String action) {
        return "<dark_gray>" + button.label() + " — " + action + " (заблокировано)";
    }

    /** Reason line of a blocked button. */
    public static String reason(String text) {
        return "<red>Причина: <gray>" + text;
    }

    /** Separator followed by the given click lines. */
    public static List<String> clicks(String... lines) {
        List<String> out = new ArrayList<>();
        out.add(SEPARATOR);
        for (String line : lines) {
            out.add(line);
        }
        return out;
    }

    /** Block for an unavailable button: separator, status, reason, blocked click. */
    public static List<String> blocked(String reason, Button button, String action) {
        List<String> out = new ArrayList<>();
        out.add(SEPARATOR);
        out.add(UNAVAILABLE);
        out.add(reason(reason));
        out.add(blockedClick(button, action));
        return out;
    }
}
