package dev.lovelace.loveleaderboards;

import dev.lovelace.loveleaderboards.commands.LeaderboardAdminCommand;
import dev.lovelace.loveleaderboards.commands.LeaderboardCommand;
import dev.lovelace.loveleaderboards.integrations.PlaceholderAPIIntegration;
import dev.lovelace.loveleaderboards.integrations.StatBusIntegration;
import dev.lovelace.loveleaderboards.listeners.GuiListener;
import dev.lovelace.loveleaderboards.listeners.LeaderboardEventListener;
import dev.lovelace.loveleaderboards.listeners.StandInteractListener;
import dev.lovelace.loveleaderboards.managers.CategoryManager;
import dev.lovelace.loveleaderboards.managers.DatabaseManager;
import dev.lovelace.loveleaderboards.managers.HallOfFameManager;
import dev.lovelace.loveleaderboards.managers.HeadManager;
import dev.lovelace.loveleaderboards.managers.LeaderboardManager;
import dev.lovelace.loveleaderboards.managers.RewardsManager;
import dev.lovelace.loveleaderboards.managers.UpdateQueueManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;

public class LoveLeaderboards extends JavaPlugin {
    private DatabaseManager databaseManager;
    private CategoryManager categoryManager;
    private HeadManager headManager;
    private UpdateQueueManager updateQueueManager;
    private LeaderboardManager leaderboardManager;
    private RewardsManager rewardsManager;
    private HallOfFameManager hallOfFameManager;

    private String lastResetYearMonth = "";
    private String lastRewardsYearMonth = "";

    @Override
    public void onEnable() {

        saveDefaultConfig();

        // 1. Database
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        // 1.5 Heads Configuration Manager
        headManager = new HeadManager(this);
        headManager.loadHeads();

        // 2. Categories
        categoryManager = new CategoryManager(this);
        categoryManager.loadCategories();

        // 3. Update Queue
        long batchInterval = getConfig().getLong("performance.batch-update-interval", 2000) / 50; // ms to ticks
        int batchSize = getConfig().getInt("performance.batch-update-size", 1000);
        updateQueueManager = new UpdateQueueManager(this, databaseManager, batchInterval, batchSize);

        // 4. Leaderboard Manager
        long cacheTtl = getConfig().getLong("performance.cache-ttl", 30000);
        int cacheMax = getConfig().getInt("performance.cache-max-size", 256);
        leaderboardManager = new LeaderboardManager(this, databaseManager, categoryManager, updateQueueManager, cacheTtl, cacheMax);

        // 4.5 Rewards Manager
        rewardsManager = new RewardsManager(this);

        // 4.6 Hall of Fame Manager
        hallOfFameManager = new HallOfFameManager(this);
        hallOfFameManager.loadStands();

        // 5. Commands & Listeners
        LeaderboardCommand userCmd = new LeaderboardCommand(this);
        getCommand("leaderboard").setExecutor(userCmd);
        getCommand("leaderboard").setTabCompleter(userCmd);

        LeaderboardAdminCommand adminCmd = new LeaderboardAdminCommand(this);
        getCommand("leaderboardadmin").setExecutor(adminCmd);
        getCommand("leaderboardadmin").setTabCompleter(adminCmd);
        Bukkit.getPluginManager().registerEvents(new GuiListener(), this);
        Bukkit.getPluginManager().registerEvents(new LeaderboardEventListener(this), this);
        Bukkit.getPluginManager().registerEvents(new StandInteractListener(this), this);
        StandInteractListener.registerCitizensListener(this);

        // 6. Integrations
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIIntegration(this).register();
        }

        if (Bukkit.getPluginManager().getPlugin("LoveCore") != null) {
            try {
                StatBusIntegration statBusIntegration = new StatBusIntegration(this);
                Bukkit.getPluginManager().registerEvents(statBusIntegration, this);
                statBusIntegration.registerClanDisbandBridge(this);
                getLogger().info("LoveCore integration: слушаем StatChangedEvent.");
            } catch (Throwable t) {
                getLogger().warning("Не удалось подключиться к LoveCore: " + t.getMessage());
            }
        } else {
            getLogger().info("LoveCore не найден — топы по бонусам, рейтингу, пивоварению и кланам не будут обновляться.");
        }

        // 7. Monthly Reset Task
        startMonthlyTask();

        getLogger().info("LoveLeaderboards loaded successfully!");
    }


    @Override
    public void onDisable() {
        if (updateQueueManager != null) {
            updateQueueManager.flushAll();
        }
        if (hallOfFameManager != null) {
            hallOfFameManager.removeAllHolograms();
            hallOfFameManager.saveStands();
        }
        if (leaderboardManager != null) {
            leaderboardManager.invalidateAllCaches();
        }
        dev.lovelace.loveleaderboards.utils.ItemBuilder.clearCaches();
        dev.lovelace.loveleaderboards.gui.GuiNavigationManager.clearAllHistory();
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("LoveLeaderboards disabled!");
    }

    private void startMonthlyTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            LocalDateTime now = LocalDateTime.now();
            String currentYearMonth = String.format("%04d-%02d", now.getYear(), now.getMonthValue());

            // Monthly reset
            if (getConfig().getBoolean("monthly-reset.enabled", true)) {
                int resetDay = getConfig().getInt("monthly-reset.day-of-month", 1);
                int resetHour = getConfig().getInt("monthly-reset.hour", 0);
                int resetMin = getConfig().getInt("monthly-reset.minute", 0);

                if (now.getDayOfMonth() == resetDay && now.getHour() == resetHour && now.getMinute() == resetMin) {
                    if (!currentYearMonth.equals(lastResetYearMonth)) {
                        lastResetYearMonth = currentYearMonth;
                        getLogger().info("Executing scheduled monthly leaderboard reset for " + currentYearMonth);
                        databaseManager.resetMonthlyLeaderboards();
                        if (leaderboardManager != null) {
                            leaderboardManager.invalidateAllCaches();
                        }
                    }
                }
            }

            // Monthly rewards
            if (getConfig().getBoolean("rewards.enabled", true)) {
                int rewDay = getConfig().getInt("rewards.run-at.day-of-month", 1);
                int rewHour = getConfig().getInt("rewards.run-at.hour", 0);
                int rewMin = getConfig().getInt("rewards.run-at.minute", 5);

                if (now.getDayOfMonth() == rewDay && now.getHour() == rewHour && now.getMinute() == rewMin) {
                    if (!currentYearMonth.equals(lastRewardsYearMonth)) {
                        lastRewardsYearMonth = currentYearMonth;
                        getLogger().info("Triggering scheduled monthly rewards for " + currentYearMonth);
                        rewardsManager.issueMonthlyRewards();
                    }
                }
            }
        }, 20 * 60L, 20 * 60L); // Check every minute
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public CategoryManager getCategoryManager() {
        return categoryManager;
    }

    public HeadManager getHeadManager() {
        return headManager;
    }

    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public RewardsManager getRewardsManager() {
        return rewardsManager;
    }

    public HallOfFameManager getHallOfFameManager() {
        return hallOfFameManager;
    }

    /**
     * Клан игрока, для отображения в GUI (шапка клановых топов).
     */
    public String getPlayerClanName(java.util.UUID playerId) {
        if (playerId == null) return null;
        if (Bukkit.getPluginManager().isPluginEnabled("LoveClans")) {
            try {
                Class<?> apiClass = Class.forName("me.lovelace.loveclans.api.LoveClansAPI");
                Object api = apiClass.getMethod("getInstance").invoke(null);
                Object clanOpt = apiClass.getMethod("getPlayerClan", java.util.UUID.class).invoke(api, playerId);
                if (clanOpt instanceof java.util.Optional<?> opt && opt.isPresent()) {
                    Object clan = opt.get();
                    Object nameObj = clan.getClass().getMethod("name").invoke(clan);
                    if (nameObj != null) return nameObj.toString();
                }
            } catch (Throwable ignored) {}
        }
        if (Bukkit.getPluginManager().getPlugin("LoveCore") != null) {
            try {
                return dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.social.ProfileOracle.class)
                        .flatMap(oracle -> oracle.clanName(playerId))
                        .orElse(null);
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    public String getPlayerClanId(java.util.UUID playerId) {
        if (playerId == null) return null;
        if (Bukkit.getPluginManager().isPluginEnabled("LoveClans")) {
            try {
                Class<?> apiClass = Class.forName("me.lovelace.loveclans.api.LoveClansAPI");
                Object api = apiClass.getMethod("getInstance").invoke(null);
                Object clanOpt = apiClass.getMethod("getPlayerClan", java.util.UUID.class).invoke(api, playerId);
                if (clanOpt instanceof java.util.Optional<?> opt && opt.isPresent()) {
                    Object clan = opt.get();
                    Object idObj = clan.getClass().getMethod("id").invoke(clan);
                    if (idObj != null) return idObj.toString();
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
