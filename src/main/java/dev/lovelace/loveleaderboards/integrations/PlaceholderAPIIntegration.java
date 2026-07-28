package dev.lovelace.loveleaderboards.integrations;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.LeaderboardEntry;
import dev.lovelace.loveleaderboards.models.PlayerStats;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class PlaceholderAPIIntegration extends PlaceholderExpansion {
    private final LoveLeaderboards plugin;

    public PlaceholderAPIIntegration(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "loveleaderboards";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Antigravity";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; 
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // Format 1: %loveleaderboards_alltime_top_<category>_<position>_<field>%
        // Format 2: %loveleaderboards_alltime_top_<position>_<field>% (Fallback to first category)
        
        String[] args = params.split("_");
        
        if (args.length >= 3 && (args[0].equals("alltime") || args[0].equals("monthly")) && args[1].equals("top")) {
            String timePeriod = args[0].equals("alltime") ? "alltime" : getCurrentMonthString();
            
            String category;
            int position;
            int fieldIndex;
            
            // Try parsing args[2] as position (Format 2)
            try {
                position = Integer.parseInt(args[2]);
                category = plugin.getCategoryManager().getAllCategories().stream().findFirst().map(dev.lovelace.loveleaderboards.models.Category::name).orElse("kills");
                fieldIndex = 3;
            } catch (NumberFormatException e) {
                // If it fails, args[2] is the category (Format 1)
                category = args[2];
                try {
                    position = Integer.parseInt(args[3]);
                    fieldIndex = 4;
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
                    return null; // Invalid position
                }
            }
            
            String field = args.length > fieldIndex ? args[fieldIndex] : "name";

            Optional<dev.lovelace.loveleaderboards.models.Category> catOpt = plugin.getCategoryManager().getCategory(category);
            String entityType = catOpt.map(dev.lovelace.loveleaderboards.models.Category::getEntityType).orElse("player");

            List<LeaderboardEntry> top = plugin.getLeaderboardManager().getTop(category, entityType, timePeriod, position);

            if (top.size() >= position) {
                LeaderboardEntry entry = top.get(position - 1);
                return switch (field.toLowerCase()) {
                    case "name" -> entry.entityName();
                    case "score" -> String.valueOf(entry.score());
                    case "rank" -> String.valueOf(entry.rank());
                    default -> "";
                };
            }
            return "---";
        }
        
        if (player != null && args.length >= 1) {
            String defaultCategory = plugin.getCategoryManager().getAllCategories().stream().findFirst().map(dev.lovelace.loveleaderboards.models.Category::name).orElse("kills");
            if (args[0].equals("myrank")) {
                String category = args.length >= 2 ? args[1] : defaultCategory;
                Optional<PlayerStats> stats = plugin.getLeaderboardManager().getPlayerStats(player.getUniqueId(), category);
                return stats.map(s -> String.valueOf(s.rank())).orElse("Нет");
            } else if (args[0].equals("myscore")) {
                String category = args.length >= 2 ? args[1] : defaultCategory;
                Optional<PlayerStats> stats = plugin.getLeaderboardManager().getPlayerStats(player.getUniqueId(), category);
                return stats.map(s -> String.valueOf(s.score())).orElse("0");
            }
        }

        return null;
    }

    private String getCurrentMonthString() {
        LocalDate now = LocalDate.now();
        return String.format("%04d-%02d", now.getYear(), now.getMonthValue());
    }
}
