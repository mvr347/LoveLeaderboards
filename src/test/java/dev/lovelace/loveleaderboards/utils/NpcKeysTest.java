package dev.lovelace.loveleaderboards.utils;

import dev.lovelace.loveleaderboards.models.LeaderboardStand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcKeysTest {

    private static ConfigurationSection stand(String yaml) throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString("s:\n" + yaml.indent(2));
        return cfg.getConfigurationSection("s");
    }

    @Test
    void legacyNumericIdBecomesCitizensKey() throws Exception {
        NpcKeys.Stored stored = NpcKeys.read(stand("npc-id: 42"));
        assertEquals("42", stored.key());
        assertEquals("citizens", stored.provider());
        assertTrue(stored.migrated());
    }

    @Test
    void citizensIdZeroIsARealId() throws Exception {
        NpcKeys.Stored stored = NpcKeys.read(stand("npc-id: 0"));
        assertTrue(stored.present());
        assertEquals("0", stored.key());
    }

    @Test
    void legacyMinusOneMeansNoNpc() throws Exception {
        NpcKeys.Stored stored = NpcKeys.read(stand("npc-id: -1"));
        assertFalse(stored.present());
        assertFalse(stored.migrated());
    }

    @Test
    void missingNpcFieldsMeanNoNpc() throws Exception {
        assertFalse(NpcKeys.read(stand("category: kills")).present());
    }

    @Test
    void newFormatKeepsStringKeyAndProvider() throws Exception {
        NpcKeys.Stored stored = NpcKeys.read(stand("npc-key: ext-1a2b3c4d\nnpc-provider: LoveEssential"));
        assertEquals("ext-1a2b3c4d", stored.key());
        assertEquals("loveessential", stored.provider());
        assertFalse(stored.migrated());
    }

    @Test
    void newKeyWinsOverLeftoverLegacyId() throws Exception {
        NpcKeys.Stored stored = NpcKeys.read(stand("npc-id: 7\nnpc-key: \"9\"\nnpc-provider: citizens"));
        assertEquals("9", stored.key());
        assertFalse(stored.migrated());
    }

    @Test
    void newKeyWithoutProviderDefaultsToCitizens() throws Exception {
        assertEquals("citizens", NpcKeys.read(stand("npc-key: \"5\"")).provider());
    }

    @Test
    void numericYamlKeyIsReadAsText() throws Exception {
        // an unquoted number in npc-key must not be lost or turned into "5.0"
        assertEquals("5", NpcKeys.read(stand("npc-key: 5")).key());
    }

    @Test
    void standDropsProviderWhenKeyIsNull() {
        LeaderboardStand stand = new LeaderboardStand("a", null, "kills", 1, "all", null, null, "citizens", null);
        assertFalse(stand.hasNpc());
        assertNull(stand.getNpcProvider());
        stand.setNpc("12", "citizens");
        assertTrue(stand.hasNpc());
        stand.setNpc(null, "citizens");
        assertNull(stand.getNpcProvider());
    }
}
