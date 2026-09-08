package dev.lovelace.loveleaderboards.integrations;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Pure-reflection integration with Vesuvio AntiCheat.
 * Filters out high-risk and flagged suspect players from leaderboards and reward distributions.
 *
 * Author: Lovelace
 */
public final class VesuvioIntegration {

    private static boolean enabled = true;

    private VesuvioIntegration() {}

    public static void setEnabled(boolean isEnabled) {
        enabled = isEnabled;
    }

    public static boolean isAvailable() {
        return enabled && Bukkit.getPluginManager().isPluginEnabled("Vesuvio");
    }

    public static boolean isSuspect(String entityId) {
        if (!isAvailable() || entityId == null || entityId.isBlank() || "empty".equalsIgnoreCase(entityId)) {
            return false;
        }
        try {
            UUID uuid = UUID.fromString(entityId);
            return isSuspect(uuid);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isSuspect(UUID uuid) {
        if (!isAvailable() || uuid == null) {
            return false;
        }
        try {
            Class<?> providerClass = Class.forName("net.lovelace.vesuvio.api.VesuvioProvider");
            Method isAvailMethod = providerClass.getMethod("isAvailable");
            if (!((boolean) isAvailMethod.invoke(null))) return false;

            Method getMethod = providerClass.getMethod("get");
            Object api = getMethod.invoke(null);
            if (api == null) return false;

            Class<?> apiClass = Class.forName("net.lovelace.vesuvio.api.VesuvioAPI");
            Method isSuspectMethod = apiClass.getMethod("isSuspect", UUID.class);
            if ((boolean) isSuspectMethod.invoke(api, uuid)) {
                return true;
            }

            Method isHighRiskMethod = apiClass.getMethod("isHighRisk", UUID.class);
            if ((boolean) isHighRiskMethod.invoke(api, uuid)) {
                return true;
            }

            Method isQueuedMethod = apiClass.getMethod("isQueuedForWave", UUID.class);
            return (boolean) isQueuedMethod.invoke(api, uuid);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
