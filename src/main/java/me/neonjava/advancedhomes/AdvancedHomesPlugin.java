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
import me.neonjava.advancedhomes.model.Home;

import java.util.List;
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
        getCommand("home").setExecutor(this);
        getCommand("homes").setExecutor(this);
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

        // Only use Dialog GUI if protocol is known and >= 772 (Minecraft 1.21.7+)
        int protocol = getPlayerProtocolVersion(player);
        if (protocol >= 772) {
            getLogger().info("[UI Choice] Player " + player.getName() + " -> Dialog GUI (Protocol: " + protocol + ")");
            return dialogHomesUI;
        }

        getLogger().info("[UI Choice] Player " + player.getName() + " -> Chest GUI (Protocol: " + protocol + " < 772 or unknown)");
        return inventoryHomesUI;
    }

    private int getPlayerProtocolVersion(Player player) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
                Object viaApi = Class.forName("com.viaversion.viaversion.api.Via").getMethod("getAPI").invoke(null);
                if (viaApi != null) {
                    Class<?> viaApiClass = Class.forName("com.viaversion.viaversion.api.ViaAPI");
                    Object versionObj = viaApiClass.getMethod("getPlayerVersion", java.util.UUID.class).invoke(viaApi, player.getUniqueId());
                    if (versionObj instanceof Number) {
                        return ((Number) versionObj).intValue();
                    }
                }
            }
        } catch (Throwable ignored) {}
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
                Object protocolManager = Class.forName("com.comphenix.protocol.ProtocolLibrary").getMethod("getProtocolManager").invoke(null);
                if (protocolManager != null) {
                    Object versionObj = protocolManager.getClass().getMethod("getProtocolVersion", Player.class).invoke(protocolManager, player);
                    if (versionObj instanceof Number) {
                        return ((Number) versionObj).intValue();
                    }
                }
            }
        } catch (Throwable ignored) {}
        try {
            if (player instanceof com.destroystokyo.paper.network.NetworkClient) {
                return ((com.destroystokyo.paper.network.NetworkClient) player).getProtocolVersion();
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private boolean isBedrockPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (uuid.getMostSignificantBits() == 0) {
            return true;
        }
        String name = player.getName();
        if (name != null && (name.startsWith(".") || name.startsWith("*"))) {
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
