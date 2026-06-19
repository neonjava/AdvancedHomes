package me.neonjava.advancedhomes.listener;

import me.neonjava.advancedhomes.AdvancedHomesPlugin;
import me.neonjava.advancedhomes.model.Home;
import me.neonjava.advancedhomes.ui.HomesHolder;
import me.neonjava.advancedhomes.ui.InventoryHomesUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PlayerListener implements Listener {
    private final AdvancedHomesPlugin plugin;
    private final int[] homeSlots = {2, 3, 4, 5, 6, 11, 12, 13, 14, 15};

    public PlayerListener(AdvancedHomesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof HomesHolder)) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        HomesHolder holder = (HomesHolder) event.getInventory().getHolder();
        int slot = event.getRawSlot();

        InventoryHomesUI invUI = (InventoryHomesUI) plugin.getInventoryHomesUI();

        switch (holder.getType()) {
            case MAIN:
                if (slot == 22) {
                    player.closeInventory();
                    return;
                }
                // Check if slot is one of the home slots
                int homeIndex = -1;
                for (int i = 0; i < homeSlots.length; i++) {
                    if (homeSlots[i] == slot) {
                        homeIndex = i;
                        break;
                    }
                }
                if (homeIndex != -1) {
                    List<Home> homes = plugin.getHomeManager().getHomes(player.getUniqueId());
                    int limit = plugin.getHomeManager().getPlayerHomeLimit(player);

                    if (homeIndex < homes.size()) {
                        Home home = homes.get(homeIndex);
                        boolean isLocked = homeIndex >= limit;
                        invUI.openHomeManagementMenu(player, home, isLocked);
                    } else if (homeIndex < limit) {
                        invUI.openCreateHomeFlow(player, homeIndex);
                    } else {
                        player.closeInventory();
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessageLockedWarning()));
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessageBuyRanksWarning()));
                        player.sendMessage(ChatColor.GRAY + "---------------------------------------------");
                        for (String line : invUI.getRankCriteriaLore(homeIndex)) {
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
                        }
                        player.sendMessage(ChatColor.GRAY + "---------------------------------------------");
                    }
                }
                break;

            case CREATE:
                if (slot == 11) {
                    invUI.openCreateHomeTextInput(player);
                } else if (slot == 15) {
                    invUI.openMainHomesMenu(player);
                }
                break;

            case MANAGEMENT:
                Home mHome = holder.getHome();
                boolean isLocked = holder.isLocked();
                if (slot == 22) {
                    invUI.openMainHomesMenu(player);
                } else if (slot == 16) {
                    invUI.openDeleteConfirmation(player, mHome);
                } else if (slot == 10) {
                    player.closeInventory();
                    if (isLocked) {
                        int remaining = mHome.getRemainingTeleports();
                        if (remaining <= 0) {
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessageHomeExpired()));
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessageNeedRankTeleport()));
                            player.sendMessage(ChatColor.YELLOW + "Coordinates of '" + mHome.getName() + "': X: " + (int)mHome.getX() + ", Y: " + (int)mHome.getY() + ", Z: " + (int)mHome.getZ() + " in world: " + mHome.getWorldName());
                            return;
                        }

                        int nextRemaining = remaining - 1;
                        mHome.setRemainingTeleports(nextRemaining);
                        plugin.getDatabaseManager().updateRemainingTeleports(player.getUniqueId(), mHome.getName(), nextRemaining);
                        player.sendMessage(ChatColor.GOLD + "Warning: You can teleport to this home only " + nextRemaining + " more times before it expires!");
                    }
                    plugin.getHomeManager().teleportPlayer(player, mHome);
                } else if (!isLocked) {
                    if (slot == 12) {
                        invUI.openRenameHomeTextInput(player, mHome);
                    } else if (slot == 14) {
                        invUI.openIconBrowser(player, mHome, false, 0, "", "all");
                    }
                }
                break;

            case ICON_BROWSER:
                Home iHome = holder.getHome();
                boolean iLocked = holder.isLocked();
                int page = holder.getPage();
                String query = holder.getSearchQuery();
                String category = holder.getCategory();

                if (slot == 53) {
                    invUI.openHomeManagementMenu(player, iHome, iLocked);
                    return;
                }

                if (slot == 45) {
                    // Cycle category
                    String nextCat = getNextCategory(category);
                    invUI.openIconBrowser(player, iHome, iLocked, 0, query, nextCat);
                    return;
                }

                if (slot == 46) {
                    invUI.openIconSearchTextInput(player, iHome, iLocked, category);
                    return;
                }

                if (slot == 48 && page > 0) {
                    invUI.openIconBrowser(player, iHome, iLocked, page - 1, query, category);
                    return;
                }

                // Next page check
                List<Material> filtered = filterMaterials(query, category);
                int itemsPerPage = 45;
                int totalPages = (filtered.size() + itemsPerPage - 1) / itemsPerPage;
                if (slot == 50 && page < totalPages - 1) {
                    invUI.openIconBrowser(player, iHome, iLocked, page + 1, query, category);
                    return;
                }

                if (slot >= 0 && slot < 45) {
                    ItemStack clickedItem = event.getCurrentItem();
                    if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                        Material mat = clickedItem.getType();
                        plugin.getHomeManager().updateIcon(player, iHome.getName(), mat.name());
                        invUI.openHomeManagementMenu(player, iHome, iLocked);
                    }
                }
                break;

            case DELETE_CONFIRM:
                Home dHome = holder.getHome();
                if (slot == 11) {
                    plugin.getHomeManager().deleteHome(player, dHome.getName());
                    player.closeInventory();
                } else if (slot == 15) {
                    invUI.openHomeManagementMenu(player, dHome, holder.isLocked());
                }
                break;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getChatInputManager().handleChat(player.getUniqueId(), event.getMessage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getConfigManager().isCancelOnMove()) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (plugin.getHomeManager().isTeleporting(uuid)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null && (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ())) {
                plugin.getHomeManager().cancelTeleport(uuid);
                player.sendMessage(ChatColor.RED + "Teleportation cancelled due to movement.");
            }
        }
    }

    private String getNextCategory(String current) {
        switch (current.toLowerCase(Locale.ROOT)) {
            case "all": return "blocks";
            case "blocks": return "food";
            case "food": return "tools";
            case "tools": return "misc";
            default: return "all";
        }
    }

    private List<Material> filterMaterials(String query, String category) {
        List<Material> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (Material mat : Material.values()) {
            if (!mat.isItem() || mat.isAir()) continue;
            String name = mat.name().toLowerCase(Locale.ROOT);
            if (!name.contains(lowerQuery)) continue;

            // Filter by category
            if ("blocks".equals(category) && !mat.isBlock()) continue;
            if ("food".equals(category) && !mat.isEdible()) continue;
            if ("tools".equals(category)) {
                boolean isTool = name.contains("sword") || name.contains("pickaxe") || name.contains("axe")
                        || name.contains("shovel") || name.contains("hoe") || name.contains("helmet")
                        || name.contains("chestplate") || name.contains("leggings") || name.contains("boots")
                        || name.contains("shield") || name.contains("bow") || name.contains("crossbow");
                if (!isTool) continue;
            }
            if ("misc".equals(category)) {
                boolean isBlock = mat.isBlock();
                boolean isEdible = mat.isEdible();
                boolean isTool = name.contains("sword") || name.contains("pickaxe") || name.contains("axe")
                        || name.contains("shovel") || name.contains("hoe") || name.contains("helmet")
                        || name.contains("chestplate") || name.contains("leggings") || name.contains("boots")
                        || name.contains("shield") || name.contains("bow") || name.contains("crossbow");
                if (isBlock || isEdible || isTool) continue;
            }
            results.add(mat);
        }
        return results;
    }
}
