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
        // Sort by creation date: oldest first
        loaded.sort(Comparator.comparingLong(Home::getCreationDate));
        homesCache.put(playerUuid, loaded);
    }

    public List<Home> getHomes(UUID playerUuid) {
        if (!homesCache.containsKey(playerUuid)) {
            loadPlayerHomes(playerUuid);
        }
        return homesCache.getOrDefault(playerUuid, new ArrayList<>());
    }

    public void invalidateCache(UUID playerUuid) {
        homesCache.remove(playerUuid);
    }

    public boolean addHome(Player player, String name) {
        UUID uuid = player.getUniqueId();
        List<Home> currentHomes = getHomes(uuid);
        
        int limit = getPlayerHomeLimit(player);
        
        // Count existing homes. If we have active homes equal to or greater than limit, we can't create more.
        // Wait, rank downgrade logic says: "Plugin must NOT delete homes. All 10 homes remain stored. Accessible Homes: Player may fully use only first allowed homes. Homes 1-3 allowed: Teleport, Rename, Change Icon, Delete. Homes 4-10: Only Delete."
        // What if they want to create a new home but already have more homes than their limit?
        // Usually, if they are already at or above their limit, they cannot create a new home.
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
            currentHomes.add(home);
            // Re-sort just in case
            currentHomes.sort(Comparator.comparingLong(Home::getCreationDate));
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
            List<Home> currentHomes = getHomes(uuid);
            currentHomes.removeIf(h -> h.getName().equalsIgnoreCase(homeName));
            player.sendMessage(ChatColor.GREEN + "Home '" + homeName + "' deleted.");
            return true;
        }
        player.sendMessage(ChatColor.RED + "Failed to delete home.");
        return false;
    }

    public boolean renameHome(Player player, String oldName, String newName) {
        UUID uuid = player.getUniqueId();
        boolean renamed = plugin.getDatabaseManager().renameHome(uuid, oldName, newName);
        if (renamed) {
            List<Home> currentHomes = getHomes(uuid);
            for (Home h : currentHomes) {
                if (h.getName().equalsIgnoreCase(oldName)) {
                    h.setName(newName);
                    break;
                }
            }
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
            List<Home> currentHomes = getHomes(uuid);
            for (Home h : currentHomes) {
                if (h.getName().equalsIgnoreCase(homeName)) {
                    h.setIconMaterial(material);
                    break;
                }
            }
            player.sendMessage(ChatColor.GREEN + "Icon for '" + homeName + "' changed to " + material + ".");
            return true;
        }
        player.sendMessage(ChatColor.RED + "Failed to change icon.");
        return false;
    }

    public int getPlayerHomeLimit(Player player) {
        if (player.isOp()) return 10;
        for (int i = 10; i >= 1; i--) {
            if (player.hasPermission("homes.limit." + i)) {
                return i;
            }
        }
        return 1; // Default fallback
    }

    public void teleportPlayer(Player player, Home home) {
        int delay = plugin.getConfigManager().getTeleportDelay();
        if (delay <= 0) {
            Location loc = home.getLocation();
            if (loc == null) {
                player.sendMessage(ChatColor.RED + "The world for this home is not loaded.");
                return;
            }
            player.teleportAsync(loc);
            player.sendMessage(ChatColor.GREEN + "Teleported to '" + home.getName() + "'!");
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
                    player.sendMessage(ChatColor.RED + "Teleportation cancelled due to movement.");
                    scheduledTask.cancel();
                    activeTeleports.remove(player.getUniqueId());
                    return;
                }
            }

            if (secondsLeft[0] <= 0) {
                Location loc = home.getLocation();
                if (loc == null) {
                    player.sendMessage(ChatColor.RED + "The world for this home is not loaded.");
                } else {
                    player.teleportAsync(loc);
                    player.sendMessage(ChatColor.GREEN + "Teleported to '" + home.getName() + "'!");
                }
                scheduledTask.cancel();
                activeTeleports.remove(player.getUniqueId());
            } else {
                player.sendMessage(ChatColor.GOLD + "Teleporting in " + secondsLeft[0] + "...");
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
