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
            return inventoryHomesUI;
        }

        if (dialogHomesUI == null) {
            return inventoryHomesUI;
        }

        if (configManager.isEnableBedrockSupport() && isBedrockPlayer(player)) {
            return inventoryHomesUI;
        }

        return dialogHomesUI;
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
