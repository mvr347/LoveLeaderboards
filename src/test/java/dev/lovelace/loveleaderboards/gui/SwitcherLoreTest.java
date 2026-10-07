package dev.lovelace.loveleaderboards.gui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SwitcherLoreTest {

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
        assertEquals(List.of("&7  Opt0", "&a▶ Opt1", "&7  Opt2", "", SwitcherLore.HINT), lore);
    }

    @Test
    void windowCentredWithMarkers() {
        assertArrayEquals(new int[] {2, 9}, SwitcherLore.window(5, 12));
        List<String> lore = SwitcherLore.build(options(12), 5);
        assertEquals(SwitcherLore.ELLIPSIS, lore.get(0));
        assertEquals("&7  Opt2", lore.get(1));
        assertEquals("&a▶ Opt5", lore.get(4));
        assertEquals("&7  Opt8", lore.get(7));
        assertEquals(SwitcherLore.ELLIPSIS, lore.get(8));
        assertEquals(11, lore.size());
    }

    @Test
    void windowShiftsInwardAtEdges() {
        assertArrayEquals(new int[] {0, 7}, SwitcherLore.window(0, 10));
        assertArrayEquals(new int[] {3, 10}, SwitcherLore.window(9, 10));
        List<String> start = SwitcherLore.build(options(10), 0);
        assertEquals("&a▶ Opt0", start.get(0));
        assertEquals(SwitcherLore.ELLIPSIS, start.get(7));
        List<String> end = SwitcherLore.build(options(10), 9);
        assertEquals(SwitcherLore.ELLIPSIS, end.get(0));
        assertEquals("&a▶ Opt9", end.get(7));
    }

    @Test
    void stripsColourCodes() {
        assertEquals("Всё время", SwitcherLore.strip("&6Всё время"));
        assertEquals("x", SwitcherLore.strip("§lx"));
    }
}
