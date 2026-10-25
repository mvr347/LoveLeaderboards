package dev.lovelace.loveleaderboards;

import dev.lovelace.loveleaderboards.commands.LeaderboardAdminRedirectCommand;
import dev.lovelace.loveleaderboards.commands.LeaderboardCommand;
import dev.lovelace.loveleaderboards.commands.LoveLeaderboardsAdminCommand;
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
        rewardsManager.warnAboutBrokenCommands();

        // 4.6 Hall of Fame Manager
        hallOfFameManager = new HallOfFameManager(this);
        hallOfFameManager.loadStands();

        // 5. Commands & Listeners
        LeaderboardCommand userCmd = new LeaderboardCommand(this);
        getCommand("leaderboard").setExecutor(userCmd);
        getCommand("leaderboard").setTabCompleter(userCmd);

        LoveLeaderboardsAdminCommand adminCmd = new LoveLeaderboardsAdminCommand(this);
        getCommand("loveleaderboardsadmin").setExecutor(adminCmd);
        getCommand("loveleaderboardsadmin").setTabCompleter(adminCmd);

        // Старое имя команды оставлено зарегистрированным, но лишь перенаправляет
        // на /loveleaderboardsadmin — чтобы не пропадать без объяснений для тех,
        // кто наберёт его по привычке.
        getCommand("leaderboardadmin").setExecutor(new LeaderboardAdminRedirectCommand(this));

        Bukkit.getPluginManager().registerEvents(new GuiListener(), this);
        Bukkit.getPluginManager().registerEvents(new LeaderboardEventListener(this), this);
        Bukkit.getPluginManager().registerEvents(new StandInteractListener(this), this);
        if (Bukkit.getPluginManager().getPlugin("LoveCore") != null) {
            try {
                Bukkit.getPluginManager().registerEvents(new dev.lovelace.loveleaderboards.listeners.StandNpcClickListener(this), this);
            } catch (Throwable t) {
                getLogger().warning("Не удалось подписаться на клики по NPC (LoveCore): " + t.getMessage());
            }
        }

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

        boolean vesuvioEnabled = getConfig().getBoolean("integrations.vesuvio.enabled", true);
        dev.lovelace.loveleaderboards.integrations.VesuvioIntegration.setEnabled(vesuvioEnabled);
        if (dev.lovelace.loveleaderboards.integrations.VesuvioIntegration.isAvailable()) {
            getLogger().info("Vesuvio integration: фильтрация читеров и подозреваемых из лидербордов активна.");
        }

        // One periodic async refresh of hot top lists (replaces cache wipe on every batch flush)
        long refreshTicks = Math.max(20L, Math.min(cacheTtl / 2, 10_000L) / 50L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> leaderboardManager.refreshHotTops(), refreshTicks, refreshTicks);

        // Rows of clans that vanished without a disband event are removed here, at start and on a timer.
        long sweepTicks = Math.max(1L, getConfig().getLong("cleanup.interval-minutes", 5L)) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> leaderboardManager.sweepOrphanClans(), 100L, sweepTicks);

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
            hallOfFameManager.saveStands(false);
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

                // ">=" instead of "==": a server that was down at the exact minute catches up later,
                // and the DB marker row guarantees the previous month is paid only once.
                java.time.LocalDateTime due = now.withDayOfMonth(Math.min(rewDay, now.toLocalDate().lengthOfMonth()))
                        .withHour(rewHour).withMinute(rewMin).withSecond(0).withNano(0);
                if (!now.isBefore(due) && !currentYearMonth.equals(lastRewardsYearMonth)) {
                    lastRewardsYearMonth = currentYearMonth;
                    if (rewardsManager.issueMonthlyRewards()) {
                        getLogger().info("Issued monthly rewards for the month before " + currentYearMonth);
                    }
                }
            }
        }, 20 * 30L, 20 * 60L); // First check 30s after startup (reward catch-up), then every minute
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

    public dev.lovelace.loveleaderboards.integrations.StandNpcs getStandNpcs() {
        return hallOfFameManager.getStandNpcs();
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
