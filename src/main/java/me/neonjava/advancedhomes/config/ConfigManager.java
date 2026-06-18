package me.neonjava.advancedhomes.config;

import me.neonjava.advancedhomes.AdvancedHomesPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private final AdvancedHomesPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(AdvancedHomesPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public int getMaxHomes() {
        return config.getInt("max-homes", 10);
    }

    public int getTeleportDelay() {
        return config.getInt("teleport-delay", 3);
    }

    public boolean isCancelOnMove() {
        return config.getBoolean("cancel-on-move", true);
    }

    public boolean isAllowColoredNames() {
        return config.getBoolean("allow-colored-names", true);
    }

    public boolean isEnableBedrockSupport() {
        return config.getBoolean("enable-bedrock-support", true);
    }

    public boolean isPreferInventoryUI() {
        return config.getBoolean("prefer-inventory-ui", true);
    }

    public String getStorageType() {
        return config.getString("storage-type", "sqlite");
    }

    public String getMysqlHost() {
        return config.getString("mysql.host", "localhost");
    }

    public int getMysqlPort() {
        return config.getInt("mysql.port", 3306);
    }

    public String getMysqlDatabase() {
        return config.getString("mysql.database", "minecraft");
    }

    public String getMysqlUsername() {
        return config.getString("mysql.username", "root");
    }

    public String getMysqlPassword() {
        return config.getString("mysql.password", "");
    }
}
