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
                            
                            // Execute reward commands on main server thread
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                for (String cmd : commands) {
                                    String finalCmd = cmd.replace("%player%", entry.entityName())
                                                         .replace("%clan%", entry.entityName())
                                                         .replace("%clan_name%", entry.entityName())
                                                         .replace("%score%", String.valueOf((long) entry.score()));
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                                }
                            });
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
}
