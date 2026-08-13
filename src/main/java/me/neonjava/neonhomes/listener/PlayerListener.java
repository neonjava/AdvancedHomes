package me.neonjava.neonhomes.listener;

import me.neonjava.neonhomes.NeonHomesPlugin;
import me.neonjava.neonhomes.config.ConfigManager;
import me.neonjava.neonhomes.model.Home;
import me.neonjava.neonhomes.ui.HomesHolder;
import me.neonjava.neonhomes.ui.InventoryHomesUI;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PlayerListener implements Listener {
    private final NeonHomesPlugin plugin;

    public PlayerListener(NeonHomesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getHomeManager().refreshPlayerHomes(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getHomeManager().invalidateCache(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        boolean isHomesHolder = event.getInventory().getHolder() instanceof HomesHolder;
        boolean isCheckHomesHolder = event.getInventory().getHolder() instanceof me.neonjava.neonhomes.ui.CheckHomesHolder;
        if (!isHomesHolder && !isCheckHomesHolder) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();

        if (isCheckHomesHolder) {
            me.neonjava.neonhomes.ui.CheckHomesHolder checkHolder = (me.neonjava.neonhomes.ui.CheckHomesHolder) event.getInventory().getHolder();
            int slot = event.getRawSlot();
            if (slot >= 0 && slot < 45) {
                List<Home> homes = plugin.getHomeManager().getHomes(checkHolder.getTarget().getUniqueId());
                if (slot < homes.size()) {
                    Home home = homes.get(slot);
                    player.closeInventory();
                    plugin.getHomeManager().teleportPlayer(player, home, true);
                    player.sendMessage(ChatColor.GREEN + "Teleporting to " + checkHolder.getTarget().getName() + "'s home: " + home.getName());
                }
            }
            return;
        }

        HomesHolder holder = (HomesHolder) event.getInventory().getHolder();
        int slot = event.getRawSlot();

        InventoryHomesUI invUI = (InventoryHomesUI) plugin.getInventoryHomesUI();

        switch (holder.getType()) {
            case MAIN:
                int currentPage = holder.getPage();
                int maxHomes = plugin.getConfigManager().getMaxHomes();
                int itemsPerPage = 45;
                int totalPages = (maxHomes + itemsPerPage - 1) / itemsPerPage;

                if (slot >= 0 && slot < 45) {
                    int globalIndex = currentPage * 45 + slot;
                    if (globalIndex < maxHomes) {
                        List<Home> homes = plugin.getHomeManager().getHomes(player.getUniqueId());
                        int limit = plugin.getHomeManager().getPlayerHomeLimit(player);

                        if (globalIndex < homes.size()) {
                            Home home = homes.get(globalIndex);
                            boolean isLocked = globalIndex >= limit;
                            invUI.openHomeManagementMenu(player, home, isLocked);
                        } else if (globalIndex < limit) {
                            invUI.openCreateHomeTextInput(player);
                        } else {
                            ConfigManager.RankRequirement req = plugin.getConfigManager().getRankRequirementForSlot(globalIndex);
                            String warning = plugin.getConfigManager().getMessageBuyRanksWarning()
                                    .replace("<rank>", req.getRankName())
                                    .replace("<slots>", String.valueOf(req.getSlots()));
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&', warning));
                        }
                    }
                } else if (slot == 45 && currentPage > 0) {
                    invUI.openMainHomesMenu(player, currentPage - 1);
                } else if (slot == 53 && currentPage < totalPages - 1) {
                    invUI.openMainHomesMenu(player, currentPage + 1);
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
                if (slot == 0) {
                    // Teleport (Slot 0)
                    player.closeInventory();
                    if (isLocked) {
                        int remaining = mHome.getRemainingTeleports();
                        ConfigManager.RankRequirement req = holder.getSlotIndex() >= 0 
                                ? plugin.getConfigManager().getRankRequirementForSlot(holder.getSlotIndex())
                                : new ConfigManager.RankRequirement("VIP", 5);

                        if (remaining <= 0) {
                            String noChancesMsg = plugin.getConfigManager().getMessageHomeNoChances()
                                    .replace("<rank>", req.getRankName())
                                    .replace("<slots>", String.valueOf(req.getSlots()));
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noChancesMsg));
                            return;
                        }
                        int nextRemaining = remaining - 1;
                        mHome.setRemainingTeleports(nextRemaining);
                        plugin.getDatabaseManager().updateRemainingTeleports(player.getUniqueId(), mHome.getName(), nextRemaining);

                        String expiredMsg = plugin.getConfigManager().getMessageHomeExpired()
                                .replace("<remaining>", String.valueOf(nextRemaining))
                                .replace("<rank>", req.getRankName());
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', expiredMsg));
                    }
                    plugin.getHomeManager().teleportPlayer(player, mHome);
                } else if (slot == 1 && !isLocked) {
                    // Rename (Slot 1)
                    invUI.openRenameHomeTextInput(player, mHome);
                } else if (slot == 2 && !isLocked) {
                    // Icon (Slot 2)
                    invUI.openIconBrowser(player, mHome, false, 0, "", "all");
                } else if (slot == 4) {
                    // Delete (Slot 4)
                    invUI.openDeleteConfirmation(player, mHome);
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

                List<Material> filtered = filterMaterials(query, category);
                int totalIconPages = (filtered.size() + 45 - 1) / 45;
                if (slot == 50 && page < totalIconPages - 1) {
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandPreprocess(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().trim();
        String[] parts = message.split("\\s+");
        if (parts.length == 0) return;

        String cmdName = parts[0].substring(1).toLowerCase(Locale.ROOT);

        if (cmdName.equals("home") || cmdName.equals("homes")) {
            event.setCancelled(true);
            Player player = event.getPlayer();

            String[] args = new String[parts.length - 1];
            System.arraycopy(parts, 1, args, 0, parts.length - 1);

            org.bukkit.command.PluginCommand command = plugin.getCommand(cmdName);
            if (command != null) {
                plugin.onCommand(player, command, cmdName, args);
            }
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
            org.bukkit.Location from = event.getFrom();
            org.bukkit.Location to = event.getTo();
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
        java.util.List<Material> results = new java.util.ArrayList<>();
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (Material mat : Material.values()) {
            if (!mat.isItem() || mat.isAir()) continue;
            String name = mat.name().toLowerCase(Locale.ROOT);
            if (!name.contains(lowerQuery)) continue;

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
