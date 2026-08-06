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
        // Format 1: %loveleaderboards_<period>_top_<category>_<position>_<field>%
        // Format 2: %loveleaderboards_<period>_top_<position>_<field>% (Fallback to first category)
        
        String[] args = params.split("_");
        
        if (args.length >= 3 && args[1].equals("top")) {
            dev.lovelace.loveleaderboards.models.TimePeriod periodEnum = dev.lovelace.loveleaderboards.models.TimePeriod.fromString(args[0]);
            String timePeriod = periodEnum.getDbKey();
            
            String category;
            int position;
            int fieldIndex;
            
            // Try parsing args[2] as position (Format 2)
            try {
                position = Integer.parseInt(args[2]);
                category = plugin.getCategoryManager().getAllCategories().stream()
                        .filter(dev.lovelace.loveleaderboards.models.Category::enabled)
                        .sorted(java.util.Comparator.comparingInt(dev.lovelace.loveleaderboards.models.Category::sortOrder))
                        .findFirst()
                        .map(dev.lovelace.loveleaderboards.models.Category::name)
                        .orElse("kills");
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
                if (entry.entityId().equals("empty")) {
                    return "---";
                }
                return switch (field.toLowerCase()) {
                    case "name" -> entry.entityName();
                    case "score" -> String.valueOf((long) entry.score());
                    case "score_formatted", "score_double" -> String.valueOf(entry.score());
                    case "rank" -> String.valueOf(entry.rank());
                    default -> "";
                };
            }
            return "---";
        }
        
        if (player != null && args.length >= 1) {
            String defaultCategory = plugin.getCategoryManager().getAllCategories().stream()
                    .filter(dev.lovelace.loveleaderboards.models.Category::enabled)
                    .sorted(java.util.Comparator.comparingInt(dev.lovelace.loveleaderboards.models.Category::sortOrder))
                    .findFirst()
                    .map(dev.lovelace.loveleaderboards.models.Category::name)
                    .orElse("kills");

            String sub = args[0].toLowerCase();
            if (sub.equals("myrank") || sub.equals("myscore") || sub.equals("myclanrank") || sub.equals("myclanscore")) {
                String category = args.length >= 2 ? args[1] : defaultCategory;
                Optional<dev.lovelace.loveleaderboards.models.Category> catOpt = plugin.getCategoryManager().getCategory(category);
                boolean isClan = sub.contains("clan") || (catOpt.isPresent() && catOpt.get().isClanCategory());

                Optional<PlayerStats> stats;
                if (isClan) {
                    String clanId = plugin.getPlayerClanId(player.getUniqueId());
                    String clanName = plugin.getPlayerClanName(player.getUniqueId());
                    String searchId = clanId != null ? clanId : clanName;
                    stats = searchId != null ? plugin.getLeaderboardManager().getEntityStats("clan", searchId, category, dev.lovelace.loveleaderboards.models.TimePeriod.ALL_TIME.getDbKey()) : Optional.empty();
                } else {
                    stats = plugin.getLeaderboardManager().getPlayerStats(player.getUniqueId(), category);
                }

                if (sub.endsWith("rank")) {
                    return stats.map(s -> String.valueOf(s.rank())).orElse("Нет");
                } else {
                    return stats.map(s -> String.valueOf((long) s.score())).orElse("0");
                }
            }
        }

        return null;
    }
}
