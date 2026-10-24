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

    @Test
    void ampersandTitleBecomesAJsonComponent() {
        assertEquals(
                "title Ada title [{\"text\":\"\"},{\"text\":\"Мастер Охоты\",\"color\":\"gold\",\"bold\":true}]",
                RewardCommands.withJsonText("title Ada title &6&lМастер Охоты"));
        assertEquals(
                "actionbar Ada [{\"text\":\"\"},{\"text\":\"Ура \",\"color\":\"green\"},{\"text\":\"клан\",\"color\":\"white\"}]",
                RewardCommands.withJsonText("actionbar Ada &aУра &fклан"));
    }

    @Test
    void jsonAndOtherCommandsAreLeftAlone() {
        String json = "title Ada title {\"text\":\"Мастер\",\"color\":\"gold\"}";
        assertEquals(json, RewardCommands.withJsonText(json));
        assertEquals("broadcast &6Клан победил", RewardCommands.withJsonText("broadcast &6Клан победил"));
        assertEquals("title Ada times 10 70 20", RewardCommands.withJsonText("title Ada times 10 70 20"));
    }

    @Test
    void quotesInTheTextAreEscaped() {
        assertTrue(RewardCommands.withJsonText("title Ada title &6Он сказал \"да\"").contains("\\\"да\\\""));
    }
}
