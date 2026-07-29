package dev.lovelace.loveleaderboards;

import dev.lovelace.loveleaderboards.commands.LeaderboardAdminCommand;
import dev.lovelace.loveleaderboards.commands.LeaderboardCommand;
import dev.lovelace.loveleaderboards.integrations.LoveClansIntegration;
import dev.lovelace.loveleaderboards.integrations.LoveHuntIntegration;
import dev.lovelace.loveleaderboards.integrations.RatingSyncIntegration;
import dev.lovelace.loveleaderboards.integrations.PlaceholderAPIIntegration;
import dev.lovelace.loveleaderboards.listeners.GuiListener;
import dev.lovelace.loveleaderboards.listeners.LeaderboardEventListener;
import dev.lovelace.loveleaderboards.listeners.StandInteractListener;
import dev.lovelace.loveleaderboards.managers.CategoryManager;
import dev.lovelace.loveleaderboards.managers.DatabaseManager;
import dev.lovelace.loveleaderboards.managers.HallOfFameManager;
import dev.lovelace.loveleaderboards.managers.LeaderboardManager;
import dev.lovelace.loveleaderboards.managers.RewardsManager;
import dev.lovelace.loveleaderboards.managers.UpdateQueueManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;

public class LoveLeaderboards extends JavaPlugin {
    private DatabaseManager databaseManager;
    private CategoryManager categoryManager;
    private UpdateQueueManager updateQueueManager;
    private LeaderboardManager leaderboardManager;
    private RewardsManager rewardsManager;
    private HallOfFameManager hallOfFameManager;
    private LoveClansIntegration loveClansIntegration;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        // 1. Database
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

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

        // 6. Integrations
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIIntegration(this).register();
        }

        if (getConfig().getBoolean("integrations.love-hunt.enabled", true)) {
            new LoveHuntIntegration(this).register();
        }

        if (getConfig().getBoolean("integrations.love-clans.enabled", true)) {
            loveClansIntegration = new LoveClansIntegration(this);
            loveClansIntegration.startSyncTask();
        }

        // Рейтинг охотника и навык пивовара — накопленное состояние, а не событие,
        // поэтому их вычитываем периодически целиком.
        if (getConfig().getBoolean("integrations.rating-sync.enabled", true)) {
            new RatingSyncIntegration(this).startSyncTask();
        }

        // 7. Monthly Reset Task
        startMonthlyTask();

        getLogger().info("LoveLeaderboards loaded successfully!");
    }


    @Override
    public void onDisable() {
        if (updateQueueManager != null) {
            updateQueueManager.flushBatch();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("LoveLeaderboards disabled!");
    }

    private void startMonthlyTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (!getConfig().getBoolean("monthly-reset.enabled", true)) return;

            LocalDateTime now = LocalDateTime.now();
            int resetDay = getConfig().getInt("monthly-reset.day-of-month", 1);
            int resetHour = getConfig().getInt("monthly-reset.hour", 0);
            int resetMin = getConfig().getInt("monthly-reset.minute", 0);

            if (now.getDayOfMonth() == resetDay && now.getHour() == resetHour && now.getMinute() == resetMin) {
                databaseManager.resetMonthlyLeaderboards();
            }

            // Reward task execution logic (simplification)
            if (getConfig().getBoolean("rewards.enabled", true)) {
                int rewDay = getConfig().getInt("rewards.run-at.day-of-month", 1);
                int rewHour = getConfig().getInt("rewards.run-at.hour", 0);
                int rewMin = getConfig().getInt("rewards.run-at.minute", 5);

                if (now.getDayOfMonth() == rewDay && now.getHour() == rewHour && now.getMinute() == rewMin) {
                    getLogger().info("Triggering monthly rewards.");
                    rewardsManager.issueMonthlyRewards();
                }
            }

        }, 20 * 60L, 20 * 60L); // check every minute
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public CategoryManager getCategoryManager() {
        return categoryManager;
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

    public LoveClansIntegration getLoveClansIntegration() {
        return loveClansIntegration;
    }
}

