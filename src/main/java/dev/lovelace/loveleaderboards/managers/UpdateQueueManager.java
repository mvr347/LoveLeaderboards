package dev.lovelace.loveleaderboards.managers;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.LeaderboardUpdate;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UpdateQueueManager {
    private final Queue<LeaderboardUpdate> queue = new ConcurrentLinkedQueue<>();
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
        queue.offer(new LeaderboardUpdate(
            entityId.toString(), entityName != null ? entityName : "Unknown", entityType != null ? entityType : "player", category, score
        ));
    }

    public void enqueue(String entityId, String entityName, String entityType, String category, double score) {
        if (entityId == null || category == null || Double.isNaN(score) || Double.isInfinite(score)) return;
        queue.offer(new LeaderboardUpdate(
            entityId, entityName != null ? entityName : "Unknown", entityType != null ? entityType : "player", category, score
        ));
    }

    public synchronized void flushBatch() {
        if (queue.isEmpty()) return;

        List<LeaderboardUpdate> batch = new ArrayList<>();
        int count = 0;
        while (!queue.isEmpty() && count < batchSize) {
            batch.add(queue.poll());
            count++;
        }

        if (!batch.isEmpty()) {
            dbManager.batchUpdateScores(batch);
            if (plugin.getLeaderboardManager() != null) {
                plugin.getLeaderboardManager().invalidateAllCaches();
            }
        }
    }

    public synchronized void flushAll() {
        boolean updated = false;
        while (!queue.isEmpty()) {
            List<LeaderboardUpdate> batch = new ArrayList<>();
            int count = 0;
            while (!queue.isEmpty() && count < batchSize) {
                batch.add(queue.poll());
                count++;
            }
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
