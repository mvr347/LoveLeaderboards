package dev.lovelace.loveleaderboards.managers;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.Category;
import dev.lovelace.loveleaderboards.models.LeaderboardEntry;
import dev.lovelace.loveleaderboards.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import dev.lovelace.loveleaderboards.models.TimePeriod;

import java.time.YearMonth;
import java.util.List;

public class RewardsManager {
    private final LoveLeaderboards plugin;

    public RewardsManager(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    /**
     * Pays rewards for the previous (finished) month, at most once per month. Must run off the main thread.
     *
     * @return true if this call paid out, false if disabled or the month was already paid
     */
    public boolean issueMonthlyRewards() {
        if (!plugin.getConfig().getBoolean("rewards.enabled", true)) return false;

        ConfigurationSection categoriesSec = plugin.getConfig().getConfigurationSection("rewards.categories");
        if (categoriesSec == null) return false;

        String monthlyKey = TimePeriod.previousMonthKey(YearMonth.now());
        // Claim first: the marker row is the idempotency guard across restarts and double triggers.
        if (!plugin.getDatabaseManager().claimMonthRewards(monthlyKey)) return false;
        plugin.getDatabaseManager().purgePendingRewards(plugin.getConfig().getInt("rewards.pending-keep-days", 60));

        for (String category : categoriesSec.getKeys(false)) {
            int maxRank = 50; 

            Category catObj = plugin.getCategoryManager().getCategory(category).orElse(null);
            String entityType = catObj != null ? catObj.getEntityType() : "player";
            
            List<LeaderboardEntry> top = plugin.getLeaderboardManager().getTop(category, entityType, monthlyKey, maxRank);
            if (top.isEmpty() || (top.size() == 1 && top.get(0).entityName().equals("Свободное место"))) continue;

            ConfigurationSection ranksSec = categoriesSec.getConfigurationSection(category);
            if (ranksSec == null) continue;

            for (LeaderboardEntry entry : top) {
                if (entry.entityId().equals("empty") || entry.entityName().equals("Свободное место")) continue;

                int rank = entry.rank();
                
                // Find matching rank group
                for (String key : ranksSec.getKeys(false)) {
                    if (key.startsWith("rank-")) {
                        String rangeStr = key.substring(5);
                        String[] bounds = rangeStr.split("-");
                        int min;
                        int max;
                        try {
                            min = Integer.parseInt(bounds[0]);
                            max = bounds.length > 1 ? Integer.parseInt(bounds[1]) : min;
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Invalid rewards rank range key '" + key + "' under rewards.categories."
                                    + category + " — skipping.");
                            continue;
                        }

                        if (rank >= min && rank <= max) {
                            List<String> commands = ranksSec.getStringList(key + ".commands");
                            
                            boolean online = isOnline(entry.entityId());
                            if (RewardCommands.shouldDefer(entityType, online)) {
                                // Offline winner: the commands need the player online, so they wait for the next join.
                                plugin.getDatabaseManager().addPendingReward(entry.entityId(), monthlyKey, category,
                                        entry.score(), commands);
                            } else {
                                runCommands(commands, entry.entityName(), entry.score(), category, rank);
                            }
                            break;
                        }
                    }
                }
            }
        }

        // Broadcast notification
        String message = plugin.getConfig().getString("messages.rewards-given");
        if (message != null) message = message.replace("%month%", monthlyKey);
        final String finalMessage = message;
        if (message != null && !message.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getServer().sendMessage(TextUtil.parse(finalMessage));
            });
        }
        return true;
    }

    private static boolean isOnline(String entityId) {
        try {
            return Bukkit.getPlayer(java.util.UUID.fromString(entityId)) != null;
        } catch (IllegalArgumentException notAUuid) {
            return false;
        }
    }

    /** Runs the commands on the main thread; a failing command is logged and never stops the ones after it. */
    private void runCommands(List<String> commands, String name, double score, String category, int rank) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String cmd : commands) {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), RewardCommands.render(cmd, name, score));
                } catch (Exception e) {
                    plugin.getLogger().warning("Reward command failed (" + category + ", rank " + rank + "): '"
                            + cmd + "' - " + e.getMessage());
                }
            }
        });
    }

    /** Delivers what was held back while the player was offline. Safe to call from any thread. */
    public void deliverPending(org.bukkit.entity.Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<dev.lovelace.loveleaderboards.managers.DatabaseManager.PendingReward> pending =
                    plugin.getDatabaseManager().takePendingRewards(player.getUniqueId().toString());
            for (var reward : pending) {
                runCommands(reward.commands(), player.getName(), reward.score(), reward.category(), 0);
            }
            if (!pending.isEmpty()) {
                plugin.getLogger().info("Delivered " + pending.size() + " pending reward(s) to " + player.getName());
            }
        });
    }

    /** Warns once about reward commands that can never work (see {@link RewardCommands#usesLegacyCodesInComponentCommand}). */
    public void warnAboutBrokenCommands() {
        ConfigurationSection categoriesSec = plugin.getConfig().getConfigurationSection("rewards.categories");
        if (categoriesSec == null) return;
        for (String key : categoriesSec.getKeys(true)) {
            if (!key.endsWith(".commands")) continue;
            for (String cmd : categoriesSec.getStringList(key)) {
                if (RewardCommands.usesLegacyCodesInComponentCommand(cmd)) {
                    plugin.getLogger().warning("rewards.categories." + key + ": '" + cmd
                            + "' uses '&' colour codes, but /title, /subtitle, /actionbar and /tellraw need a JSON text component.");
                }
            }
        }
    }
}
