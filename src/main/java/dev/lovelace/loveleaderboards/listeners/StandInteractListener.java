package dev.lovelace.loveleaderboards.listeners;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.gui.LeaderboardMainGui;
import dev.lovelace.loveleaderboards.models.LeaderboardStand;
import dev.lovelace.loveleaderboards.models.TimePeriod;
import dev.lovelace.loveleaderboards.utils.TextUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class StandInteractListener implements Listener {
    private final LoveLeaderboards plugin;

    public StandInteractListener(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        LeaderboardStand stand = plugin.getHallOfFameManager().getStandByHologramUuid(event.getRightClicked().getUniqueId());

        if (stand != null) {
            event.setCancelled(true);
            handleStandInteract(player, stand);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCitizensNpcClick(net.citizensnpcs.api.event.NPCRightClickEvent event) {
        Player player = event.getClicker();
        LeaderboardStand stand = plugin.getHallOfFameManager().getStandByNpcId(event.getNPC().getId());

        if (stand != null) {
            event.setCancelled(true);
            handleStandInteract(player, stand);
        }
    }

    private void handleStandInteract(Player player, LeaderboardStand stand) {
        if (player.isSneaking()) {
            // Shift + Right-Click -> Cycle period
            plugin.getHallOfFameManager().togglePeriod(stand);

            String periodKey = stand.getCurrentPeriod().toLowerCase();
            String periodName = plugin.getConfig().getString("hall-of-fame.periods." + periodKey,
                    switch (periodKey) {
                        case "monthly" -> "За месяц";
                        case "weekly" -> "За неделю";
                        case "yearly" -> "За год";
                        default -> "За всё время";
                    });

            String messageTemplate = plugin.getConfig().getString("hall-of-fame.interaction.shift-click-message",
                    "&a[Зал Славы] &fПериод отображения переключен на: &e%period%");
            player.sendMessage(TextUtil.colorize(messageTemplate.replace("%period%", periodName)));

            String soundStr = plugin.getConfig().getString("hall-of-fame.interaction.shift-click-sound", "BLOCK_NOTE_BLOCK_PLING");
            float volume = (float) plugin.getConfig().getDouble("hall-of-fame.interaction.shift-click-sound-volume", 1.0);
            float pitch = (float) plugin.getConfig().getDouble("hall-of-fame.interaction.shift-click-sound-pitch", 1.5);
            try {
                player.playSound(player.getLocation(), Sound.valueOf(soundStr.toUpperCase()), volume, pitch);
            } catch (IllegalArgumentException ignored) {}
        } else {
            // Normal Right-Click -> Open standardized LeaderboardMainGui for stand category and period
            String soundStr = plugin.getConfig().getString("hall-of-fame.interaction.normal-click-sound", "UI_BUTTON_CLICK");
            float volume = (float) plugin.getConfig().getDouble("hall-of-fame.interaction.normal-click-sound-volume", 1.0);
            float pitch = (float) plugin.getConfig().getDouble("hall-of-fame.interaction.normal-click-sound-pitch", 1.0);
            try {
                player.playSound(player.getLocation(), Sound.valueOf(soundStr.toUpperCase()), volume, pitch);
            } catch (IllegalArgumentException ignored) {}

            TimePeriod period = TimePeriod.fromString(stand.getCurrentPeriod());
            player.openInventory(new LeaderboardMainGui(plugin, player, stand.getCategory(), period, 1).getInventory());
        }
    }
}
