package dev.lovelace.loveleaderboards.managers;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.LeaderboardUpdate;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UpdateQueueManager {
    private record Key(String entityType, String entityId, String category) {
    }

    // Updates are score deltas, so pending ones for the same (type, entity, category) are summed instead of queued
    // one by one: memory is bounded by distinct entries rather than by event rate when the database falls behind.
    private final ConcurrentHashMap<Key, LeaderboardUpdate> pending = new ConcurrentHashMap<>();
    private final LoveLeaderboards plugin;
    private final DatabaseManager dbManager;
    private final int batchSize;

    public UpdateQueueManager(LoveLeaderboards plugin, DatabaseManager dbManager, long intervalTicks, int batchSize) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        this.batchSize = batchSize;

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushBatch, intervalTicks, intervalTicks);
    }

    public void enqueue(UUID entityId, String entityName, String entityType, String category, double score) {
        if (entityId == null || category == null || Double.isNaN(score) || Double.isInfinite(score)) return;
        offer(new LeaderboardUpdate(
            entityId.toString(), entityName != null ? entityName : "Unknown", entityType != null ? entityType : "player", category, score
        ));
    }

    public void enqueue(String entityId, String entityName, String entityType, String category, double score) {
        if (entityId == null || category == null || Double.isNaN(score) || Double.isInfinite(score)) return;
        offer(new LeaderboardUpdate(
            entityId, entityName != null ? entityName : "Unknown", entityType != null ? entityType : "player", category, score
        ));
    }

    private void offer(LeaderboardUpdate update) {
        pending.merge(new Key(update.entityType(), update.entityId(), update.category()), update,
            (old, added) -> new LeaderboardUpdate(old.entityId(), added.entityName(), old.entityType(), old.category(),
                old.score() + added.score()));
    }

    /** Takes up to {@code max} pending updates out of the map; each key is removed atomically. */
    private List<LeaderboardUpdate> drain(int max) {
        List<LeaderboardUpdate> batch = new ArrayList<>();
        for (Key key : pending.keySet()) {
            if (batch.size() >= max) break;
            LeaderboardUpdate update = pending.remove(key);
            if (update != null) batch.add(update);
        }
        return batch;
    }

    public synchronized void flushBatch() {
        if (pending.isEmpty()) return;

        List<LeaderboardUpdate> batch = drain(batchSize);

        if (!batch.isEmpty()) {
            dbManager.batchUpdateScores(batch);
            if (plugin.getLeaderboardManager() != null) {
                plugin.getLeaderboardManager().invalidateCategories(categoriesOf(batch));
            }
        }
    }

    private static java.util.Set<String> categoriesOf(List<LeaderboardUpdate> batch) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (LeaderboardUpdate u : batch) out.add(u.category());
        return out;
    }

    public synchronized void flushAll() {
        boolean updated = false;
        while (!pending.isEmpty()) {
            List<LeaderboardUpdate> batch = drain(batchSize);
            if (!batch.isEmpty()) {
                dbManager.batchUpdateScores(batch);
                updated = true;
            }
        }
        if (updated && plugin.getLeaderboardManager() != null) {
            plugin.getLeaderboardManager().invalidateAllCaches();
        }
    }
}
