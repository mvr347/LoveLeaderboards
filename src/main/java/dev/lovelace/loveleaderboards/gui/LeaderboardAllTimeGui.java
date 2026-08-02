package dev.lovelace.loveleaderboards.gui;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.TimePeriod;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public class LeaderboardAllTimeGui extends BaseGui {
    private final LoveLeaderboards plugin;
    private final Player viewer;
    private final String currentCategory;
    private final String entityType;

    public LeaderboardAllTimeGui(LoveLeaderboards plugin, Player viewer, String currentCategory, String entityType) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.currentCategory = currentCategory;
        this.entityType = entityType != null ? entityType : "player";
        LeaderboardMainGui mainGui = new LeaderboardMainGui(plugin, viewer, currentCategory, TimePeriod.ALL_TIME, this.entityType, 1);
        this.inventory = mainGui.getInventory();
    }

    @Override
    public void open(Player player) {
        player.openInventory(new LeaderboardMainGui(plugin, viewer, currentCategory, TimePeriod.ALL_TIME, entityType, 1).getInventory());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {}
}
