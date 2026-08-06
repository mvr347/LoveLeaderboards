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
                ps.setInt(4, Math.max(1, limit));

                try (ResultSet rs = ps.executeQuery()) {
                    int currentRank = 1;
                    while (rs.next()) {
                        String eId = rs.getString("entity_id");
                        String eName = rs.getString("entity_name");

                        if ("clan".equalsIgnoreCase(entityType) && !isClanActive(eId, eName)) {
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
                try {
                    UUID uuid = UUID.fromString(entityId);
                    Object clanOpt = apiClass.getMethod("getClanById", UUID.class).invoke(api, uuid);
                    if (clanOpt instanceof Optional<?> opt && opt.isPresent()) {
                        return true;
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.FINE, "Failed to check clan by UUID " + entityId, e);
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
            plugin.getLogger().log(Level.FINE, "LoveClans API call failed, defaulting to active clan", t);
            return true;
        }
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

    public void resetMonthlyLeaderboards() {
        synchronized (dbLock) {
            if (connection == null) return;
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE leaderboard_entries SET score = 0.0 WHERE time_period = 'monthly'")) {
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
