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
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import me.neonjava.advancedhomes.model.Home;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class AdvancedHomesPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private HomeManager homeManager;
    private ChatInputManager chatInputManager;
    private me.neonjava.advancedhomes.util.ItemsDatabase itemsDatabase;
    private HomesUI inventoryHomesUI;
    private HomesUI dialogHomesUI;

    @Override
    public void onEnable() {
        // Initialize managers
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        homeManager = new HomeManager(this);
        chatInputManager = new ChatInputManager();
        itemsDatabase = new me.neonjava.advancedhomes.util.ItemsDatabase(this);

        // UI instances
        inventoryHomesUI = new InventoryHomesUI(this);

        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            dialogHomesUI = new DialogHomesUI(this);
            getLogger().info("Paper Dialog API detected and initialized!");
        } catch (Throwable t) {
            getLogger().info("Paper Dialog API not detected or unsupported, using Inventory GUI fallback.");
            dialogHomesUI = null;
        }

        // Register Commands and Listeners
        getCommand("home").setExecutor(this);
        getCommand("home").setTabCompleter(this);
        getCommand("homes").setExecutor(this);
        getCommand("homes").setTabCompleter(this);
        getCommand("checkhome").setExecutor(this);
        getCommand("checkhome").setTabCompleter(this);
        if (getCommand("neonhomes") != null) {
            getCommand("neonhomes").setExecutor(this);
            getCommand("neonhomes").setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new me.neonjava.advancedhomes.ui.SignInput(this), this);

        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            Bukkit.getPluginManager().registerEvents(new me.neonjava.advancedhomes.listener.PaperChatListener(this), this);
            getLogger().info("Paper AsyncChatEvent listener registered successfully!");
        } catch (ClassNotFoundException ignored) {}

        // Start Telemetry Reporter
        try {
            new me.neonjava.advancedhomes.telemetry.TelemetryReporter(this).start();
            getLogger().info("Telemetry reporter initialized.");
        } catch (Throwable t) {
            getLogger().fine("Telemetry reporter skipped: " + t.getMessage());
        }

        getLogger().info("NeonHomes Plugin successfully enabled!");
    }

    @Override
    public void onDisable() {
        me.neonjava.advancedhomes.ui.SignInput.shutdown();
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("NeonHomes Plugin disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("neonhomes") || command.getName().equalsIgnoreCase("advancedhomes")) {
            if (!sender.hasPermission("homes.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use NeonHomes admin commands!");
                return true;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                configManager.loadConfig();
                sender.sendMessage(ChatColor.GREEN + "[NeonHomes] Configuration reloaded successfully!");
                return true;
            }

            sender.sendMessage(ChatColor.AQUA + "--- NeonHomes v" + getDescription().getVersion() + " ---");
            sender.sendMessage(ChatColor.YELLOW + "/neonhomes reload " + ChatColor.GRAY + "- Reloads the plugin configuration.");
            sender.sendMessage(ChatColor.YELLOW + "/checkhome <player> " + ChatColor.GRAY + "- View another player's homes.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use the homes menu!");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("checkhome")) {
            if (!player.hasPermission("homes.admin.checkhome")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to check other player's homes!");
                return true;
            }
            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "Usage: /checkhome <username>");
                return true;
            }
            String targetName = args[0];
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            ((InventoryHomesUI) inventoryHomesUI).openCheckHomesMenu(player, target);
            return true;
        }

        if (!player.hasPermission("homes.use")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to open the homes menu!");
            return true;
        }

        // If command is /home and args are provided, teleport directly
        if (command.getName().equalsIgnoreCase("home") && args.length > 0) {
            String homeName = args[0];
            List<Home> currentHomes = homeManager.getHomes(player.getUniqueId());
            Home targetHome = null;
            int homeIndex = -1;
            for (int i = 0; i < currentHomes.size(); i++) {
                if (currentHomes.get(i).getName().equalsIgnoreCase(homeName)) {
                    targetHome = currentHomes.get(i);
                    homeIndex = i;
                    break;
                }
            }

            if (targetHome == null) {
                player.sendMessage(ChatColor.RED + "Home '" + homeName + "' not found.");
                return true;
            }

            int limit = homeManager.getPlayerHomeLimit(player);
            boolean isLocked = homeIndex >= limit;
            if (isLocked) {
                int remaining = targetHome.getRemainingTeleports();
                if (remaining <= 0) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', configManager.getMessageHomeExpired()));
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', configManager.getMessageNeedRankTeleport()));
                    player.sendMessage(ChatColor.YELLOW + "Coordinates of '" + targetHome.getName() + "': X: " + (int)targetHome.getX() + ", Y: " + (int)targetHome.getY() + ", Z: " + (int)targetHome.getZ() + " in world: " + targetHome.getWorldName());
                    return true;
                }

                int nextRemaining = remaining - 1;
                targetHome.setRemainingTeleports(nextRemaining);
                databaseManager.updateRemainingTeleports(player.getUniqueId(), targetHome.getName(), nextRemaining);
                player.sendMessage(ChatColor.GOLD + "Warning: You can teleport to this home only " + nextRemaining + " more times before it expires!");
            }

            homeManager.teleportPlayer(player, targetHome);
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
            return inventoryHomesUI;
        }
        if (configManager.isEnableBedrockSupport() && isBedrockPlayer(player.getUniqueId())) {
            return inventoryHomesUI;
        }
        if (dialogHomesUI == null) {
            return inventoryHomesUI;
        }
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
                Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
                Object api = viaClass.getMethod("getAPI").invoke(null);
                int protocolVersion = (int) api.getClass().getMethod("getPlayerVersion", UUID.class).invoke(api, player.getUniqueId());
                // Clients < 1.21.5/1.21.7 (protocol < 770) will be served custom InventoryHomesUI chest GUI
                if (protocolVersion < 770) {
                    return inventoryHomesUI;
                }
            }
        } catch (Throwable ignored) {}

        return dialogHomesUI;
    }

    private boolean isBedrockPlayer(UUID uuid) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")) {
                return org.geysermc.geyser.api.GeyserApi.api().isBedrockPlayer(uuid);
            }
        } catch (Throwable ignored) {}

        try {
            if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
                return org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(uuid);
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

    public me.neonjava.advancedhomes.util.ItemsDatabase getItemsDatabase() {
        return itemsDatabase;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        Player player = (Player) sender;
        if (command.getName().equalsIgnoreCase("home") && args.length == 1) {
            List<Home> homes = homeManager.getHomes(player.getUniqueId());
            List<String> suggestions = new ArrayList<>();
            String input = args[0].toLowerCase();
            for (Home home : homes) {
                if (home.getName().toLowerCase().startsWith(input)) {
                    suggestions.add(home.getName());
                }
            }
            return suggestions;
        }

        if (command.getName().equalsIgnoreCase("checkhome") && args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            String input = args[0].toLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(input)) {
                    suggestions.add(online.getName());
                }
            }
            return suggestions;
        }

        return Collections.emptyList();
    }
}
