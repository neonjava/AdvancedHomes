package me.neonjava.advancedhomes.storage;

import me.neonjava.advancedhomes.AdvancedHomesPlugin;
import me.neonjava.advancedhomes.model.Home;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {
    private final AdvancedHomesPlugin plugin;
    private Connection connection;

    public DatabaseManager(AdvancedHomesPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void initialize() {
        try {
            if (plugin.getConfigManager().getStorageType().equalsIgnoreCase("mysql")) {
                setupMysql();
            } else {
                setupSqlite();
            }
            createTable();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
        }
    }

    private void setupSqlite() throws Exception {
        Class.forName("org.sqlite.JDBC");
        File file = new File(plugin.getDataFolder(), "homes.db");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }
        String url = "jdbc:sqlite:" + file.getAbsolutePath();
        connection = DriverManager.getConnection(url);
    }

    private void setupMysql() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String host = plugin.getConfigManager().getMysqlHost();
        int port = plugin.getConfigManager().getMysqlPort();
        String database = plugin.getConfigManager().getMysqlDatabase();
        String username = plugin.getConfigManager().getMysqlUsername();
        String password = plugin.getConfigManager().getMysqlPassword();

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true";
        connection = DriverManager.getConnection(url, username, password);
    }

    private void createTable() throws SQLException {
        String query;
        if (plugin.getConfigManager().getStorageType().equalsIgnoreCase("mysql")) {
            query = "CREATE TABLE IF NOT EXISTS player_homes (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "home_name VARCHAR(100) NOT NULL," +
                    "world_name VARCHAR(100) NOT NULL," +
                    "x DOUBLE NOT NULL," +
                    "y DOUBLE NOT NULL," +
                    "z DOUBLE NOT NULL," +
                    "yaw FLOAT NOT NULL," +
                    "pitch FLOAT NOT NULL," +
                    "icon_material VARCHAR(100) NOT NULL," +
                    "creation_date BIGINT NOT NULL," +
                    "remaining_teleports INT DEFAULT 3," +
                    "UNIQUE(player_uuid, home_name)" +
                    ");";
        } else {
            query = "CREATE TABLE IF NOT EXISTS player_homes (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "home_name VARCHAR(100) NOT NULL," +
                    "world_name VARCHAR(100) NOT NULL," +
                    "x DOUBLE NOT NULL," +
                    "y DOUBLE NOT NULL," +
                    "z DOUBLE NOT NULL," +
                    "yaw FLOAT NOT NULL," +
                    "pitch FLOAT NOT NULL," +
                    "icon_material VARCHAR(100) NOT NULL," +
                    "creation_date BIGINT NOT NULL," +
                    "remaining_teleports INTEGER DEFAULT 3," +
                    "UNIQUE(player_uuid, home_name)" +
                    ");";
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(query);
            
            // Alter existing tables to add remaining_teleports column if it doesn't exist
            try {
                if (plugin.getConfigManager().getStorageType().equalsIgnoreCase("mysql")) {
                    statement.execute("ALTER TABLE player_homes ADD COLUMN remaining_teleports INT DEFAULT 3;");
                } else {
                    statement.execute("ALTER TABLE player_homes ADD COLUMN remaining_teleports INTEGER DEFAULT 3;");
                }
            } catch (SQLException ignored) {}
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to close database connection", e);
        }
    }

    private synchronized Connection getConnection() throws SQLException {
        boolean needsReconnect = false;

        if (connection == null || connection.isClosed()) {
            needsReconnect = true;
        } else {
            // Validate the connection is actually alive (handles MySQL wait_timeout + SQLite locks)
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SELECT 1");
            } catch (SQLException e) {
                plugin.getLogger().warning("Database connection stale, reconnecting... (" + e.getMessage() + ")");
                needsReconnect = true;
                try { connection.close(); } catch (SQLException ignored) {}
            }
        }

        if (needsReconnect) {
            if (plugin.getConfigManager().getStorageType().equalsIgnoreCase("mysql")) {
                try {
                    setupMysql();
                    plugin.getLogger().info("MySQL connection re-established.");
                } catch (Exception e) {
                    throw new SQLException("Failed to reconnect to MySQL", e);
                }
            } else {
                try {
                    setupSqlite();
                    plugin.getLogger().info("SQLite connection re-established.");
                } catch (Exception e) {
                    throw new SQLException("Failed to reconnect to SQLite", e);
                }
            }
        }
        return connection;
    }

    /**
     * Loads homes from database. Returns null on failure (so callers can distinguish
     * "no homes" from "load failed") to prevent caching an empty list over real data.
     */
    public synchronized List<Home> loadHomes(UUID playerUuid) {
        List<Home> homes = new ArrayList<>();
        String query = "SELECT * FROM player_homes WHERE player_uuid = ?";
        try (PreparedStatement statement = getConnection().prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Home home = new Home(
                            playerUuid,
                            resultSet.getString("home_name"),
                            resultSet.getString("world_name"),
                            resultSet.getDouble("x"),
                            resultSet.getDouble("y"),
                            resultSet.getDouble("z"),
                            resultSet.getFloat("yaw"),
                            resultSet.getFloat("pitch"),
                            resultSet.getString("icon_material"),
                            resultSet.getLong("creation_date"),
                            resultSet.getInt("remaining_teleports")
                    );
                    homes.add(home);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load homes for player " + playerUuid, e);
            return null; // Signal failure — do NOT return empty list
        }
        return homes;
    }

    public synchronized boolean saveHome(Home home) {
        String query = "INSERT OR REPLACE INTO player_homes (player_uuid, home_name, world_name, x, y, z, yaw, pitch, icon_material, creation_date, remaining_teleports) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        if (plugin.getConfigManager().getStorageType().equalsIgnoreCase("mysql")) {
            query = "INSERT INTO player_homes (player_uuid, home_name, world_name, x, y, z, yaw, pitch, icon_material, creation_date, remaining_teleports) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                    "world_name = VALUES(world_name), x = VALUES(x), y = VALUES(y), z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch), icon_material = VALUES(icon_material), remaining_teleports = VALUES(remaining_teleports)";
        }
        try (PreparedStatement statement = getConnection().prepareStatement(query)) {
            statement.setString(1, home.getPlayerUuid().toString());
            statement.setString(2, home.getName());
            statement.setString(3, home.getWorldName());
            statement.setDouble(4, home.getX());
            statement.setDouble(5, home.getY());
            statement.setDouble(6, home.getZ());
            statement.setFloat(7, home.getYaw());
            statement.setFloat(8, home.getPitch());
            statement.setString(9, home.getIconMaterial());
            statement.setLong(10, home.getCreationDate());
            statement.setInt(11, home.getRemainingTeleports());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save home " + home.getName() + " for player " + home.getPlayerUuid(), e);
            return false;
        }
    }

    public synchronized boolean deleteHome(UUID playerUuid, String homeName) {
        String query = "DELETE FROM player_homes WHERE player_uuid = ? AND home_name = ?";
        try (PreparedStatement statement = getConnection().prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, homeName);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete home " + homeName + " for player " + playerUuid, e);
            return false;
        }
    }

    public synchronized boolean renameHome(UUID playerUuid, String oldName, String newName) {
        String query = "UPDATE player_homes SET home_name = ? WHERE player_uuid = ? AND home_name = ?";
        try (PreparedStatement statement = getConnection().prepareStatement(query)) {
            statement.setString(1, newName);
            statement.setString(2, playerUuid.toString());
            statement.setString(3, oldName);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to rename home " + oldName + " to " + newName + " for player " + playerUuid, e);
            return false;
        }
    }

    public synchronized boolean updateIcon(UUID playerUuid, String homeName, String newIcon) {
        String query = "UPDATE player_homes SET icon_material = ? WHERE player_uuid = ? AND home_name = ?";
        try (PreparedStatement statement = getConnection().prepareStatement(query)) {
            statement.setString(1, newIcon);
            statement.setString(2, playerUuid.toString());
            statement.setString(3, homeName);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update icon for home " + homeName + " for player " + playerUuid, e);
            return false;
        }
    }

    public synchronized boolean updateRemainingTeleports(UUID playerUuid, String homeName, int remaining) {
        String query = "UPDATE player_homes SET remaining_teleports = ? WHERE player_uuid = ? AND home_name = ?";
        try (PreparedStatement statement = getConnection().prepareStatement(query)) {
            statement.setInt(1, remaining);
            statement.setString(2, playerUuid.toString());
            statement.setString(3, homeName);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update remaining teleports for home " + homeName + " for player " + playerUuid, e);
            return false;
        }
    }
}
