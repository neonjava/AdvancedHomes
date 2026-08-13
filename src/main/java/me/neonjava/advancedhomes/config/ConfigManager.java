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
        return config.getInt("max-homes", 90);
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
        return config.getBoolean("prefer-inventory-ui", false);
    }

    public boolean isTelemetryEnabled() {
        return config.getBoolean("telemetry", true);
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

    public int getDefaultHomeLimit() {
        return config.getInt("default-home-limit", 3);
    }

    public int getExpiredRankTeleportChances() {
        return config.getInt("expired-rank-teleport-chances", 3);
    }

    public boolean isUseUnicodeFont() {
        return config.getBoolean("use-unicode-font", true);
    }

    /**
     * Map of Rank Name -> Maximum Home Slots (e.g. VIP -> 5, MVP -> 10)
     */
    public java.util.Map<String, Integer> getRanks() {
        java.util.Map<String, Integer> ranks = new java.util.LinkedHashMap<>();
        if (config.isConfigurationSection("ranks")) {
            for (String key : config.getConfigurationSection("ranks").getKeys(false)) {
                ranks.put(key, config.getInt("ranks." + key, 5));
            }
        }
        return ranks;
    }

    /**
     * Returns the required rank name and limit for a given slot index (0-indexed)
     */
    public RankRequirement getRankRequirementForSlot(int slotIndex) {
        int slotNumber = slotIndex + 1;
        java.util.Map<String, Integer> ranks = getRanks();
        if (ranks.isEmpty()) {
            return new RankRequirement("VIP", 5);
        }

        for (java.util.Map.Entry<String, Integer> entry : ranks.entrySet()) {
            if (slotNumber <= entry.getValue()) {
                return new RankRequirement(entry.getKey(), entry.getValue());
            }
        }

        // Return highest rank
        String lastRank = "VIP";
        int lastMax = 90;
        for (java.util.Map.Entry<String, Integer> entry : ranks.entrySet()) {
            lastRank = entry.getKey();
            lastMax = entry.getValue();
        }
        return new RankRequirement(lastRank, lastMax);
    }

    public static class RankRequirement {
        private final String rankName;
        private final int slots;

        public RankRequirement(String rankName, int slots) {
            this.rankName = rankName;
            this.slots = slots;
        }

        public String getRankName() {
            return rankName;
        }

        public int getSlots() {
            return slots;
        }
    }

    public String getMessageNeedRank() {
        return config.getString("messages.need-rank", "&cYou need <rank> rank to access this home slot!");
    }

    public String getMessageBuyRank() {
        return config.getString("messages.buy-rank", "&ePurchase rank upgrades at &b&nneonjava.in&e store section!");
    }

    public String getMessageLockedTooltip() {
        return config.getString("messages.locked-tooltip", "&cYou need &e<rank> &crank to unlock this slot!\n&7Purchase at &b&nneonjava.in");
    }

    public String getMessageBuyRanksWarning() {
        return config.getString("messages.buy-ranks-warning", "&cThis home slot requires &e<rank> &crank (&e<slots> &chomes). Purchase at &b&nneonjava.in&c!");
    }

    public String getMessageTeleportCountdown() {
        return config.getString("messages.teleport-countdown", "&7Teleporting to &f<home> &7in &b<seconds> &7seconds. Do not move!");
    }

    public String getMessageTeleportSuccess() {
        return config.getString("messages.teleport-success", "&aTeleported to &f<home>&a!");
    }

    public String getMessageTeleportCancelled() {
        return config.getString("messages.teleport-cancelled", "&cTeleportation cancelled due to movement.");
    }

    public String getMessageWorldNotLoaded() {
        return config.getString("messages.world-not-loaded", "&cThe world for this home is not loaded.");
    }

    public String getMessageHomeExpired() {
        return config.getString("messages.home-expired", "&cYour rank has expired! You have &e<remaining> &cchances left to teleport to this home.");
    }

    public String getMessageHomeNoChances() {
        return config.getString("messages.home-no-chances", "&cThis home is locked! You have used all grace teleports. Re-purchase &e<rank> &crank at &b&nneonjava.in &cto unlock.");
    }

    public String getMessageNeedRankTeleport() {
        return config.getString("messages.need-rank-teleport", "&cYou need &e<rank> &crank to access this home! Visit &b&nneonjava.in &cto unlock.");
    }
}
