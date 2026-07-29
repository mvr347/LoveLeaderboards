package dev.lovelace.loveleaderboards.integrations;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.Category;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Клановые топы. Раньше интеграция обращалась к {@code dev.lovelace.loveclans.api.LoveClansAPI}
 * и методам getId/getName/getPower — ни такого пакета, ни таких методов в LoveClans нет.
 * Синхронизация падала в пустой catch, и клановые топы всегда оставались пустыми.
 *
 * Настоящий API лежит в {@code me.lovelace.loveclans.api.LoveClansAPI}, отдаётся статическим
 * getInstance(), а клан — record с методами id(), name(), influence() и chestMoney().
 * Обращаемся рефлексией: собираться против кланов LoveLeaderboards не должен.
 */
public class LoveClansIntegration {

    /** Категории с этим значением {@code integration} наполняются данными кланов. */
    private static final String INTEGRATION_NAME = "LoveClans";
    private static final String METRIC_INFLUENCE = "influence";
    private static final String METRIC_WEALTH = "wealth";

    private final LoveLeaderboards plugin;
    private boolean warnedUnavailable;

    public LoveClansIntegration(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    public void startSyncTask() {
        long interval = plugin.getConfig().getLong("integrations.love-clans.sync-interval", 3600) * 20L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::syncClans, interval, interval);
    }

    private void syncClans() {
        Object api = resolveApi();
        if (api == null) {
            return;
        }

        try {
            Collection<?> clans = (Collection<?>) api.getClass().getMethod("getAllClans").invoke(api);
            for (Object clan : clans) {
                String id = String.valueOf(invoke(clan, "id"));
                String name = String.valueOf(invoke(clan, "name"));

                for (Category category : plugin.getCategoryManager().getAllCategories()) {
                    if (!INTEGRATION_NAME.equalsIgnoreCase(category.integration())) {
                        continue;
                    }
                    plugin.getLeaderboardManager().updateClanScore(id, name, category.name(),
                            valueFor(clan, metricOf(category)));
                }
            }
        } catch (ReflectiveOperationException | ClassCastException exception) {
            plugin.getLogger().log(Level.WARNING,
                "Не удалось синхронизировать клановые топы — API LoveClans изменился?", exception);
        }
    }

    /**
     * Какую метрику клана берёт категория. Имя категории про деньги означает казну,
     * всё остальное — влияние: так новая категория заводится одним конфигом, без правки кода.
     */
    private String metricOf(Category category) {
        String name = category.name().toLowerCase(Locale.ROOT);
        return name.contains("wealth") || name.contains("money") || name.contains("bank")
                ? METRIC_WEALTH
                : METRIC_INFLUENCE;
    }

    private double valueFor(Object clan, String metric) throws ReflectiveOperationException {
        Object raw = METRIC_WEALTH.equals(metric) ? invoke(clan, "chestMoney") : invoke(clan, "influence");
        return raw instanceof Number number ? number.doubleValue() : 0.0;
    }

    private Object invoke(Object target, String method) throws ReflectiveOperationException {
        Method m = target.getClass().getMethod(method);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private Object resolveApi() {
        try {
            Class<?> apiClass = Class.forName("me.lovelace.loveclans.api.LoveClansAPI");
            return apiClass.getMethod("getInstance").invoke(null);
        } catch (ReflectiveOperationException exception) {
            if (!warnedUnavailable) {
                warnedUnavailable = true;
                plugin.getLogger().info("LoveClans не найден — клановые топы отключены.");
            }
            return null;
        }
    }

    public String getPlayerClanName(UUID playerUuid) {
        return playerClanField(playerUuid, "name");
    }

    public String getPlayerClanId(UUID playerUuid) {
        return playerClanField(playerUuid, "id");
    }

    /** Клан игрока приходит как Optional — разворачиваем его тоже рефлексией. */
    private String playerClanField(UUID playerUuid, String field) {
        Object api = resolveApi();
        if (api == null) {
            return null;
        }
        try {
            Object result = api.getClass().getMethod("getPlayerClan", UUID.class).invoke(api, playerUuid);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                return null;
            }
            return String.valueOf(invoke(optional.get(), field));
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
