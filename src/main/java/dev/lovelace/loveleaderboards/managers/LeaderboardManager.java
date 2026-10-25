package dev.lovelace.loveleaderboards.managers;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.Comparison;
import dev.lovelace.loveleaderboards.models.LeaderboardEntry;
import dev.lovelace.loveleaderboards.models.PlayerStats;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LeaderboardManager {
    private final LoveLeaderboards plugin;
    private final DatabaseManager databaseManager;
    private final CategoryManager categoryManager;
    private final CacheManager<String, List<LeaderboardEntry>> cacheManager;
    private final CacheManager<String, PlayerStats> statsCache;
    private final UpdateQueueManager updateQueue;
    private final InFlightGuard<String, List<LeaderboardEntry>> topInFlight = new InFlightGuard<>();
    private final InFlightGuard<String, Optional<PlayerStats>> statsInFlight = new InFlightGuard<>();
    private final java.util.concurrent.Executor asyncExecutor;
    /** Top lists somebody asked for recently; the periodic refresh keeps exactly these warm. */
    private final java.util.concurrent.ConcurrentMap<String, HotTop> hotTops = new ConcurrentHashMap<>();
    private final Set<String> dirtyCategories = ConcurrentHashMap.newKeySet();
    private static final long HOT_IDLE_MS = 5 * 60_000L;

    private record HotTop(String category, String entityType, String period, int limit, long lastRequested) {}

    public LeaderboardManager(LoveLeaderboards plugin, DatabaseManager databaseManager, 
                              CategoryManager categoryManager, UpdateQueueManager updateQueue,
                              long cacheTtlMs, int cacheMaxSize) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.categoryManager = categoryManager;
        this.updateQueue = updateQueue;
        this.cacheManager = new CacheManager<>(cacheMaxSize, cacheTtlMs);
        this.statsCache = new CacheManager<>(cacheMaxSize, cacheTtlMs);
        this.asyncExecutor = r -> Bukkit.getScheduler().runTaskAsynchronously(plugin, r);
    }

    /**
     * One periodic async pass (replaces invalidating everything every flush): re-fetches the hot top
     * lists, stale-while-revalidate, so PAPI/GUI readers on the main thread never see an empty miss.
     */
    public void refreshHotTops() {
        long now = System.currentTimeMillis();
        hotTops.values().removeIf(h -> now - h.lastRequested() > HOT_IDLE_MS);
        dirtyCategories.clear();
        for (HotTop h : hotTops.values()) {
            String key = topKey(h.category(), h.entityType(), h.period(), h.limit());
            topInFlight.load(key, () -> {
                List<LeaderboardEntry> filled = fillEmptyRanks(
                        databaseManager.getTopByCategory(h.category(), h.entityType(), h.period(), h.limit()),
                        h.limit(), h.entityType(), h.category());
                cacheManager.put(key, filled);
                return filled;
            }, asyncExecutor);
        }
    }

    /** Called after a score batch: only stats of the touched categories are dropped; tops refresh periodically. */
    public void invalidateCategories(Set<String> categories) {
        if (categories == null || categories.isEmpty()) return;
        dirtyCategories.addAll(categories);
        statsCache.invalidateIf(k -> {
            // key = type:id:category:period ; ids may contain ':' only for clan names, so match on segments
            for (String c : categories) {
                if (k.contains(":" + c + ":")) return true;
            }
            return false;
        });
    }

    private static String topKey(String category, String entityType, String timePeriod, int limit) {
        return String.format("%s:%s:%s:%s", category, entityType, timePeriod, limit);
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
        String cacheKey = topKey(category, entityType, timePeriod, limit);
        hotTops.put(cacheKey, new HotTop(category, entityType, timePeriod, limit, System.currentTimeMillis()));

        Optional<List<LeaderboardEntry>> cached = cacheManager.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        java.util.function.Supplier<List<LeaderboardEntry>> loader = () -> {
            List<LeaderboardEntry> top = databaseManager.getTopByCategory(category, entityType, timePeriod, limit);
            List<LeaderboardEntry> filled = fillEmptyRanks(top, limit, entityType, category);
            cacheManager.put(cacheKey, filled);
            return filled;
        };

        if (!Bukkit.isPrimaryThread()) {
            return loader.get();
        } else {
            // Return empty while fetching to not block main thread (PAPI); concurrent misses share one query
            topInFlight.load(cacheKey, loader, asyncExecutor);
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

    private final java.util.concurrent.atomic.AtomicBoolean sweepQueued = new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Deletes the rows of clans that no longer exist. LoveClans drops a clan without a disband event
     * when its last member leaves, so listening to the event alone leaves such clans in the top.
     * Called on the main thread (reads LoveClans); the delete itself runs async. Does nothing when the
     * live list is unavailable or empty, so a clan plugin that has not loaded its data yet cannot
     * trigger a mass delete.
     */
    public void sweepOrphanClans() {
        var live = databaseManager.liveClanIds();
        if (live.isEmpty() || live.get().isEmpty()) return;
        java.util.Set<String> ids = live.get();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var orphans = dev.lovelace.loveleaderboards.managers.ClanLiveness.orphans(databaseManager.clanEntityIds(), ids);
            int removed = 0;
            for (String id : orphans) removed += databaseManager.removeEntity("clan", id);
            if (removed > 0) {
                invalidateAllCaches();
                plugin.getLogger().info("Удалено строк несуществующих кланов из топа: " + removed);
            }
        });
    }

    /** Sweeps shortly after a clan change, once however many events arrive together. */
    public void sweepOrphanClansSoon() {
        if (!sweepQueued.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sweepQueued.set(false);
            sweepOrphanClans();
        }, 40L);
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
            statsInFlight.load(cacheKey, () -> {
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
            }, asyncExecutor);
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
