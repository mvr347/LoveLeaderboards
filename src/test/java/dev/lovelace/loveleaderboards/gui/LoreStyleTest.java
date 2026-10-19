package dev.lovelace.loveleaderboards.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoreStyleTest {

    @Test
    void clickColoursFollowTheStandard() {
        assertEquals("<green>ЛКМ <dark_gray>— <gray>открыть", LoreStyle.click(LoreStyle.Button.LEFT, "открыть"));
        assertEquals("<gold>ПКМ <dark_gray>— <gray>сравнить", LoreStyle.click(LoreStyle.Button.RIGHT, "сравнить"));
        assertEquals("<yellow>Shift+ЛКМ <dark_gray>— <gray>x", LoreStyle.click(LoreStyle.Button.SHIFT_LEFT, "x"));
        assertEquals("<yellow>Shift+ПКМ <dark_gray>— <gray>x", LoreStyle.click(LoreStyle.Button.SHIFT_RIGHT, "x"));
    }

    @Test
    void clicksStartWithSeparator() {
        List<String> lore = LoreStyle.clicks(LoreStyle.click(LoreStyle.Button.LEFT, "a"));
        assertEquals(LoreStyle.SEPARATOR, lore.get(0));
        assertEquals("<green>ЛКМ <dark_gray>— <gray>a", lore.get(1));
        assertEquals(2, lore.size());
    }

    @Test
    void blockedBlockOrderIsSeparatorStatusReasonClick() {
        List<String> lore = LoreStyle.blocked("Нет клана", LoreStyle.Button.LEFT, "открыть");
        assertEquals(List.of(
                LoreStyle.SEPARATOR,
                "<red>✕ Действие недоступно",
                "<red>Причина: <gray>Нет клана",
                "<dark_gray>ЛКМ — открыть (заблокировано)"), lore);
    }

    @Test
    void separatorIsDarkGrayAndStruckThrough() {
        assertTrue(LoreStyle.SEPARATOR.startsWith("<dark_gray><st>"));
        assertTrue(LoreStyle.SEPARATOR.endsWith("</st>"));
    }
}
