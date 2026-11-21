package dev.lovelace.loveleaderboards.utils;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Reads and normalises the NPC reference stored for a stand in stands.yml.
 *
 * <p>Legacy format: {@code npc-id: <int>} (a numeric id, {@code -1} meaning "no NPC").
 * Current format: {@code npc-key: <string>} plus {@code npc-provider: <provider id>}.
 * 2026-11-21: the only NPC provider is LoveEssential, so the stored provider is always read as
 * {@code loveessential}; LoveEssential resolves a numeric key as the NPC's visible number, so migration is
 * lossless.</p>
 */
public final class NpcKeys {

    /** The only supported NPC provider. */
    public static final String PROVIDER = dev.lovelace.lovecore.api.provider.Providers.LOVE_ESSENTIAL;

    /** @param key null when the stand has no NPC; @param migrated true when the value came from the legacy {@code npc-id} */
    public record Stored(String key, String provider, boolean migrated) {
        public static final Stored NONE = new Stored(null, null, false);

        public boolean present() {
            return key != null;
        }
    }

    private NpcKeys() {}

    /** Normalises a raw key; blank and the legacy "-1" sentinel mean "no NPC". Id 0 is a real id. */
    public static String normalize(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.toString().trim();
        if (text.isEmpty() || text.equals("-1")) {
            return null;
        }
        return text;
    }

    public static Stored read(ConfigurationSection stand) {
        String key = normalize(stand.get("npc-key"));
        if (key != null) {
            // A stored npc-provider (for example the old "citizens") is ignored: there is one provider now.
            return new Stored(key, PROVIDER, false);
        }
        String legacy = normalize(stand.get("npc-id"));
        if (legacy != null) {
            return new Stored(legacy, PROVIDER, true);
        }
        return Stored.NONE;
    }
}
