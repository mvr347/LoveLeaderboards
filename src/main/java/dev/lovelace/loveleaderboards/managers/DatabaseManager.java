package dev.lovelace.loveleaderboards.managers;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.LeaderboardEntry;
import dev.lovelace.loveleaderboards.models.LeaderboardUpdate;
import dev.lovelace.loveleaderboards.models.PlayerStats;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DatabaseManager {
    private final LoveLeaderboards plugin;
    private Connection connection;

    public DatabaseManager(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
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
        if (batch.isEmpty()) return;

        String upsertSql = "INSERT INTO leaderboard_entries (entity_type, entity_id, entity_name, category, time_period, score, updated_at) " +
                           "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                           "ON CONFLICT(entity_type, entity_id, category, time_period) DO UPDATE SET score = score + excluded.score, updated_at = excluded.updated_at";

        try (PreparedStatement ps = connection.prepareStatement(upsertSql)) {
            connection.setAutoCommit(false);
            long currentTime = System.currentTimeMillis() / 1000;
            
            String allTimeKey = dev.lovelace.loveleaderboards.models.TimePeriod.ALL_TIME.getDbKey();
            String monthlyKey = dev.lovelace.loveleaderboards.models.TimePeriod.MONTHLY.getDbKey();
            String weeklyKey = dev.lovelace.loveleaderboards.models.TimePeriod.WEEKLY.getDbKey();
            String todayKey = dev.lovelace.loveleaderboards.models.TimePeriod.TODAY.getDbKey();

            String[] periods = {allTimeKey, monthlyKey, weeklyKey, todayKey};

            for (LeaderboardUpdate update : batch) {
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
            try { connection.rollback(); } catch (SQLException ignored) {}
        }
    }

    public void ensurePlayerExists(UUID uuid, String playerName, String category) {
        String sql = "INSERT OR IGNORE INTO leaderboard_entries (entity_type, entity_id, entity_name, category, time_period, score, updated_at) " +
                     "VALUES ('player', ?, ?, ?, ?, 0.0, ?)";
        long currentTime = System.currentTimeMillis() / 1000;
        String[] periods = {
            dev.lovelace.loveleaderboards.models.TimePeriod.ALL_TIME.getDbKey(),
            dev.lovelace.loveleaderboards.models.TimePeriod.MONTHLY.getDbKey(),
            dev.lovelace.loveleaderboards.models.TimePeriod.WEEKLY.getDbKey(),
            dev.lovelace.loveleaderboards.models.TimePeriod.TODAY.getDbKey()
        };

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (String period : periods) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
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

    public void setScore(String entityType, String entityId, String entityName, String category, double exactScore) {
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

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (String period : periods) {
                ps.setString(1, entityType);
                ps.setString(2, entityId);
                ps.setString(3, entityName);
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

    public List<LeaderboardEntry> getTopByCategory(String category, String entityType, String timePeriod, int limit) {
        String query = """
            SELECT entity_id, entity_name, score, updated_at,
                   ROW_NUMBER() OVER (ORDER BY score DESC) as rank
            FROM leaderboard_entries
            WHERE category = ? AND entity_type = ? AND time_period = ?
            ORDER BY score DESC
            LIMIT ?
        """;

        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, category);
            ps.setString(2, entityType);
            ps.setString(3, timePeriod);
            ps.setInt(4, limit);

            ResultSet rs = ps.executeQuery();
            List<LeaderboardEntry> entries = new ArrayList<>();

            while (rs.next()) {
                entries.add(new LeaderboardEntry(
                    entityType,
                    rs.getString("entity_id"),
                    rs.getString("entity_name"),
                    rs.getInt("rank"),
                    rs.getDouble("score"),
                    rs.getLong("updated_at")
                ));
            }

            return entries;

        } catch (SQLException e) {
            plugin.getLogger().warning("getTopByCategory failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public Optional<PlayerStats> getPlayerStats(UUID uuid, String category) {
        return getPlayerStats(uuid, category, dev.lovelace.loveleaderboards.models.TimePeriod.ALL_TIME.getDbKey());
    }

    public Optional<PlayerStats> getPlayerStats(UUID uuid, String category, String timePeriod) {
        String query = """
            SELECT rank, score, entity_name FROM (
                SELECT entity_id, score, entity_name,
                       ROW_NUMBER() OVER (ORDER BY score DESC) as rank
                FROM leaderboard_entries
                WHERE category = ? AND time_period = ? AND entity_type = 'player'
            ) WHERE entity_id = ?
        """;


        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, category);
            ps.setString(2, timePeriod);
            ps.setString(3, uuid.toString());

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(new PlayerStats(
                    uuid,
                    rs.getString("entity_name"),
                    rs.getInt("rank"),
                    rs.getDouble("score")
                ));
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("getPlayerStats failed: " + e.getMessage());
        }

        return Optional.empty();
    }

    public void resetMonthlyLeaderboards() {
        // We delete data that is older than 1 month (i.e. 2 months old)
        // so that last month's data remains available for rewards and history.
        LocalDate twoMonthsAgo = LocalDate.now().minusMonths(2);
        String oldMonth = String.format("%04d-%02d", twoMonthsAgo.getYear(), twoMonthsAgo.getMonthValue());

        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM leaderboard_entries WHERE time_period = ?")) {
            ps.setString(1, oldMonth);
            ps.executeUpdate();
            plugin.getLogger().info("Cleaned up old monthly leaderboards for: " + oldMonth);
        } catch (SQLException e) {
            plugin.getLogger().severe("resetMonthly failed: " + e.getMessage());
        }
    }

    private String getCurrentMonthString() {
        LocalDate now = LocalDate.now();
        return String.format("%04d-%02d", now.getYear(), now.getMonthValue());
    }

    private String getLastMonthString() {
        LocalDate last = LocalDate.now().minusMonths(1);
        return String.format("%04d-%02d", last.getYear(), last.getMonthValue());
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {}
    }
}
