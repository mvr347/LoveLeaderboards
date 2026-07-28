package dev.lovelace.loveleaderboards.managers;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.LeaderboardEntry;
import dev.lovelace.loveleaderboards.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDate;
import java.util.List;

public class RewardsManager {
    private final LoveLeaderboards plugin;

    public RewardsManager(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    public void issueMonthlyRewards() {
        if (!plugin.getConfig().getBoolean("rewards.enabled", true)) return;

        String lastMonth = getLastMonthString();
        ConfigurationSection categoriesSec = plugin.getConfig().getConfigurationSection("rewards.categories");
        if (categoriesSec == null) return;

        for (String category : categoriesSec.getKeys(false)) {
            int maxRank = 50; 

            dev.lovelace.loveleaderboards.models.Category catObj = plugin.getCategoryManager().getCategory(category).orElse(null);
            String entityType = catObj != null ? catObj.getEntityType() : "player";
            
            List<LeaderboardEntry> top = plugin.getLeaderboardManager().getTop(category, entityType, lastMonth, maxRank);
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
                        int min = Integer.parseInt(bounds[0]);
                        int max = bounds.length > 1 ? Integer.parseInt(bounds[1]) : min;

                        if (rank >= min && rank <= max) {
                            List<String> commands = ranksSec.getStringList(key + ".commands");
                            
                            // Execute commands synchronously
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                for (String cmd : commands) {
                                    String finalCmd = cmd.replace("%player%", entry.entityName())
                                                         .replace("%clan%", entry.entityName())
                                                         .replace("%clan_name%", entry.entityName())
                                                         .replace("%score%", String.valueOf(entry.score()));
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                                }
                            });
                            break;
                        }
                    }
                }
            }
        }

        
        // Notify players
        String message = plugin.getConfig().getString("messages.rewards-given");
        if (message != null && !message.isEmpty()) {
            Bukkit.getServer().sendMessage(TextUtil.parse(message.replace("%month%", lastMonth)));
        }
    }

    private String getLastMonthString() {
        LocalDate last = LocalDate.now().minusMonths(1);
        return String.format("%04d-%02d", last.getYear(), last.getMonthValue());
    }
}
