package dev.lovelace.loveleaderboards.integrations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcProviderChoiceTest {

    @Test
    void defaultsToCitizensWhenUnset() {
        assertEquals(new NpcProviderChoice.Result("citizens", true), NpcProviderChoice.parse(null));
        assertEquals(new NpcProviderChoice.Result("citizens", true), NpcProviderChoice.parse("  "));
    }

    @Test
    void acceptsBothProvidersCaseInsensitively() {
        assertEquals("loveessential", NpcProviderChoice.parse(" LoveEssential ").id());
        assertEquals("citizens", NpcProviderChoice.parse("CITIZENS").id());
        assertTrue(NpcProviderChoice.parse("loveessential").recognized());
    }

    @Test
    void unknownValueFallsBackToCitizensAndIsFlagged() {
        NpcProviderChoice.Result result = NpcProviderChoice.parse("znpcs");
        assertEquals("citizens", result.id());
        assertFalse(result.recognized());
    }

    @Test
    void missingProviderMessageNamesTheProviderAndThePlugin() {
        assertTrue(StandNpcs.missingProviderMessage("loveessential").contains("loveessential"));
        assertTrue(StandNpcs.missingProviderMessage("loveessential").contains("LoveEssential"));
        assertTrue(StandNpcs.missingProviderMessage("citizens").contains("Citizens"));
    }
}
