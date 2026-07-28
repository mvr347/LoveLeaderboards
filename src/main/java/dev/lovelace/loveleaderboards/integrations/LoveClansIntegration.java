package dev.lovelace.loveleaderboards.integrations;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.List;

public class LoveClansIntegration {
    private final LoveLeaderboards plugin;

    public LoveClansIntegration(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    public void startSyncTask() {
        long interval = plugin.getConfig().getLong("integrations.love-clans.sync-interval", 3600) * 20L;
        
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::syncClanPower, interval, interval);
    }

    private void syncClanPower() {
        try {
            // Using reflection to load LoveClansAPI
            Class<?> apiClass = Class.forName("dev.lovelace.loveclans.api.LoveClansAPI");
            Object apiInstance = Bukkit.getServicesManager().load(apiClass);
            
            if (apiInstance == null) {
                return;
            }

            Method getAllClans = apiClass.getMethod("getAllClans");
            List<?> allClans = (List<?>) getAllClans.invoke(apiInstance);

            for (Object clan : allClans) {
                Class<?> clanClass = clan.getClass();
                String id = (String) clanClass.getMethod("getId").invoke(clan);
                String name = (String) clanClass.getMethod("getName").invoke(clan);
                double power = (double) clanClass.getMethod("getPower").invoke(clan);

                for (dev.lovelace.loveleaderboards.models.Category cat : plugin.getCategoryManager().getAllCategories()) {
                    if ("LoveClans".equalsIgnoreCase(cat.integration())) {
                        plugin.getLeaderboardManager().updateClanScore(id, name, cat.name(), power);
                    }
                }
            }
        } catch (Exception e) {
            // Silently fail or debug log if LoveClans is not properly available
            // plugin.getLogger().warning("Failed to sync clan power: " + e.getMessage());
        }
    }

    public String getPlayerClanName(java.util.UUID playerUuid) {

        try {
            Class<?> apiClass = Class.forName("dev.lovelace.loveclans.api.LoveClansAPI");
            Object apiInstance = Bukkit.getServicesManager().load(apiClass);
            if (apiInstance != null) {
                Method getClan = apiClass.getMethod("getClanByPlayer", java.util.UUID.class);
                Object clan = getClan.invoke(apiInstance, playerUuid);
                if (clan != null) {
                    return (String) clan.getClass().getMethod("getName").invoke(clan);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public String getPlayerClanId(java.util.UUID playerUuid) {
        try {
            Class<?> apiClass = Class.forName("dev.lovelace.loveclans.api.LoveClansAPI");
            Object apiInstance = Bukkit.getServicesManager().load(apiClass);
            if (apiInstance != null) {
                Method getClan = apiClass.getMethod("getClanByPlayer", java.util.UUID.class);
                Object clan = getClan.invoke(apiInstance, playerUuid);
                if (clan != null) {
                    return (String) clan.getClass().getMethod("getId").invoke(clan);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}

