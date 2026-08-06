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
        if (categoryManager.getCategory(category).isEmpty() || Double.isNaN(scoreDelta) || Double.isInfinite(scoreDelta)) return;
        updateQueue.enqueue(uuid, playerName, "player", category, scoreDelta);
    }

    public void updateClanScore(String clanId, String clanName, String category, double scoreDelta) {
        if (categoryManager.getCategory(category).isEmpty() || Double.isNaN(scoreDelta) || Double.isInfinite(scoreDelta)) return;
        updateQueue.enqueue(clanId, clanName, "clan", category, scoreDelta);
    }

    public void setPlayerScore(UUID uuid, String playerName, String category, double exactScore) {
        if (categoryManager.getCategory(category).isEmpty() || Double.isNaN(exactScore) || Double.isInfinite(exactScore)) return;
        if (!Bukkit.isPrimaryThread()) {
            databaseManager.setScore("player", uuid.toString(), playerName, category, exactScore);
            invalidateAllCaches();
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                databaseManager.setScore("player", uuid.toString(), playerName, category, exactScore);
                invalidateAllCaches();
            });
        }
    }

    public void setClanScore(String clanId, String clanName, String category, double exactScore) {
        if (categoryManager.getCategory(category).isEmpty() || Double.isNaN(exactScore) || Double.isInfinite(exactScore)) return;
        if (!Bukkit.isPrimaryThread()) {
            databaseManager.setScore("clan", clanId, clanName, category, exactScore);
            invalidateAllCaches();
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                databaseManager.setScore("clan", clanId, clanName, category, exactScore);
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
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                List<LeaderboardEntry> top = databaseManager.getTopByCategory(category, entityType, timePeriod, limit);
                List<LeaderboardEntry> filled = fillEmptyRanks(top, limit, entityType, category);
                cacheManager.put(cacheKey, filled);
            });
            return new ArrayList<>();
        }
    }

    public void removeClan(String clanId, String clanName) {
        if (!Bukkit.isPrimaryThread()) {
            if (clanId != null) databaseManager.removeEntity("clan", clanId);
            if (clanName != null) databaseManager.removeEntity("clan", clanName);
            invalidateAllCaches();
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (clanId != null) databaseManager.removeEntity("clan", clanId);
                if (clanName != null) databaseManager.removeEntity("clan", clanName);
                invalidateAllCaches();
            });
        }
    }

    public void ensurePlayerExists(UUID uuid, String playerName) {
        if (!Bukkit.isPrimaryThread()) {
            for (dev.lovelace.loveleaderboards.models.Category cat : categoryManager.getAllCategories()) {
                databaseManager.ensurePlayerExists(uuid, playerName, cat.name());
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
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
        return getEntityStats("player", uuid.toString(), category, timePeriodKey);
    }

    public Optional<PlayerStats> getEntityStats(String entityType, String entityId, String category, String timePeriodKey) {
        String cacheKey = entityType + ":" + entityId + ":" + category + ":" + timePeriodKey;
        Optional<PlayerStats> cached = statsCache.get(cacheKey);
        if (cached.isPresent()) {
            if (cached.get().playerName().equals("Unknown")) return Optional.empty();
            return cached;
        }

        if (!Bukkit.isPrimaryThread()) {
            Optional<PlayerStats> fetched = databaseManager.getEntityStats(entityType, entityId, category, timePeriodKey);
            if (fetched.isPresent()) {
                statsCache.put(cacheKey, fetched.get());
            } else {
                UUID id;
                try {
                    id = UUID.fromString(entityId);
                } catch (Exception e) {
                    id = UUID.nameUUIDFromBytes(entityId.getBytes());
                }
                statsCache.put(cacheKey, new PlayerStats(id, "Unknown", 0, 0));
            }
            return fetched;
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                Optional<PlayerStats> fetched = databaseManager.getEntityStats(entityType, entityId, category, timePeriodKey);
                if (fetched.isPresent()) {
                    statsCache.put(cacheKey, fetched.get());
                } else {
                    UUID id;
                    try {
                        id = UUID.fromString(entityId);
                    } catch (Exception e) {
                        id = UUID.nameUUIDFromBytes(entityId.getBytes());
                    }
                    statsCache.put(cacheKey, new PlayerStats(id, "Unknown", 0, 0));
                }
            });
            return Optional.empty();
        }
    }

    public Comparison compareWithPlayer(UUID uuid1, String name1, UUID uuid2, String name2, String category) {
        return compareWithPlayer(uuid1, name1, uuid2, name2, category, dev.lovelace.loveleaderboards.models.TimePeriod.ALL_TIME.getDbKey());
    }

    public Comparison compareWithPlayer(UUID uuid1, String name1, UUID uuid2, String name2, String category, String timePeriodKey) {
        return compareEntities("player", uuid1.toString(), name1, uuid2.toString(), name2, category, timePeriodKey);
    }

    public Comparison compareEntities(String entityType, String id1Str, String name1, String id2Str, String name2, String category, String timePeriodKey) {
        Optional<PlayerStats> stats1 = getEntityStats(entityType, id1Str, category, timePeriodKey);
        Optional<PlayerStats> stats2 = getEntityStats(entityType, id2Str, category, timePeriodKey);

        UUID uuid1;
        try { uuid1 = UUID.fromString(id1Str); } catch (Exception e) { uuid1 = UUID.nameUUIDFromBytes(id1Str.getBytes()); }
        UUID uuid2;
        try { uuid2 = UUID.fromString(id2Str); } catch (Exception e) { uuid2 = UUID.nameUUIDFromBytes(id2Str.getBytes()); }

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
