package me.neonjava.advancedhomes;

import me.neonjava.advancedhomes.config.ConfigManager;
import me.neonjava.advancedhomes.listener.PlayerListener;
import me.neonjava.advancedhomes.manager.ChatInputManager;
import me.neonjava.advancedhomes.manager.HomeManager;
import me.neonjava.advancedhomes.storage.DatabaseManager;
import me.neonjava.advancedhomes.ui.DialogHomesUI;
import me.neonjava.advancedhomes.ui.InventoryHomesUI;
import me.neonjava.advancedhomes.ui.HomesUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class AdvancedHomesPlugin extends JavaPlugin implements CommandExecutor {
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private HomeManager homeManager;
    private ChatInputManager chatInputManager;
    private HomesUI dialogHomesUI;
    private HomesUI inventoryHomesUI;

    @Override
    public void onEnable() {
        // Initialize managers
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        homeManager = new HomeManager(this);
        chatInputManager = new ChatInputManager();

        // UI instances
        inventoryHomesUI = new InventoryHomesUI(this);
        
        boolean dialogueApiAvailable = false;
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            dialogueApiAvailable = true;
        } catch (ClassNotFoundException ignored) {}

        if (dialogueApiAvailable) {
            dialogHomesUI = new DialogHomesUI(this);
            getLogger().info("Paper Dialog API detected and enabled!");
        } else {
            getLogger().info("Paper Dialog API not found. Defaulting to Inventory UI.");
        }

        // Register Command and Listener
        getCommand("devhome").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new me.neonjava.advancedhomes.ui.AnvilInput(), this);

        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            Bukkit.getPluginManager().registerEvents(new me.neonjava.advancedhomes.listener.PaperChatListener(this), this);
            getLogger().info("Paper AsyncChatEvent listener registered successfully!");
        } catch (ClassNotFoundException ignored) {}

        getLogger().info("AdvancedHomes Plugin successfully enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("AdvancedHomes Plugin disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use the homes menu!");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("homes.use")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to open the homes menu!");
            return true;
        }

        HomesUI ui = getUI(player);
        if (ui == null) {
            player.sendMessage(ChatColor.RED + "An error occurred while loading the homes interface.");
            return true;
        }

        ui.openMainHomesMenu(player);
        return true;
    }

    public HomesUI getUI(Player player) {
        if (configManager.isPreferInventoryUI()) {
            getLogger().info("[UI Choice] Player " + player.getName() + " -> Chest GUI (Config prefer-inventory-ui is true)");
            return inventoryHomesUI;
        }

        if (dialogHomesUI == null) {
            getLogger().info("[UI Choice] Player " + player.getName() + " -> Chest GUI (Dialog API is not supported on this server version)");
            return inventoryHomesUI;
        }

        if (configManager.isEnableBedrockSupport() && isBedrockPlayer(player)) {
            getLogger().info("[UI Choice] Player " + player.getName() + " -> Chest GUI (Bedrock player detected)");
            return inventoryHomesUI;
        }

        // ViaBackwards cannot properly translate Dialog API packets for clients < 1.21.7 (protocol < 772).
        // Those clients get a mangled UI (acacia buttons), so we fall back to Inventory GUI for them.
        int protocol = getPlayerProtocolVersion(player);
        if (protocol > 0 && protocol < 772) {
            getLogger().info("[UI Choice] Player " + player.getName() + " -> Chest GUI (Client protocol " + protocol + " < 772, ViaBackwards cannot translate Dialog API)");
            return inventoryHomesUI;
        }

        getLogger().info("[UI Choice] Player " + player.getName() + " -> Dialog GUI (Protocol: " + protocol + ")");
        return dialogHomesUI;
    }

    private int getPlayerProtocolVersion(Player player) {
        // ViaVersion is the most reliable source when it is installed
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
                Object viaApi = Class.forName("com.viaversion.viaversion.api.Via").getMethod("getAPI").invoke(null);
                if (viaApi != null) {
                    Object version = viaApi.getClass().getMethod("getPlayerVersion", java.util.UUID.class).invoke(viaApi, player.getUniqueId());
                    if (version instanceof Number n && n.intValue() > 0) return n.intValue();
                }
            }
        } catch (Throwable ignored) {}
        // Fallback: Paper NetworkClient
        try {
            if (player instanceof com.destroystokyo.paper.network.NetworkClient nc) {
                int v = nc.getProtocolVersion();
                if (v > 0) return v;
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private boolean isBedrockPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        if (name != null && name.startsWith(".")) {
            return true;
        }
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")) {
                if (org.geysermc.geyser.api.GeyserApi.api() != null && org.geysermc.geyser.api.GeyserApi.api().isBedrockPlayer(uuid)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Floodgate")) {
                if (org.geysermc.floodgate.api.FloodgateApi.getInstance() != null && org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(uuid)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public HomeManager getHomeManager() {
        return homeManager;
    }

    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }

    public HomesUI getInventoryHomesUI() {
        return inventoryHomesUI;
    }
}
