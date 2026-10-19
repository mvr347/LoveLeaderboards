package dev.lovelace.loveleaderboards.gui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwitcherLoreTest {

    private static final String NEXT = LoreStyle.click(LoreStyle.Button.LEFT, SwitcherLore.NEXT_ACTION);
    private static final String PREVIOUS = LoreStyle.click(LoreStyle.Button.RIGHT, SwitcherLore.PREVIOUS_ACTION);

    private static List<String> options(int n) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add("&bOpt" + i);
        return list;
    }

    @Test
    void cyclingWrapsBothWays() {
        assertEquals(1, SwitcherLore.next(0, 4));
        assertEquals(0, SwitcherLore.next(3, 4));
        assertEquals(3, SwitcherLore.previous(0, 4));
        assertEquals(2, SwitcherLore.previous(3, 4));
        assertEquals(1, SwitcherLore.cycle(0, 4, false));
        assertEquals(3, SwitcherLore.cycle(0, 4, true));
    }

    @Test
    void twoOptionToggleFlipsOnEitherClick() {
        assertEquals(1, SwitcherLore.cycle(0, 2, false));
        assertEquals(1, SwitcherLore.cycle(0, 2, true));
        assertEquals(0, SwitcherLore.cycle(1, 2, false));
        assertEquals(0, SwitcherLore.cycle(1, 2, true));
    }

    @Test
    void emptyListIsSafe() {
        assertEquals(0, SwitcherLore.next(0, 0));
        assertEquals(0, SwitcherLore.previous(0, 0));
    }

    @Test
    void shortListShownInFull() {
        assertArrayEquals(new int[] {0, 7}, SwitcherLore.window(6, 7));
        List<String> lore = SwitcherLore.build(options(3), 1);
        assertEquals(List.of(
                SwitcherLore.SUBTITLE,
                "",
                SwitcherLore.OPTIONS_TITLE,
                "    <gray>Opt0",
                "  <green>✓ Opt1",
                "    <gray>Opt2",
                LoreStyle.SEPARATOR,
                NEXT,
                PREVIOUS), lore);
    }

    @Test
    void windowCentredWithMarkers() {
        assertArrayEquals(new int[] {2, 9}, SwitcherLore.window(5, 12));
        List<String> lore = SwitcherLore.build(options(12), 5);
        assertEquals(SwitcherLore.ELLIPSIS, lore.get(3));
        assertEquals("    <gray>Opt2", lore.get(4));
        assertEquals("  <green>✓ Opt5", lore.get(7));
        assertEquals("    <gray>Opt8", lore.get(10));
        assertEquals(SwitcherLore.ELLIPSIS, lore.get(11));
        assertEquals(LoreStyle.SEPARATOR, lore.get(12));
        assertEquals(15, lore.size());
    }

    @Test
    void windowShiftsInwardAtEdges() {
        assertArrayEquals(new int[] {0, 7}, SwitcherLore.window(0, 10));
        assertArrayEquals(new int[] {3, 10}, SwitcherLore.window(9, 10));
        List<String> start = SwitcherLore.build(options(10), 0);
        assertEquals("  <green>✓ Opt0", start.get(3));
        assertEquals(SwitcherLore.ELLIPSIS, start.get(10));
        List<String> end = SwitcherLore.build(options(10), 9);
        assertEquals(SwitcherLore.ELLIPSIS, end.get(3));
        assertEquals("  <green>✓ Opt9", end.get(10));
    }

    @Test
    void clickBlockFollowsSeparator() {
        List<String> lore = SwitcherLore.build(options(4), 2);
        int first = lore.indexOf(NEXT);
        assertEquals(LoreStyle.SEPARATOR, lore.get(first - 1));
        assertEquals(PREVIOUS, lore.get(first + 1));
    }

    @Test
    void blockedSwitchShowsStatusAndReasonInsteadOfClicks() {
        List<String> lore = SwitcherLore.buildBlocked(options(2), 0, "Вы не состоите в клане", "переключить режим");
        assertEquals(List.of(
                SwitcherLore.SUBTITLE,
                "",
                SwitcherLore.OPTIONS_TITLE,
                "  <green>✓ Opt0",
                "    <gray>Opt1",
                LoreStyle.SEPARATOR,
                LoreStyle.UNAVAILABLE,
                "<red>Причина: <gray>Вы не состоите в клане",
                "<dark_gray>ЛКМ — переключить режим (заблокировано)"), lore);
        assertTrue(lore.stream().noneMatch(line -> line.startsWith("<green>ЛКМ")));
    }

    @Test
    void stripsColourCodes() {
        assertEquals("Всё время", SwitcherLore.strip("&6Всё время"));
        assertEquals("x", SwitcherLore.strip("§lx"));
    }
}
