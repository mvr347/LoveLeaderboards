package dev.lovelace.loveleaderboards.utils;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Reads and normalises the NPC reference stored for a stand in stands.yml.
 *
 * <p>Legacy format: {@code npc-id: <int>} (a Citizens id, {@code -1} meaning "no NPC").
 * Current format: {@code npc-key: <string>} plus {@code npc-provider: <provider id>}. A legacy numeric id is a
 * valid key for the {@code citizens} provider, so migration is lossless.</p>
 */
public final class NpcKeys {

    public static final String LEGACY_PROVIDER = "citizens";

    /** @param key null when the stand has no NPC; @param migrated true when the value came from the legacy {@code npc-id} */
    public record Stored(String key, String provider, boolean migrated) {
        public static final Stored NONE = new Stored(null, null, false);

        public boolean present() {
            return key != null;
        }
    }

    private NpcKeys() {}

    /** Normalises a raw key; blank and the legacy "-1" sentinel mean "no NPC". Citizens id 0 is a real id. */
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
            String provider = stand.getString("npc-provider", LEGACY_PROVIDER);
            if (provider == null || provider.isBlank()) {
                provider = LEGACY_PROVIDER;
            }
            return new Stored(key, provider.trim().toLowerCase(java.util.Locale.ROOT), false);
        }
        String legacy = normalize(stand.get("npc-id"));
        if (legacy != null) {
            return new Stored(legacy, LEGACY_PROVIDER, true);
        }
        return Stored.NONE;
    }
}
