package dev.lovelace.loveleaderboards.integrations;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.Category;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Топы, которые нельзя построить по событию: рейтинг охотника и навык пивовара —
 * это накопленное состояние игрока, а не разовое действие, поэтому их приходится
 * периодически вычитывать целиком.
 *
 * Оба плагина отдают свои значения через ServicesManager, обращение — рефлексией,
 * чтобы LoveLeaderboards не собирался против них.
 */
public class RatingSyncIntegration {

    private static final String HUNT_INTEGRATION = "LoveHuntRating";
    private static final String BREW_INTEGRATION = "LoveBrew";

    private final LoveLeaderboards plugin;
    private boolean warnedHunt;
    private boolean warnedBrew;

    public RatingSyncIntegration(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    public void startSyncTask() {
        long interval = plugin.getConfig().getLong("integrations.rating-sync.sync-interval", 900) * 20L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            syncHunterRatings();
            syncBrewerSkills();
        }, interval, interval);
    }

    private void syncHunterRatings() {
        if (!hasCategory(HUNT_INTEGRATION)) {
            return;
        }
        Object api = service("me.lovelace.loveHunt.api.LoveHuntAPI");
        if (api == null) {
            if (!warnedHunt) {
                warnedHunt = true;
                plugin.getLogger().info("LoveHunt не найден — топ по рейтингу охотников отключён.");
            }
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<UUID, Double> ratings = (Map<UUID, Double>) api.getClass()
                    .getMethod("getHunterRatings").invoke(api);
            push(HUNT_INTEGRATION, ratings);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            plugin.getLogger().log(Level.WARNING, "Не удалось получить рейтинги охотников", exception);
        }
    }

    private void syncBrewerSkills() {
        if (!hasCategory(BREW_INTEGRATION)) {
            return;
        }
        Object api = service("dev.lovelace.lovebrew.api.BreweryAPI");
        if (api == null) {
            if (!warnedBrew) {
                warnedBrew = true;
                plugin.getLogger().info("LoveBrew не найден — топ пивоваров отключён.");
            }
            return;
        }
        try {
            Object future = api.getClass().getMethod("getBrewerSkillsAsync").invoke(api);
            if (!(future instanceof CompletableFuture<?> completable)) {
                return;
            }
            completable.thenAccept(result -> {
                if (result instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<UUID, ? extends Number> skills = (Map<UUID, ? extends Number>) map;
                    push(BREW_INTEGRATION, skills);
                }
            });
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "Не удалось получить навыки пивоваров", exception);
        }
    }

    /** Раскладывает значения по всем категориям с этой интеграцией. */
    private void push(String integration, Map<UUID, ? extends Number> values) {
        for (Category category : plugin.getCategoryManager().getAllCategories()) {
            if (!integration.equalsIgnoreCase(category.integration())) {
                continue;
            }
            values.forEach((uuid, value) -> {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                String name = player.getName() == null ? uuid.toString() : player.getName();
                plugin.getLeaderboardManager().setPlayerScore(uuid, name, category.name(), value.doubleValue());
            });
        }
    }

    private boolean hasCategory(String integration) {
        return plugin.getCategoryManager().getAllCategories().stream()
                .anyMatch(category -> integration.equalsIgnoreCase(category.integration()));
    }

    private Object service(String className) {
        try {
            Class<?> apiClass = Class.forName(className);
            return Bukkit.getServicesManager().load(apiClass);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }
}
