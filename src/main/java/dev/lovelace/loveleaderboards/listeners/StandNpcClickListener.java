package dev.lovelace.loveleaderboards.listeners;

import dev.lovelace.lovecore.api.npc.NpcClick;
import dev.lovelace.lovecore.api.npc.NpcClickEvent;
import dev.lovelace.lovecore.api.npc.NpcHandle;
import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.integrations.StandNpcs;
import dev.lovelace.loveleaderboards.models.LeaderboardStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Right-click on a stand NPC from any NpcProvider. LoveCore fires the event for every provider, so clicks from
 * a provider other than the configured one are ignored. Registered only when LoveCore is present.
 */
public class StandNpcClickListener implements Listener {
    private final LoveLeaderboards plugin;

    public StandNpcClickListener(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNpcClick(NpcClickEvent event) {
        if (event.click() != NpcClick.RIGHT) {
            return;
        }
        NpcHandle npc = event.npc();
        if (!npc.providerId().equals(plugin.getStandNpcs().configuredProvider())) {
            return;
        }
        LeaderboardStand stand = null;
        String tagged = npc.tag(StandNpcs.TAG_STAND).orElse(null);
        if (tagged != null) {
            stand = plugin.getHallOfFameManager().getStandById(tagged);
        }
        if (stand == null) {
            // Stands whose NPC predates tags: match by the stored (provider, key) pair.
            stand = plugin.getHallOfFameManager().getStandByNpc(npc.providerId(), npc.key());
        }
        if (stand != null) {
            event.setCancelled(true);
            StandInteractListener.handleStandInteract(plugin, event.getPlayer(), stand);
        }
    }
}
