package me.neonjava.advancedhomes.manager;

import me.neonjava.advancedhomes.AdvancedHomesPlugin;
import me.neonjava.advancedhomes.model.Home;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HomeManager {
    private final AdvancedHomesPlugin plugin;
    private final Map<UUID, List<Home>> homesCache = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> activeTeleports = new ConcurrentHashMap<>();

    public HomeManager(AdvancedHomesPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadPlayerHomes(UUID playerUuid) {
        List<Home> loaded = plugin.getDatabaseManager().loadHomes(playerUuid);
        if (loaded == null) {
            // Database load failed — do NOT overwrite cache with empty data.
            // If there's existing cached data, keep it. Otherwise, don't cache anything
            // so the next getHomes() call will retry the load.
            plugin.getLogger().warning("Failed to load homes for " + playerUuid + " — keeping existing cache if available.");
            return;
        }
        // Sort by creation date: oldest first
        loaded.sort(Comparator.comparingLong(Home::getCreationDate));
        homesCache.put(playerUuid, loaded);
    }

    public List<Home> getHomes(UUID playerUuid) {
        List<Home> cached = homesCache.get(playerUuid);
        if (cached == null) {
            loadPlayerHomes(playerUuid);
            cached = homesCache.get(playerUuid);
        }
        return cached != null ? cached : new ArrayList<>();
    }

    public Home getHome(UUID playerUuid, String name) {
        for (Home h : getHomes(playerUuid)) {
            if (h.getName().equalsIgnoreCase(name)) {
                return h;
            }
        }
        return null;
    }

    public void invalidateCache(UUID playerUuid) {
        homesCache.remove(playerUuid);
    }

    /**
     * Force-refresh a player's homes from the database, replacing the cache.
     * Called on PlayerJoinEvent to ensure fresh data.
     */
    public void refreshPlayerHomes(UUID playerUuid) {
        homesCache.remove(playerUuid);
        loadPlayerHomes(playerUuid);
    }

    public boolean addHome(Player player, String name) {
        UUID uuid = player.getUniqueId();
        List<Home> currentHomes = getHomes(uuid);
        
        int limit = getPlayerHomeLimit(player);
        
        // Check for duplicate home name (case-insensitive)
        for (Home h : currentHomes) {
            if (h.getName().equalsIgnoreCase(name)) {
                player.sendMessage(ChatColor.RED + "A home named '" + name + "' already exists! Choose a different name.");
                return false;
            }
        }
        
        // Count existing homes. If we have active homes equal to or greater than limit, we can't create more.
        // Rank downgrade logic: Plugin must NOT delete homes. All 10 homes remain stored.
        // If they are already at or above their limit, they cannot create a new home.
        if (currentHomes.size() >= limit) {
            player.sendMessage(ChatColor.RED + "You have reached your home limit of " + limit + ". Delete some to create new ones!");
            return false;
        }

        Location loc = player.getLocation();
        Home home = new Home(
                uuid,
                name,
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch(),
                "RED_BED", // Default icon
                System.currentTimeMillis()
        );

        boolean saved = plugin.getDatabaseManager().saveHome(home);
        if (saved) {
            // Reload cache from DB to guarantee cache-DB consistency
            // (prevents ghost entries from INSERT OR REPLACE edge cases)
            refreshPlayerHomes(uuid);
            player.sendMessage(ChatColor.GREEN + "Home '" + name + "' created successfully!");
            return true;
        }
        player.sendMessage(ChatColor.RED + "Failed to save home to database.");
        return false;
    }

    public boolean deleteHome(Player player, String homeName) {
        UUID uuid = player.getUniqueId();
        boolean deleted = plugin.getDatabaseManager().deleteHome(uuid, homeName);
        if (deleted) {
            // Reload cache from DB to guarantee consistency
            refreshPlayerHomes(uuid);
            player.sendMessage(ChatColor.GREEN + "Home '" + homeName + "' deleted.");
            return true;
        }
        player.sendMessage(ChatColor.RED + "Failed to delete home.");
        return false;
    }

    public boolean renameHome(Player player, String oldName, String newName) {
        UUID uuid = player.getUniqueId();
        
        // Check if a home with the new name already exists
        List<Home> currentHomes = getHomes(uuid);
        for (Home h : currentHomes) {
            if (h.getName().equalsIgnoreCase(newName)) {
                player.sendMessage(ChatColor.RED + "A home named '" + newName + "' already exists! Choose a different name.");
                return false;
            }
        }
        
        boolean renamed = plugin.getDatabaseManager().renameHome(uuid, oldName, newName);
        if (renamed) {
            // Reload cache from DB to guarantee consistency
            refreshPlayerHomes(uuid);
            player.sendMessage(ChatColor.GREEN + "Home renamed from '" + oldName + "' to '" + newName + "'.");
            return true;
        }
        player.sendMessage(ChatColor.RED + "Failed to rename home.");
        return false;
    }

    public boolean updateIcon(Player player, String homeName, String material) {
        UUID uuid = player.getUniqueId();
        boolean updated = plugin.getDatabaseManager().updateIcon(uuid, homeName, material);
        if (updated) {
            // Reload cache from DB to guarantee consistency
            refreshPlayerHomes(uuid);
            player.sendMessage(ChatColor.GREEN + "Icon for '" + homeName + "' changed to " + material + ".");
            return true;
        }
        player.sendMessage(ChatColor.RED + "Failed to change icon.");
        return false;
    }

    public int getPlayerHomeLimit(Player player) {
        int max = plugin.getConfigManager().getMaxHomes();
        if (player.isOp()) return max;

        int limit = plugin.getConfigManager().getDefaultHomeLimit();

        // Check direct numbered permissions (e.g. homes.limit.50)
        for (int i = max; i >= 1; i--) {
            if (player.hasPermission("homes.limit." + i)) {
                limit = Math.max(limit, i);
                break;
            }
        }

        // Check configured rank permissions (e.g. homes.rank.vip, group.vip)
        for (Map.Entry<String, Integer> entry : plugin.getConfigManager().getRanks().entrySet()) {
            String rankName = entry.getKey().toLowerCase(Locale.ROOT);
            if (player.hasPermission("homes.rank." + rankName) || 
                player.hasPermission("group." + rankName) || 
                player.hasPermission("homes.limit." + rankName)) {
                limit = Math.max(limit, entry.getValue());
            }
        }

        return limit;
    }

    public void teleportPlayer(Player player, Home home) {
        teleportPlayer(player, home, false);
    }

    public void teleportPlayer(Player player, Home home, boolean ignoreDelay) {
        int delay = ignoreDelay ? 0 : plugin.getConfigManager().getTeleportDelay();
        if (delay <= 0) {
            Location loc = home.getLocation();
            if (loc == null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessageWorldNotLoaded()));
                return;
            }
            player.teleportAsync(loc);
            String successMsg = plugin.getConfigManager().getMessageTeleportSuccess()
                    .replace("<home>", home.getName());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', successMsg));
            return;
        }

        cancelTeleport(player.getUniqueId());

        Location startLoc = player.getLocation().clone();
        int[] secondsLeft = { delay };

        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, (scheduledTask) -> {
            if (!player.isOnline()) {
                scheduledTask.cancel();
                activeTeleports.remove(player.getUniqueId());
                return;
            }

            if (plugin.getConfigManager().isCancelOnMove()) {
                Location currentLoc = player.getLocation();
                if (startLoc.getWorld() != currentLoc.getWorld() || startLoc.distanceSquared(currentLoc) > 0.1) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessageTeleportCancelled()));
                    scheduledTask.cancel();
                    activeTeleports.remove(player.getUniqueId());
                    return;
                }
            }

            if (secondsLeft[0] <= 0) {
                Location loc = home.getLocation();
                if (loc == null) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessageWorldNotLoaded()));
                } else {
                    player.teleportAsync(loc);
                    String successMsg = plugin.getConfigManager().getMessageTeleportSuccess()
                            .replace("<home>", home.getName());
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', successMsg));
                }
                scheduledTask.cancel();
                activeTeleports.remove(player.getUniqueId());
            } else {
                String countdownMsg = plugin.getConfigManager().getMessageTeleportCountdown()
                        .replace("<home>", home.getName())
                        .replace("<seconds>", String.valueOf(secondsLeft[0]));
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', countdownMsg));
                secondsLeft[0]--;
            }
        }, () -> {
            activeTeleports.remove(player.getUniqueId());
        }, 1L, 20L);

        if (task != null) {
            activeTeleports.put(player.getUniqueId(), task);
        }
    }

    public void cancelTeleport(UUID uuid) {
        ScheduledTask task = activeTeleports.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public boolean isTeleporting(UUID uuid) {
        return activeTeleports.containsKey(uuid);
    }
}
