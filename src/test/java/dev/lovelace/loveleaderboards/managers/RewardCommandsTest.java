package dev.lovelace.loveleaderboards.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RewardCommandsTest {

    @Test
    void rendersAllPlaceholders() {
        assertEquals("give Ada 5 for Ada 1200", RewardCommands.render("give %player% 5 for %clan% %score%", "Ada", 1200.7));
    }

    @Test
    void onlyOfflinePlayersAreDeferred() {
        assertTrue(RewardCommands.shouldDefer("player", false));
        assertFalse(RewardCommands.shouldDefer("player", true));
        assertFalse(RewardCommands.shouldDefer("clan", false));
    }

    @Test
    void detectsAmpersandCodesInComponentCommands() {
        assertTrue(RewardCommands.usesLegacyCodesInComponentCommand("title Ada title &6&lМастер Охоты"));
        assertTrue(RewardCommands.usesLegacyCodesInComponentCommand("/minecraft:actionbar Ada &aПривет"));
        assertFalse(RewardCommands.usesLegacyCodesInComponentCommand("title Ada title {\"text\":\"Мастер\",\"color\":\"gold\"}"));
        assertFalse(RewardCommands.usesLegacyCodesInComponentCommand("broadcast &6Клан победил"));
        assertFalse(RewardCommands.usesLegacyCodesInComponentCommand("title Ada times 10 70 20"));
    }
}
