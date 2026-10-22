package dev.lovelace.loveleaderboards.integrations;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.LeaderboardStand;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Stand NPCs through LoveCore's {@code NpcProvider} contract (Citizens or LoveEssential, chosen by
 * {@code integrations.npc-provider}). Every LoveCore class reference lives in {@link Bridge}, so a missing
 * LoveCore only ever shows up as a caught error and a single warning: the stand then just has no NPC.
 *
 * <p>The provider is resolved on every call and never cached, because providers register late.</p>
 */
public final class StandNpcs {

    /** Tag put on every NPC this plugin creates; the value is the stand id. */
    public static final String TAG_STAND = "loveleaderboards.stand";

    private final LoveLeaderboards plugin;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public StandNpcs(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    /** Currently configured provider id (re-read from config each time so /reload takes effect). */
    public String configuredProvider() {
        String raw = plugin.getConfig().getString("integrations.npc-provider", NpcProviderChoice.DEFAULT);
        NpcProviderChoice.Result result = NpcProviderChoice.parse(raw);
        if (!result.recognized()) {
            warnOnce("bad-choice:" + raw, "integrations.npc-provider: неизвестное значение '" + raw
                    + "' (допустимо citizens|loveessential), используется " + result.id() + ".");
        }
        return result.id();
    }

    /** Creates the NPC for a new stand through the configured provider; returns its key or null if unavailable. */
    public String create(LeaderboardStand stand, Component name, String skinName) {
        String providerId = configuredProvider();
        try {
            if (!loveCorePresent()) {
                return null;
            }
            String key = Bridge.create(providerId, stand.getId(), stand.getLocation(), name, skinName);
            if (key == null) {
                warnOnce("missing:" + providerId, missingProviderMessage(providerId));
            }
            return key;
        } catch (NoClassDefFoundError missingApi) {
            warnOnce("no-api", "LoveCore API недоступен — NPC стендов отключены.");
            return null;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Не удалось создать NPC стенда '" + stand.getId() + "' через "
                    + providerId + ": " + t.getMessage());
            return null;
        }
    }

    /** Updates name and (if {@code skinName != null}) skin of the stand's NPC. No-op when it is not manageable. */
    public void apply(LeaderboardStand stand, Component name, String skinName) {
        if (!manageable(stand)) {
            return;
        }
        try {
            Bridge.apply(stand.getNpcProvider(), stand.getNpcKey(), stand.getId(), name, skinName);
        } catch (NoClassDefFoundError missingApi) {
            warnOnce("no-api", "LoveCore API недоступен — NPC стендов отключены.");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Не удалось обновить NPC стенда '" + stand.getId() + "': " + t.getMessage());
        }
    }

    /** Removes the stand's NPC through the provider that created it. Best effort. */
    public void remove(LeaderboardStand stand) {
        if (!stand.hasNpc() || !loveCorePresent()) {
            return;
        }
        try {
            if (!Bridge.remove(stand.getNpcProvider(), stand.getNpcKey())) {
                plugin.getLogger().fine("NPC " + stand.getNpcProvider() + ":" + stand.getNpcKey()
                        + " стенда '" + stand.getId() + "' не найден — удалять нечего.");
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Failed to remove NPC for stand " + stand.getId(), t);
        }
    }

    /** True if the stand has an NPC and it belongs to the configured provider (others are ignored on purpose). */
    private boolean manageable(LeaderboardStand stand) {
        if (!stand.hasNpc()) {
            return false;
        }
        String configured = configuredProvider();
        if (!configured.equals(stand.getNpcProvider())) {
            warnOnce("mismatch:" + stand.getId() + ":" + configured, "Стенд '" + stand.getId() + "' создан через "
                    + stand.getNpcProvider() + ", а в config.yml выбран " + configured
                    + " — NPC этого стенда не обновляется. Пересоздайте стенд или верните прежний провайдер.");
            return false;
        }
        if (!loveCorePresent()) {
            return false;
        }
        return true;
    }

    private boolean loveCorePresent() {
        if (Bukkit.getPluginManager().getPlugin("LoveCore") == null) {
            warnOnce("no-lovecore", "LoveCore не найден — NPC стендов отключены (стенды работают без NPC).");
            return false;
        }
        return true;
    }

    static String missingProviderMessage(String providerId) {
        return "NPC-провайдер '" + providerId + "' не зарегистрирован в LoveCore (не установлен "
                + (NpcProviderChoice.CITIZENS.equals(providerId) ? "Citizens" : "LoveEssential")
                + "?) — NPC стендов отключены.";
    }

    private void warnOnce(String key, String message) {
        if (warned.add(key)) {
            plugin.getLogger().warning(message);
        }
    }

    /** Isolates every LoveCore API reference; only ever loaded inside the try/catch blocks above. */
    static final class Bridge {
        private Bridge() {}

        static Optional<dev.lovelace.lovecore.api.npc.NpcProvider> provider(String id) {
            return dev.lovelace.lovecore.api.LoveCore.provider(dev.lovelace.lovecore.api.npc.NpcProvider.class, id);
        }

        static String create(String providerId, String standId, Location location, Component name, String skinName) {
            Optional<dev.lovelace.lovecore.api.npc.NpcProvider> provider = provider(providerId);
            if (provider.isEmpty()) {
                return null;
            }
            dev.lovelace.lovecore.api.npc.NpcSpec spec = dev.lovelace.lovecore.api.npc.NpcSpec.at(name, location)
                    .withType(org.bukkit.entity.EntityType.PLAYER)
                    .withTags(java.util.Map.of(TAG_STAND, standId));
            if (skinName != null) {
                spec = spec.withSkinName(skinName);
            }
            return provider.get().create(spec).key();
        }

        static void apply(String providerId, String key, String standId, Component name, String skinName) {
            Optional<dev.lovelace.lovecore.api.npc.NpcProvider> provider = provider(providerId);
            if (provider.isEmpty()) {
                return;
            }
            Optional<dev.lovelace.lovecore.api.npc.NpcHandle> handle = provider.get().byKey(key);
            if (handle.isEmpty() || !handle.get().spawned()) {
                return;
            }
            dev.lovelace.lovecore.api.npc.NpcHandle npc = handle.get();
            // NPCs created before tags existed get marked on first update, so clicks can be matched by tag from now on.
            if (npc.tag(TAG_STAND).isEmpty()) {
                npc.tag(TAG_STAND, standId);
            }
            if (skinName != null) {
                npc.setSkin(skinName);
            }
            npc.setName(name);
        }

        static boolean remove(String providerId, String key) {
            Optional<dev.lovelace.lovecore.api.npc.NpcProvider> provider = provider(providerId);
            return provider.isPresent() && provider.get().remove(key);
        }
    }
}
