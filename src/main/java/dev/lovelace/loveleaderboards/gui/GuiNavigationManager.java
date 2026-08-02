package dev.lovelace.loveleaderboards.gui;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.TimePeriod;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GuiNavigationManager {
    private static final Map<UUID, Deque<GuiState>> historyMap = new HashMap<>();

    public record GuiState(
        GuiType type,
        String category,
        TimePeriod period,
        String entityType,
        OfflinePlayer targetPlayer,
        int page
    ) {
        public enum GuiType {
            MAIN_LEADERBOARD,
            PLAYER_STATS,
            PLAYER_COMPARISON,
            CATEGORY_SELECT
        }

        public void open(LoveLeaderboards plugin, Player viewer) {
            switch (type) {
                case MAIN_LEADERBOARD -> viewer.openInventory(new LeaderboardMainGui(plugin, viewer, category, period, entityType, page).getInventory());
                case PLAYER_STATS -> viewer.openInventory(new PlayerStatsGui(plugin, viewer, targetPlayer != null ? targetPlayer : viewer, category, period).getInventory());
                case PLAYER_COMPARISON -> {
                    if (targetPlayer != null) {
                        viewer.openInventory(new PlayerComparisonGui(plugin, viewer, targetPlayer, category, period).getInventory());
                    } else {
                        viewer.openInventory(new LeaderboardMainGui(plugin, viewer, category, period, entityType, 1).getInventory());
                    }
                }
                case CATEGORY_SELECT -> viewer.openInventory(new CategorySelectGui(plugin, viewer, period, entityType, targetPlayer).getInventory());
            }
        }
    }

    public static void pushState(Player player, GuiState state) {
        historyMap.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>()).push(state);
    }

    public static boolean hasHistory(Player player) {
        Deque<GuiState> history = historyMap.get(player.getUniqueId());
        return history != null && !history.isEmpty();
    }

    public static boolean goBack(LoveLeaderboards plugin, Player player) {
        Deque<GuiState> history = historyMap.get(player.getUniqueId());
        if (history != null && !history.isEmpty()) {
            GuiState previousState = history.pop();
            previousState.open(plugin, player);
            return true;
        }
        return false;
    }

    public static void clearHistory(Player player) {
        historyMap.remove(player.getUniqueId());
    }
}
