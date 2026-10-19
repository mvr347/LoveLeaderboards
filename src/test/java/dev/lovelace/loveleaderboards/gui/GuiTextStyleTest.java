package dev.lovelace.loveleaderboards.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Text-level guard for the GUI lore style. It reads the GUI sources and config.yml as text,
 * so it needs no server. {@link LoreStyle} defines the colours and is checked by its own test,
 * so it is excluded from the colour scan.
 */
class GuiTextStyleTest {

    private static final Path GUI_DIR = Path.of("src/main/java/dev/lovelace/loveleaderboards/gui");
    private static final Path CONFIG = Path.of("src/main/resources/config.yml");

    /** Glyphs and phrases the GUI standard keeps out of names and lore. */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "[▶⭐•\\x{FE0F}\\x{1F300}-\\x{1FAFF}\\x{2694}\\x{2B50}]|Нажмите|Кликните");

    /** Colour tags that may stand right before a click label (dark gray covers blocked lines). */
    private static final String[] LMB_COLOURS = {"&a", "<green>", "&8", "<dark_gray>"};
    private static final String[] RMB_COLOURS = {"&6", "<gold>", "&8", "<dark_gray>"};
    private static final String[] SHIFT_COLOURS = {"&e", "<yellow>"};

    private static final Pattern MINI_TAG = Pattern.compile("<(green|gold|yellow|gray|white|dark_gray|red|st)>");
    private static final Pattern LEGACY_CODE = Pattern.compile("&[0-9a-fk-or]");
    private static final Pattern JAVA_STRING = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");

    @Test
    void noForbiddenGlyphsOrPhrasesInGuiText() throws IOException {
        List<String> hits = new ArrayList<>();
        List<Path> files = guiSources();
        files.add(CONFIG);
        for (Path file : files) {
            for (String line : textLines(file)) {
                if (FORBIDDEN.matcher(line).find()) {
                    hits.add(file.getFileName() + ": " + line.trim());
                }
            }
        }
        assertEquals(List.of(), hits);
    }

    @Test
    void clickLabelsUseTheStandardColours() throws IOException {
        List<String> problems = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        for (Path file : guiSources()) {
            if (!file.getFileName().toString().equals("LoreStyle.java")) files.add(file);
        }
        files.add(CONFIG);
        for (Path file : files) {
            String text = String.join("\n", textLines(file));
            checkLabel(problems, file, text, "ЛКМ", LMB_COLOURS, true);
            checkLabel(problems, file, text, "ПКМ", RMB_COLOURS, true);
            checkLabel(problems, file, text, "Shift", SHIFT_COLOURS, false);
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void miniMessageLinesDoNotMixLegacyCodes() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : guiSources()) {
            Matcher m = JAVA_STRING.matcher(String.join("\n", textLines(file)));
            while (m.find()) {
                String literal = m.group(1);
                if (MINI_TAG.matcher(literal).find() && LEGACY_CODE.matcher(literal).find()) {
                    problems.add(file.getFileName() + ": " + literal);
                }
            }
        }
        for (String line : textLines(CONFIG)) {
            if (MINI_TAG.matcher(line).find() && LEGACY_CODE.matcher(line).find()) {
                problems.add("config.yml: " + line.trim());
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void generatedClickBlocksFollowTheSeparator() {
        for (int size : new int[] {1, 2, 3, 7, 8, 12}) {
            List<String> options = new ArrayList<>();
            for (int i = 0; i < size; i++) options.add("Opt" + i);
            for (int current = 0; current < size; current++) {
                List<String> open = SwitcherLore.build(options, current);
                int first = open.indexOf(LoreStyle.click(LoreStyle.Button.LEFT, SwitcherLore.NEXT_ACTION));
                assertEquals(LoreStyle.SEPARATOR, open.get(first - 1), "size " + size + " current " + current);

                List<String> locked = SwitcherLore.buildBlocked(options, current, "Нет клана", "переключить режим");
                int status = locked.indexOf(LoreStyle.UNAVAILABLE);
                assertEquals(LoreStyle.SEPARATOR, locked.get(status - 1), "size " + size + " current " + current);
            }
        }
    }

    /** Every occurrence of {@code label} must follow one of the allowed colour tags. */
    private static void checkLabel(List<String> problems, Path file, String text, String label,
                                   String[] allowed, boolean skipShiftPrefixed) {
        int from = 0;
        while ((from = text.indexOf(label, from)) >= 0) {
            String before = text.substring(0, from);
            boolean shiftPrefixed = skipShiftPrefixed && before.endsWith("Shift+");
            if (!shiftPrefixed && Arrays.stream(allowed).noneMatch(before::endsWith)) {
                problems.add(file.getFileName() + ": " + label + " without a colour tag at offset " + from);
            }
            from += label.length();
        }
    }

    /** Lines of a file without comment lines (YAML {@code #}, Java {@code //}, javadoc {@code *}). */
    private static List<String> textLines(Path file) throws IOException {
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.startsWith("#") || t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) continue;
            out.add(line);
        }
        return out;
    }

    private static List<Path> guiSources() throws IOException {
        try (Stream<Path> files = Files.list(GUI_DIR)) {
            return new ArrayList<>(files.filter(p -> p.toString().endsWith(".java")).sorted().toList());
        }
    }
}
