package dev.lovelace.loveleaderboards.managers;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.Comparison;
import dev.lovelace.loveleaderboards.models.LeaderboardEntry;
import dev.lovelace.loveleaderboards.models.PlayerStats;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LeaderboardManager {
    private final LoveLeaderboards plugin;
    private final DatabaseManager databaseManager;
    private final CategoryManager categoryManager;
    private final CacheManager<String, List<LeaderboardEntry>> cacheManager;
    private final CacheManager<String, PlayerStats> statsCache;
    private final UpdateQueueManager updateQueue;

    public LeaderboardManager(LoveLeaderboards plugin, DatabaseManager databaseManager, 
                              CategoryManager categoryManager, UpdateQueueManager updateQueue,
                              long cacheTtlMs, int cacheMaxSize) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.categoryManager = categoryManager;
        this.updateQueue = updateQueue;
        this.cacheManager = new CacheManager<>(cacheMaxSize, cacheTtlMs);
        this.statsCache = new CacheManager<>(cacheMaxSize, cacheTtlMs);
    }

    public void updatePlayerScore(UUID uuid, String playerName, String category, double scoreDelta) {
        if (categoryManager.getCategory(category).isEmpty()) return;
        updateQueue.enqueue(uuid, playerName, "player", category, scoreDelta);
        invalidateAllCaches();
    }

    public void updateClanScore(String clanId, String clanName, String category, double scoreDelta) {
        if (categoryManager.getCategory(category).isEmpty()) return;
        updateQueue.enqueue(UUID.nameUUIDFromBytes(clanId.getBytes()).toString(), clanName, "clan", category, scoreDelta);
        invalidateAllCaches();
    }

    public void setPlayerScore(UUID uuid, String playerName, String category, double exactScore) {
        if (categoryManager.getCategory(category).isEmpty()) return;
        if (!Bukkit.isPrimaryThread()) {
            databaseManager.setScore("player", uuid.toString(), playerName, category, exactScore);
            invalidateAllCaches();
        } else {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                databaseManager.setScore("player", uuid.toString(), playerName, category, exactScore);
                invalidateAllCaches();
            });
        }
    }

    public void setClanScore(String clanId, String clanName, String category, double exactScore) {
        if (categoryManager.getCategory(category).isEmpty()) return;
        String entityId = UUID.nameUUIDFromBytes(clanId.getBytes()).toString();
        if (!Bukkit.isPrimaryThread()) {
            databaseManager.setScore("clan", entityId, clanName, category, exactScore);
            invalidateAllCaches();
        } else {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                databaseManager.setScore("clan", entityId, clanName, category, exactScore);
                invalidateAllCaches();
            });
        }
    }

    public List<LeaderboardEntry> getTop(String category, String entityType, String timePeriod, int limit) {
        String cacheKey = String.format("%s:%s:%s:%s", category, entityType, timePeriod, limit);

        Optional<List<LeaderboardEntry>> cached = cacheManager.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        if (!Bukkit.isPrimaryThread()) {
            List<LeaderboardEntry> top = databaseManager.getTopByCategory(category, entityType, timePeriod, limit);
            List<LeaderboardEntry> filled = fillEmptyRanks(top, limit, entityType, category);
            cacheManager.put(cacheKey, filled);
            return filled;
        } else {
            // Return empty while fetching to not block main thread (PAPI)
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                List<LeaderboardEntry> top = databaseManager.getTopByCategory(category, entityType, timePeriod, limit);
                List<LeaderboardEntry> filled = fillEmptyRanks(top, limit, entityType, category);
                cacheManager.put(cacheKey, filled);
            });
            return new ArrayList<>();
        }
    }

    public void ensurePlayerExists(UUID uuid, String playerName) {
        if (!Bukkit.isPrimaryThread()) {
            for (dev.lovelace.loveleaderboards.models.Category cat : categoryManager.getAllCategories()) {
                databaseManager.ensurePlayerExists(uuid, playerName, cat.name());
            }
        } else {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                for (dev.lovelace.loveleaderboards.models.Category cat : categoryManager.getAllCategories()) {
                    databaseManager.ensurePlayerExists(uuid, playerName, cat.name());
                }
            });
        }
    }

    public Optional<PlayerStats> getPlayerStats(UUID uuid, String category) {
        return getPlayerStats(uuid, category, dev.lovelace.loveleaderboards.models.TimePeriod.ALL_TIME.getDbKey());
    }

    public Optional<PlayerStats> getPlayerStats(UUID uuid, String category, String timePeriodKey) {
        String cacheKey = uuid.toString() + ":" + category + ":" + timePeriodKey;
        Optional<PlayerStats> cached = statsCache.get(cacheKey);
        if (cached.isPresent()) {
            if (cached.get().playerName().equals("Unknown")) return Optional.empty();
            return cached;
        }

        if (!Bukkit.isPrimaryThread()) {
            Optional<PlayerStats> fetched = databaseManager.getPlayerStats(uuid, category, timePeriodKey);
            if (fetched.isPresent()) {
                statsCache.put(cacheKey, fetched.get());
            } else {
                statsCache.put(cacheKey, new PlayerStats(uuid, "Unknown", 0, 0)); // Cache miss
            }
            return fetched;
        } else {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                Optional<PlayerStats> fetched = databaseManager.getPlayerStats(uuid, category, timePeriodKey);
                if (fetched.isPresent()) {
                    statsCache.put(cacheKey, fetched.get());
                } else {
                    statsCache.put(cacheKey, new PlayerStats(uuid, "Unknown", 0, 0));
                }
            });
            return Optional.empty();
        }
    }

    public Comparison compareWithPlayer(UUID uuid1, String name1, UUID uuid2, String name2, String category) {
        return compareWithPlayer(uuid1, name1, uuid2, name2, category, dev.lovelace.loveleaderboards.models.TimePeriod.ALL_TIME.getDbKey());
    }

    public Comparison compareWithPlayer(UUID uuid1, String name1, UUID uuid2, String name2, String category, String timePeriodKey) {
        Optional<PlayerStats> stats1 = getPlayerStats(uuid1, category, timePeriodKey);
        Optional<PlayerStats> stats2 = getPlayerStats(uuid2, category, timePeriodKey);
        
        return new Comparison(
            uuid1, name1, stats1.orElse(new PlayerStats(uuid1, name1, 0, 0)),
            uuid2, name2, stats2.orElse(new PlayerStats(uuid2, name2, 0, 0)),
            category
        );
    }

    private List<LeaderboardEntry> fillEmptyRanks(List<LeaderboardEntry> top, int limit, String entityType, String category) {
        List<LeaderboardEntry> filled = new ArrayList<>(top);
        while (filled.size() < limit) {
            filled.add(new LeaderboardEntry(
                entityType,
                "empty",
                "Свободное место",
                filled.size() + 1,
                0,
                System.currentTimeMillis() / 1000
            ));
        }
        return filled;
    }

    public void invalidateAllCaches() {
        cacheManager.invalidateAll();
        statsCache.invalidateAll();
    }
}
