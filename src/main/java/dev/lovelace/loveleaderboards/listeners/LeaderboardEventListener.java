package dev.lovelace.loveleaderboards.listeners;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import org.bukkit.event.player.PlayerJoinEvent;

public class LeaderboardEventListener implements Listener {
    private final LoveLeaderboards plugin;

    public LeaderboardEventListener(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getLeaderboardManager().ensurePlayerExists(player.getUniqueId(), player.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null && !killer.equals(victim)) {
            plugin.getLeaderboardManager().updatePlayerScore(
                killer.getUniqueId(),
                killer.getName(),
                "kills",
                1.0
            );
        }
    }
}
