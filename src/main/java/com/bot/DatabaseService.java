package com.bot;

import com.bot.model.UserSetting;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseService {
    private static final String URL = "jdbc:sqlite:" + Config.DB_NAME;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            initDb();
        } catch (ClassNotFoundException e) {
            System.err.println("CRITICAL: SQLite JDBC Driver not found!");
            e.printStackTrace();
            throw new RuntimeException("Database driver missing", e);
        } catch (SQLException e) {
            System.err.println("CRITICAL: Failed to initialize database!");
            e.printStackTrace();
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private static void initDb() throws SQLException {
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement()) {
            
            // Users table
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "user_id INTEGER PRIMARY KEY," +
                    "region TEXT," +
                    "queue TEXT," +
                    "mode TEXT DEFAULT 'normal'," +
                    "notify_before INTEGER DEFAULT 5," +
                    "notify_return_before INTEGER DEFAULT 0," +
                    "notify_outage INTEGER DEFAULT 1," +
                    "notify_return INTEGER DEFAULT 1," +
                    "notify_changes INTEGER DEFAULT 1," +
                    "display_mode TEXT DEFAULT 'blackout'," +
                    "is_active INTEGER DEFAULT 1" +
                    ")");

            // Daily stats table
            stmt.execute("CREATE TABLE IF NOT EXISTS daily_stats (" +
                    "date TEXT," +
                    "region TEXT," +
                    "queue TEXT," +
                    "off_hours REAL," +
                    "PRIMARY KEY (date, region, queue)" +
                    ")");

            // System config table
            stmt.execute("CREATE TABLE IF NOT EXISTS system_config (" +
                    "key TEXT PRIMARY KEY," +
                    "value TEXT" +
                    ")");
            
            stmt.execute("INSERT OR IGNORE INTO system_config (key, value) VALUES ('hoe_site_enabled', '1')");

            // Support tickets table
            stmt.execute("CREATE TABLE IF NOT EXISTS support_tickets (" +
                    "ticket_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "user_id INTEGER NOT NULL," +
                    "username TEXT," +
                    "full_name TEXT," +
                    "status TEXT DEFAULT 'unread'," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "last_message_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // Migration: Add full_name if it doesn't exist
            try {
                stmt.execute("ALTER TABLE support_tickets ADD COLUMN full_name TEXT");
            } catch (SQLException e) {
                // Column already exists, ignore
            }



            // Support messages table
            stmt.execute("CREATE TABLE IF NOT EXISTS support_messages (" +
                    "message_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "ticket_id INTEGER NOT NULL," +
                    "from_user TEXT NOT NULL," +
                    "message_text TEXT NOT NULL," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // Group subscriptions table
            stmt.execute("CREATE TABLE IF NOT EXISTS group_subscriptions (" +
                    "chat_id INTEGER PRIMARY KEY," +
                    "chat_title TEXT," +
                    "chat_type TEXT," +
                    "region TEXT NOT NULL," +
                    "queue TEXT NOT NULL," +
                    "display_mode TEXT DEFAULT 'blackout'," +
                    "notify_outage INTEGER DEFAULT 1," +
                    "notify_return INTEGER DEFAULT 1," +
                    "notify_changes INTEGER DEFAULT 1," +
                    "notify_before INTEGER DEFAULT 5," +
                    "notify_return_before INTEGER DEFAULT 0," +
                    "notify_morning INTEGER DEFAULT 1," +
                    "added_by INTEGER," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")");
        }
    }

    public static void registerUser(long userId) throws SQLException {
        String sql = "INSERT OR IGNORE INTO users (user_id) VALUES (?)";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.executeUpdate();
        }
    }

    public static void saveUser(long userId, String region, String queue) throws SQLException {

        String sql = "INSERT INTO users (user_id, region, queue) VALUES (?, ?, ?) " +
                     "ON CONFLICT(user_id) DO UPDATE SET region=excluded.region, queue=excluded.queue, is_active=1";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setString(2, region);
            pstmt.setString(3, queue);
            pstmt.executeUpdate();
        }
    }

    public static UserSetting getUserSettings(long userId) throws SQLException {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    UserSetting settings = new UserSetting();
                    settings.setUserId(rs.getLong("user_id"));
                    settings.setRegion(rs.getString("region"));
                    settings.setQueue(rs.getString("queue"));
                    settings.setMode(rs.getString("mode"));
                    settings.setNotifyBefore(rs.getInt("notify_before"));
                    settings.setNotifyReturnBefore(rs.getInt("notify_return_before"));
                    settings.setNotifyOutage(rs.getInt("notify_outage") == 1);
                    settings.setNotifyReturn(rs.getInt("notify_return") == 1);
                    settings.setNotifyChanges(rs.getInt("notify_changes") == 1);
                    settings.setDisplayMode(rs.getString("display_mode"));
                    settings.setActive(rs.getInt("is_active") == 1);
                    return settings;
                }
            }
        }
        return null;
    }

    public static void updateUserSetting(long userId, String key, Object value) throws SQLException {
        String sql = "UPDATE users SET " + key + " = ? WHERE user_id = ?";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (value instanceof Integer) pstmt.setInt(1, (Integer) value);
            else if (value instanceof String) pstmt.setString(1, (String) value);
            else if (value instanceof Boolean) pstmt.setInt(1, (Boolean) value ? 1 : 0);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        }
    }

    public static List<long[]> getAllSubs() throws SQLException {
        List<long[]> subs = new ArrayList<>();
        // In this simplified version, we return unique region/queue pairs
        // but for a real bot we might need more logic. 
        // Let's just follow the Python logic: get unique region, queue from users
        String sql = "SELECT DISTINCT region, queue FROM users";
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            // Wait, this returns strings. The Python version returns (region, queue) strings.
            // I'll return List<String[]> instead.
        }
        return null; // Fixed below
    }

    public static List<String[]> getAllSubscriptions() throws SQLException {
        List<String[]> subs = new ArrayList<>();
        String sql = "SELECT DISTINCT region, queue FROM users WHERE region IS NOT NULL AND queue IS NOT NULL";
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                subs.add(new String[]{rs.getString("region"), rs.getString("queue")});
            }
        }
        return subs;
    }

    public static List<Long> getUsersByQueue(String region, String queue) throws SQLException {
        List<Long> users = new ArrayList<>();
        String sql = "SELECT user_id FROM users WHERE region = ? AND queue = ? AND is_active = 1";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, region);
            pstmt.setString(2, queue);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    users.add(rs.getLong("user_id"));
                }
            }
        }
        return users;
    }

    public static void saveStats(String region, String queue, String date, double offHours) throws SQLException {
        String sql = "INSERT INTO daily_stats (date, region, queue, off_hours) VALUES (?, ?, ?, ?) " +
                     "ON CONFLICT(date, region, queue) DO UPDATE SET off_hours=excluded.off_hours";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, date);
            pstmt.setString(2, region);
            pstmt.setString(3, queue);
            pstmt.setDouble(4, offHours);
            pstmt.executeUpdate();
        }
    }

    public static List<String[]> getStatsData(String region, String queue) throws SQLException {
        List<String[]> stats = new ArrayList<>();
        String sql = "SELECT date, off_hours FROM daily_stats WHERE region = ? AND queue = ? ORDER BY date DESC LIMIT 7";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, region);
            pstmt.setString(2, queue);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    stats.add(new String[]{rs.getString("date"), String.valueOf(rs.getDouble("off_hours"))});
                }
            }
        }
        return stats;
    }

    public static int getTotalUsersCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public static int getUnsubscribedUsersCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE region IS NULL OR queue IS NULL";
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }


    public static List<Long> getAllUserIds() throws SQLException {
        List<Long> ids = new ArrayList<>();
        String sql = "SELECT user_id FROM users WHERE is_active = 1";
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    public static int getGroupSubsCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM group_subscriptions";
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public static void createOrUpdateTicket(long userId, String username, String fullName) throws SQLException {
        String checkSql = "SELECT ticket_id FROM support_tickets WHERE user_id = ? AND status != 'closed'";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setLong(1, userId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    // Update existing
                    String updateSql = "UPDATE support_tickets SET last_message_at = CURRENT_TIMESTAMP, status = 'unread', full_name = ? WHERE ticket_id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setString(1, fullName);
                        updateStmt.setInt(2, rs.getInt(1));
                        updateStmt.executeUpdate();
                    }
                } else {
                    // Create new
                    String insertSql = "INSERT INTO support_tickets (user_id, username, full_name, status) VALUES (?, ?, ?, 'unread')";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setLong(1, userId);
                        insertStmt.setString(2, username);
                        insertStmt.setString(3, fullName);
                        insertStmt.executeUpdate();
                    }
                }
            }
        }
    }

    public static List<String[]> getActiveTickets() throws SQLException {
        List<String[]> tickets = new ArrayList<>();
        String sql = "SELECT ticket_id, user_id, username, full_name, status FROM support_tickets WHERE status != 'closed' ORDER BY last_message_at DESC";
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                tickets.add(new String[]{
                        String.valueOf(rs.getInt("ticket_id")),
                        String.valueOf(rs.getLong("user_id")),
                        rs.getString("username"),
                        rs.getString("status"),
                        rs.getString("full_name")
                });
            }
        }
        return tickets;
    }


    public static void closeTicket(int ticketId) throws SQLException {
        String sql = "UPDATE support_tickets SET status = 'closed' WHERE ticket_id = ?";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, ticketId);
            pstmt.executeUpdate();
        }
    }

    public static void closeTicketByUserId(long userId) throws SQLException {
        String sql = "UPDATE support_tickets SET status = 'closed' WHERE user_id = ? AND status != 'closed'";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.executeUpdate();
        }
    }
}
