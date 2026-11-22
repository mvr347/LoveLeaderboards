package dev.lovelace.loveleaderboards.integrations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StandNpcsTest {

    @Test
    void leftoverProviderValuesAreReportedButNeverUsed() {
        assertEquals("citizens", StandNpcs.ignoredProviderValue("citizens"));
        assertEquals("znpcs", StandNpcs.ignoredProviderValue(" znpcs "));
        assertNull(StandNpcs.ignoredProviderValue(null));
        assertNull(StandNpcs.ignoredProviderValue("  "));
        assertNull(StandNpcs.ignoredProviderValue(" GLOBAL "));
        assertNull(StandNpcs.ignoredProviderValue("LoveEssential"));
    }

    @Test
    void missingProviderMessageNamesTheProviderAndThePlugin() {
        String message = StandNpcs.missingProviderMessage("loveessential");
        assertTrue(message.contains("loveessential"));
        assertTrue(message.contains("LoveEssential"));
    }
}
