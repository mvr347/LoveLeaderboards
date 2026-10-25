package dev.lovelace.loveleaderboards.managers;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.LeaderboardEntry;
import dev.lovelace.loveleaderboards.models.LeaderboardUpdate;
import dev.lovelace.loveleaderboards.models.PlayerStats;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {
    private final LoveLeaderboards plugin;
    private final Object dbLock = new Object();
    private Connection connection;
    private final java.util.concurrent.atomic.AtomicBoolean clanApiWarned = new java.util.concurrent.atomic.AtomicBoolean();

    public DatabaseManager(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        synchronized (dbLock) {
            try {
                File dbFile = new File(plugin.getDataFolder(), "leaderboards.db");
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                createTables();
            } catch (SQLException e) {
                plugin.getLogger().severe("DB init failed: " + e.getMessage());
            }
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS leaderboard_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entity_type TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    entity_name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    time_period TEXT NOT NULL,
                    score REAL NOT NULL DEFAULT 0,
                    updated_at INTEGER,
                    UNIQUE(entity_type, entity_id, category, time_period)
                );
            """);
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_leaderboard 
                ON leaderboard_entries(time_period, category, score DESC);
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS leaderboard_monthly_rewards (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    year_month TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    entity_name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    rank INTEGER,
                    score REAL,
                    rewards_given BOOLEAN DEFAULT 0,
                    given_at INTEGER,
                    UNIQUE(year_month, entity_id, category)
                );
            """);
            // 2026-10-19: награды победителям, которых не было в сети в момент выдачи; выполняются при входе игрока.
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS leaderboard_pending_rewards (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entity_id TEXT NOT NULL,
                    year_month TEXT NOT NULL,
                    category TEXT NOT NULL,
                    score REAL NOT NULL DEFAULT 0,
                    commands TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    UNIQUE(year_month, entity_id, category)
                );
            """);
        }
    }

    public void batchUpdateScores(List<LeaderboardUpdate> batch) {
        if (batch == null || batch.isEmpty()) return;

        String upsertSql = "INSERT INTO leaderboard_entries (entity_type, entity_id, entity_name, category, time_period, score, updated_at) " +
                           "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                           "ON CONFLICT(entity_type, entity_id, category, time_period) DO UPDATE SET score = score + excluded.score, updated_at = excluded.updated_at";

        synchronized (dbLock) {
            if (connection == null) return;
            try (PreparedStatement ps = connection.prepareStatement(upsertSql)) {
                connection.setAutoCommit(false);
                long currentTime = System.currentTimeMillis() / 1000;
                
                String allTimeKey = dev.lovelace.loveleaderboards.models.TimePeriod.ALL_TIME.getDbKey();
                String monthlyKey = dev.lovelace.loveleaderboards.models.TimePeriod.MONTHLY.getDbKey();
                String weeklyKey = dev.lovelace.loveleaderboards.models.TimePeriod.WEEKLY.getDbKey();
                String todayKey = dev.lovelace.loveleaderboards.models.TimePeriod.TODAY.getDbKey();

                String[] periods = {allTimeKey, monthlyKey, weeklyKey, todayKey};

                for (LeaderboardUpdate update : batch) {
                    if (update == null || Double.isNaN(update.score()) || Double.isInfinite(update.score())) continue;
                    for (String period : periods) {
                        ps.setString(1, update.entityType());
                        ps.setString(2, update.entityId());
                        ps.setString(3, update.entityName());
                        ps.setString(4, update.category());
                        ps.setString(5, period);
                        ps.setDouble(6, update.score());
                        ps.setLong(7, currentTime);
                        ps.addBatch();
                    }
                }

                ps.executeBatch();
                connection.commit();
                connection.setAutoCommit(true);

            } catch (SQLException e) {
                plugin.getLogger().warning("Batch update failed: " + e.getMessage());
                try {
                    if (connection != null && !connection.getAutoCommit()) {
                        connection.rollback();
                        connection.setAutoCommit(true);
                    }
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().log(Level.FINE, "Failed to rollback transaction", rollbackEx);
                }
            }
        }
    }

    public void ensurePlayerExists(UUID uuid, String playerName, String category) {
        if (uuid == null || category == null) return;
        String sql = "INSERT OR IGNORE INTO leaderboard_entries (entity_type, entity_id, entity_name, category, time_period, score, updated_at) " +
                     "VALUES ('player', ?, ?, ?, ?, 0.0, ?)";
        long currentTime = System.currentTimeMillis() / 1000;
        String[] periods = {
            dev.lovelace.loveleaderboards.models.TimePeriod.ALL_TIME.getDbKey(),
            dev.lovelace.loveleaderboards.models.TimePeriod.MONTHLY.getDbKey(),
            dev.lovelace.loveleaderboards.models.TimePeriod.WEEKLY.getDbKey(),
            dev.lovelace.loveleaderboards.models.TimePeriod.TODAY.getDbKey()
        };

        synchronized (dbLock) {
            if (connection == null) return;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (String period : periods) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, playerName != null ? playerName : "Unknown");
                    ps.setString(3, category);
                    ps.setString(4, period);
                    ps.setLong(5, currentTime);
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to ensure player exists in DB: " + e.getMessage());
            }
        }
    }

    public void setScore(String entityType, String entityId, String entityName, String category, double exactScore) {
        if (entityType == null || entityId == null || category == null || Double.isNaN(exactScore) || Double.isInfinite(exactScore)) return;
        String sql = "INSERT INTO leaderboard_entries (entity_type, entity_id, entity_name, category, time_period, score, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT(entity_type, entity_id, category, time_period) DO UPDATE SET score = excluded.score, updated_at = excluded.updated_at";
        long currentTime = System.currentTimeMillis() / 1000;
        String[] periods = {
            dev.lovelace.loveleaderboards.models.TimePeriod.ALL_TIME.getDbKey(),
            dev.lovelace.loveleaderboards.models.TimePeriod.MONTHLY.getDbKey(),
            dev.lovelace.loveleaderboards.models.TimePeriod.WEEKLY.getDbKey(),
            dev.lovelace.loveleaderboards.models.TimePeriod.TODAY.getDbKey()
        };

        synchronized (dbLock) {
            if (connection == null) return;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (String period : periods) {
                    ps.setString(1, entityType);
                    ps.setString(2, entityId);
                    ps.setString(3, entityName != null ? entityName : "Unknown");
                    ps.setString(4, category);
                    ps.setString(5, period);
                    ps.setDouble(6, exactScore);
                    ps.setLong(7, currentTime);
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to set score in DB: " + e.getMessage());
            }
        }
    }

    public List<LeaderboardEntry> getTopByCategory(String category, String entityType, String timePeriod, int limit) {
        if (category == null || entityType == null || timePeriod == null) return new ArrayList<>();

        String query = """
            SELECT entity_id, entity_name, score, updated_at,
                   ROW_NUMBER() OVER (ORDER BY score DESC) as rank
            FROM leaderboard_entries
            WHERE category = ? AND entity_type = ? AND time_period = ?
            ORDER BY score DESC
            LIMIT ?
        """;

        List<LeaderboardEntry> entries = new ArrayList<>();

        synchronized (dbLock) {
            if (connection == null) return entries;
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                ps.setString(1, category);
                ps.setString(2, entityType);
                ps.setString(3, timePeriod);
                int fetchLimit = Math.max(1, limit + 25);
                ps.setInt(4, fetchLimit);

                try (ResultSet rs = ps.executeQuery()) {
                    int currentRank = 1;
                    while (rs.next() && entries.size() < limit) {
                        String eId = rs.getString("entity_id");
                        String eName = rs.getString("entity_name");

                        if ("clan".equalsIgnoreCase(entityType) && !isClanActive(eId, eName)) {
                            continue;
                        }
                        if ("player".equalsIgnoreCase(entityType) && dev.lovelace.loveleaderboards.integrations.VesuvioIntegration.isSuspect(eId)) {
                            continue;
                        }

                        entries.add(new LeaderboardEntry(
                            entityType,
                            eId,
                            eName != null ? eName : "Unknown",
                            currentRank++,
                            rs.getDouble("score"),
                            rs.getLong("updated_at")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("getTopByCategory failed: " + e.getMessage());
                return new ArrayList<>();
            }
        }

        return entries;
    }

    public boolean isClanActive(String entityId, String entityName) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("LoveClans")) {
            return true;
        }
        try {
            Class<?> apiClass = Class.forName("me.lovelace.loveclans.api.LoveClansAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            
            if (entityId != null) {
                UUID uuid = null;
                try {
                    uuid = UUID.fromString(entityId);
                } catch (IllegalArgumentException ignored) {
                    // legacy row keyed by name or tag: checked below
                }
                if (uuid != null) {
                    // A clan row is judged by its id alone: a new clan with the same name must not revive it.
                    Object clanOpt = apiClass.getMethod("getClanById", UUID.class).invoke(api, uuid);
                    return clanOpt instanceof Optional<?> opt && opt.isPresent();
                }
            }

            if (entityName != null && !entityName.isEmpty()) {
                try {
                    Object clanOptTag = apiClass.getMethod("getClanByTag", String.class).invoke(api, entityName);
                    if (clanOptTag instanceof Optional<?> opt && opt.isPresent()) {
                        return true;
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.FINE, "Failed to check clan by tag " + entityName, e);
                }

                try {
                    Object clansObj = apiClass.getMethod("getAllClans").invoke(api);
                    if (clansObj instanceof Collection<?> clans) {
                        for (Object clan : clans) {
                            String name = null;
                            String tag = null;
                            try {
                                Object n = clan.getClass().getMethod("name").invoke(clan);
                                if (n != null) name = n.toString();
                            } catch (Exception ignored) {}
                            try {
                                Object t = clan.getClass().getMethod("tag").invoke(clan);
                                if (t != null) tag = t.toString();
                            } catch (Exception ignored) {}
                            if (entityName.equalsIgnoreCase(name) || entityName.equalsIgnoreCase(tag)) {
                                return true;
                            }
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.FINE, "Failed to check clan in getAllClans", e);
                }
            }

            return false;
        } catch (Throwable t) {
            if (clanApiWarned.compareAndSet(false, true))
                plugin.getLogger().log(Level.WARNING, "LoveClans API call failed, clans stay in the top until it works", t);
            return true;
        }
    }

    /**
     * Ids of the clans that exist now, lower-case. Empty when LoveClans is absent or its API fails,
     * so the caller never deletes on a failed lookup. Reads LoveClans' maps: main thread only.
     */
    public Optional<java.util.Set<String>> liveClanIds() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("LoveClans")) return Optional.empty();
        try {
            Class<?> apiClass = Class.forName("me.lovelace.loveclans.api.LoveClansAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object clansObj = apiClass.getMethod("getAllClans").invoke(api);
            if (!(clansObj instanceof Collection<?> clans)) return Optional.empty();
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (Object clan : clans) {
                Object id = clan.getClass().getMethod("id").invoke(clan);
                if (id != null) ids.add(id.toString().toLowerCase(java.util.Locale.ROOT));
            }
            return Optional.of(ids);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Could not list live clans", t);
            return Optional.empty();
        }
    }

    /** Distinct entity ids stored for clans. */
    public List<String> clanEntityIds() {
        List<String> ids = new ArrayList<>();
        synchronized (dbLock) {
            if (connection == null) return ids;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT DISTINCT entity_id FROM leaderboard_entries WHERE entity_type = 'clan'");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString(1));
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to list clan rows: " + e.getMessage());
            }
        }
        return ids;
    }

    public int removeEntity(String entityType, String entityId) {
        if (entityType == null || entityId == null) return 0;
        String sql = "DELETE FROM leaderboard_entries WHERE entity_type = ? AND (entity_id = ? OR entity_name = ?)";
        synchronized (dbLock) {
            if (connection == null) return 0;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, entityType);
                ps.setString(2, entityId);
                ps.setString(3, entityId);
                return ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to remove entity from DB: " + e.getMessage());
                return 0;
            }
        }
    }

    public Optional<PlayerStats> getPlayerStats(UUID uuid, String category) {
        return getPlayerStats(uuid, category, dev.lovelace.loveleaderboards.models.TimePeriod.ALL_TIME.getDbKey());
    }

    public Optional<PlayerStats> getPlayerStats(UUID uuid, String category, String timePeriod) {
        if (uuid == null) return Optional.empty();
        return getEntityStats("player", uuid.toString(), category, timePeriod);
    }

    public Optional<PlayerStats> getEntityStats(String entityType, String entityId, String category, String timePeriod) {
        if (entityType == null || entityId == null || category == null || timePeriod == null) return Optional.empty();

        String query = """
            SELECT rank, score, entity_name FROM (
                SELECT entity_id, score, entity_name,
                       ROW_NUMBER() OVER (ORDER BY score DESC) as rank
                FROM leaderboard_entries
                WHERE category = ? AND time_period = ? AND entity_type = ?
            ) WHERE entity_id = ? OR entity_name = ?
        """;

        synchronized (dbLock) {
            if (connection == null) return Optional.empty();
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                ps.setString(1, category);
                ps.setString(2, timePeriod);
                ps.setString(3, entityType);
                ps.setString(4, entityId);
                ps.setString(5, entityId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        UUID id;
                        try {
                            id = UUID.fromString(entityId);
                        } catch (Exception e) {
                            id = UUID.nameUUIDFromBytes(entityId.getBytes());
                        }
                        String name = rs.getString("entity_name");
                        return Optional.of(new PlayerStats(
                            id,
                            name != null ? name : "Unknown",
                            rs.getInt("rank"),
                            rs.getDouble("score")
                        ));
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().warning("getEntityStats failed: " + e.getMessage());
            }
        }

        return Optional.empty();
    }

    private static final String BATCH_ENTITY = "__batch__";
    private static final String BATCH_CATEGORY = "__all__";

    public boolean isMonthRewarded(String monthKey) {
        synchronized (dbLock) {
            if (connection == null) return true; // fail closed: never pay without a ledger
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM leaderboard_monthly_rewards WHERE year_month = ? AND entity_id = ? AND category = ? AND rewards_given = 1")) {
                ps.setString(1, monthKey);
                ps.setString(2, BATCH_ENTITY);
                ps.setString(3, BATCH_CATEGORY);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("isMonthRewarded failed: " + e.getMessage());
                return true;
            }
        }
    }

    /**
     * Atomically claims the reward payout for a month. Returns true only for the single caller that
     * inserted the marker row, so a restart or a second trigger can never pay twice.
     */
    public boolean claimMonthRewards(String monthKey) {
        synchronized (dbLock) {
            if (connection == null) return false;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO leaderboard_monthly_rewards (year_month, entity_id, entity_name, category, rewards_given, given_at) " +
                    "VALUES (?, ?, ?, ?, 1, ?)")) {
                ps.setString(1, monthKey);
                ps.setString(2, BATCH_ENTITY);
                ps.setString(3, BATCH_ENTITY);
                ps.setString(4, BATCH_CATEGORY);
                ps.setLong(5, System.currentTimeMillis() / 1000);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().warning("claimMonthRewards failed: " + e.getMessage());
                return false;
            }
        }
    }

    /** Stores a reward for an offline winner; the unique key makes a repeated payout of the same month harmless. */
    public void addPendingReward(String entityId, String monthKey, String category, double score, List<String> commands) {
        synchronized (dbLock) {
            if (connection == null) return;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO leaderboard_pending_rewards (entity_id, year_month, category, score, commands, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, entityId);
                ps.setString(2, monthKey);
                ps.setString(3, category);
                ps.setDouble(4, score);
                ps.setString(5, String.join("\n", commands));
                ps.setLong(6, System.currentTimeMillis() / 1000);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("addPendingReward failed: " + e.getMessage());
            }
        }
    }

    /** One stored reward: the player-less command templates and the score they were earned with. */
    public record PendingReward(String category, String monthKey, double score, List<String> commands) {}

    /** Returns and deletes everything waiting for this player. */
    public List<PendingReward> takePendingRewards(String entityId) {
        List<PendingReward> result = new ArrayList<>();
        synchronized (dbLock) {
            if (connection == null) return result;
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT category, year_month, score, commands FROM leaderboard_pending_rewards WHERE entity_id = ? ORDER BY id");
                 PreparedStatement delete = connection.prepareStatement(
                         "DELETE FROM leaderboard_pending_rewards WHERE entity_id = ?")) {
                select.setString(1, entityId);
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        result.add(new PendingReward(rs.getString(1), rs.getString(2), rs.getDouble(3),
                                java.util.Arrays.asList(rs.getString(4).split("\n"))));
                    }
                }
                if (!result.isEmpty()) {
                    delete.setString(1, entityId);
                    delete.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("takePendingRewards failed: " + e.getMessage());
                result.clear();
            }
        }
        return result;
    }

    public void purgePendingRewards(int keepDays) {
        synchronized (dbLock) {
            if (connection == null) return;
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM leaderboard_pending_rewards WHERE created_at < ?")) {
                ps.setLong(1, System.currentTimeMillis() / 1000 - keepDays * 86400L);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("purgePendingRewards failed: " + e.getMessage());
            }
        }
    }

    public void resetMonthlyLeaderboards() {
        // The monthly bucket is stored under a date-derived key (e.g. "2026-08" from
        // TimePeriod.MONTHLY.getDbKey()), never the literal string "monthly" — matching that
        // literal here always matched zero rows, silently no-op'ing every scheduled/admin reset.
        String monthlyKey = dev.lovelace.loveleaderboards.models.TimePeriod.MONTHLY.getDbKey();
        synchronized (dbLock) {
            if (connection == null) return;
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE leaderboard_entries SET score = 0.0 WHERE time_period = ?")) {
                ps.setString(1, monthlyKey);
                int resetCount = ps.executeUpdate();
                plugin.getLogger().info("Reset monthly leaderboards scores for " + resetCount + " entries.");
            } catch (SQLException e) {
                plugin.getLogger().severe("resetMonthly failed: " + e.getMessage());
            }
        }
    }

    public void close() {
        synchronized (dbLock) {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing database connection: " + e.getMessage());
            }
        }
    }
}
