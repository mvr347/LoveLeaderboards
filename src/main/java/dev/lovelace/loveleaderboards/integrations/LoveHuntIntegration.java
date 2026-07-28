package dev.lovelace.loveleaderboards.integrations;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;

public class LoveHuntIntegration implements Listener {
    private final LoveLeaderboards plugin;

    public LoveHuntIntegration(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    public void register() {
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName("dev.lovelace.lovehunt.events.BountyCompletedEvent");
            
            EventExecutor executor = (listener, event) -> {
                if (!eventClass.isInstance(event)) return;
                try {
                    Method getHunter = eventClass.getMethod("getHunter");
                    Player hunter = (Player) getHunter.invoke(event);
                    
                    // We can also extract reward if needed:
                    // Method getReward = eventClass.getMethod("getReward");
                    // double reward = (double) getReward.invoke(event);
                    
                    if (hunter != null) {
                        for (dev.lovelace.loveleaderboards.models.Category cat : plugin.getCategoryManager().getAllCategories()) {
                            if ("LoveHunt".equalsIgnoreCase(cat.integration())) {
                                plugin.getLeaderboardManager().updatePlayerScore(
                                    hunter.getUniqueId(),
                                    hunter.getName(),
                                    cat.name(),
                                    1.0
                                );
                            }
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to process BountyCompletedEvent: " + e.getMessage());
                }
            };
            
            Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.NORMAL, executor, plugin);
            plugin.getLogger().info("Successfully hooked into LoveHunt BountyCompletedEvent.");
            
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("LoveHunt event class not found. Is LoveHunt installed and version correct?");
        }
    }
}
