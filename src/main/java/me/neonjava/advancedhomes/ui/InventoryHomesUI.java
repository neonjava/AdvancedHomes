package me.neonjava.advancedhomes.ui;

import me.neonjava.advancedhomes.AdvancedHomesPlugin;
import me.neonjava.advancedhomes.model.Home;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.*;

public class InventoryHomesUI implements HomesUI {
    private final AdvancedHomesPlugin plugin;
    private final int[] homeSlots = {2, 3, 4, 5, 6, 11, 12, 13, 14, 15};

    public InventoryHomesUI(AdvancedHomesPlugin plugin) {
        this.plugin = plugin;
    }

    private void runSync(Player player, Runnable runnable) {
        player.getScheduler().run(plugin, task -> runnable.run(), null);
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ComponentToBukkit formatName(String name) {
        if (plugin.getConfigManager().isAllowColoredNames()) {
            return new ComponentToBukkit(ChatColor.translateAlternateColorCodes('&', name));
        } else {
            return new ComponentToBukkit(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', name)));
        }
    }

    private static class ComponentToBukkit {
        private final String text;
        public ComponentToBukkit(String text) {
            this.text = text;
        }
        @Override
        public String toString() {
            return text;
        }
    }

    public List<String> getRankCriteriaLore(int slotIndex) {
        String rankStr;
        if (slotIndex >= 1 && slotIndex <= 4) { // Homes 2, 3, 4, 5
            rankStr = "VIP+";
        } else if (slotIndex == 5) { // Home 6
            rankStr = "MVP";
        } else if (slotIndex >= 6 && slotIndex <= 7) { // Homes 7, 8
            rankStr = "STAR";
        } else { // Homes 9, 10
            rankStr = "GOD";
        }

        List<String> lore = new ArrayList<>();
        lore.add(plugin.getConfigManager().getMessageNeedRank().replace("<rank>", rankStr));
        lore.add(plugin.getConfigManager().getMessageBuyRank());
        return lore;
    }

    @Override
    public void openMainHomesMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new HomesHolder(HomesHolder.Type.MAIN), 27, ChatColor.DARK_BLUE + "Homes Menu");

        // Fill background with light gray glass panes
        ItemStack border = createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        List<Home> homes = plugin.getHomeManager().getHomes(player.getUniqueId());
        int limit = plugin.getHomeManager().getPlayerHomeLimit(player);

        for (int i = 0; i < 10; i++) {
            int slot = homeSlots[i];
            if (i < homes.size()) {
                Home home = homes.get(i);
                boolean isLocked = i >= limit;

                Material iconMat = Material.matchMaterial(home.getIconMaterial());
                if (iconMat == null) iconMat = Material.RED_BED;

                String displayName = isLocked ? "&c🔒 Locked Home" : "&a" + home.getName();
                String statusStr = isLocked ? "&cLocked by Rank" : "&aActive";

                List<String> loreList = new ArrayList<>();
                loreList.add("&7Status: " + statusStr);
                if (isLocked) {
                    loreList.add("");
                    loreList.addAll(getRankCriteriaLore(i));
                    loreList.add("");
                    loreList.add("&eClick to Manage (Limited Teleport / Delete)");
                } else {
                    loreList.add("");
                    loreList.add("&eClick to Manage Home");
                }

                ItemStack homeItem = createItem(
                        iconMat,
                        displayName,
                        loreList.toArray(new String[0])
                );
                inv.setItem(slot, homeItem);
            } else if (i < limit) {
                // Empty slot (Create Home)
                ItemStack createItem = createItem(
                        Material.GREEN_STAINED_GLASS_PANE,
                        "&a+ Create Home",
                        "&7Empty Home Slot",
                        "&eClick to create a home here"
                );
                inv.setItem(slot, createItem);
            } else {
                // Locked slot (Locked by Rank)
                List<String> loreList = getRankCriteriaLore(i);
                ItemStack lockedItem = createItem(
                        Material.RED_STAINED_GLASS_PANE,
                        "&c🔒 Locked Slot",
                        loreList.toArray(new String[0])
                );
                inv.setItem(slot, lockedItem);
            }
        }

        // Close button at slot 22
        inv.setItem(22, createItem(Material.BARRIER, "&cClose Menu"));

        player.openInventory(inv);
    }

    @Override
    public void openCreateHomeFlow(Player player, int slotIndex) {
        Inventory inv = Bukkit.createInventory(new HomesHolder(HomesHolder.Type.CREATE, slotIndex), 27, ChatColor.DARK_GREEN + "Create Home?");

        ItemStack border = createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        inv.setItem(11, createItem(Material.LIME_CONCRETE, "&aCreate Home", "&7Click to enter a home name"));
        inv.setItem(15, createItem(Material.RED_CONCRETE, "&cCancel", "&7Return to homes list"));

        player.openInventory(inv);
    }

    public void openCreateHomeTextInput(Player player) {
        player.closeInventory();
        AnvilInput.open(player, "Create Home Name", "Home", name -> {
            runSync(player, () -> {
                String trimmed = name.trim();
                if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("cancel")) {
                    player.sendMessage(ChatColor.RED + "Creation aborted.");
                    openMainHomesMenu(player);
                    return;
                }
                if (trimmed.length() > 16) {
                    player.sendMessage(ChatColor.RED + "Home name is too long! Max length: 16 characters.");
                    openCreateHomeTextInput(player);
                    return;
                }
                plugin.getHomeManager().addHome(player, trimmed);
                openMainHomesMenu(player);
            });
        });
    }

    @Override
    public void openHomeManagementMenu(Player player, Home home, boolean isLocked) {
        Inventory inv = Bukkit.createInventory(new HomesHolder(HomesHolder.Type.MANAGEMENT, home, isLocked), 27, ChatColor.DARK_BLUE + "Manage Home");

        ItemStack border = createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        if (!isLocked) {
            inv.setItem(10, createItem(Material.COMPASS, "&a⚡ Teleport to Home", "&7Click to teleport to this home after countdown"));
            inv.setItem(12, createItem(Material.NAME_TAG, "&a✏ Rename Home", "&7Change the name of this home"));
            inv.setItem(14, createItem(Material.PAINTING, "&a🖼 Change Icon", "&7Select a new icon material"));
        } else {
            int remaining = home.getRemainingTeleports();
            inv.setItem(10, createItem(Material.COMPASS, "&a⚡ Teleport to Home (" + remaining + " left)", "&7Click to teleport to this home", "&7Limited to 3 total teleports."));

            inv.setItem(11, createItem(Material.EMERALD, "&a🚩 Set Team Home", "&7Click to run /team sethome " + home.getName()));
            inv.setItem(12, createItem(Material.ENDER_PEARL, "&a⚡ Team Teleport", "&7Click to run /team home " + home.getName()));

            // Find home index to show rank criteria
            List<Home> homes = plugin.getHomeManager().getHomes(player.getUniqueId());
            int homeIndex = -1;
            for (int i = 0; i < homes.size(); i++) {
                if (homes.get(i).getName().equalsIgnoreCase(home.getName())) {
                    homeIndex = i;
                    break;
                }
            }
            List<String> criteriaLore = new ArrayList<>();
            criteriaLore.add("&7Upgrade your rank to access this home.");
            criteriaLore.add("&7Only deletion and 3 teleports are allowed.");
            criteriaLore.add("");
            if (homeIndex != -1) {
                criteriaLore.addAll(getRankCriteriaLore(homeIndex));
            }

            inv.setItem(13, createItem(
                    Material.BARRIER,
                    "&c⚠️ Locked by Rank",
                    criteriaLore.toArray(new String[0])
            ));

            inv.setItem(14, createItem(Material.REDSTONE, "&c❌ Team Delete", "&7Click to run /team delhome " + home.getName()));
        }

        // Delete button is always available
        inv.setItem(16, createItem(Material.TNT, "&c❌ Delete Home", "&7Permanently delete this home"));

        // Back button
        inv.setItem(22, createItem(Material.ARROW, "&e⬅ Return to Homes Menu"));

        player.openInventory(inv);
    }

    public void openRenameHomeTextInput(Player player, Home home) {
        player.closeInventory();
        AnvilInput.open(player, "Rename: " + home.getName(), home.getName(), name -> {
            runSync(player, () -> {
                String trimmed = name.trim();
                if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("cancel") || trimmed.equals(home.getName())) {
                    player.sendMessage(ChatColor.RED + "Rename aborted.");
                    openHomeManagementMenu(player, home, false);
                    return;
                }
                if (trimmed.length() > 16) {
                    player.sendMessage(ChatColor.RED + "Home name is too long! Max length: 16 characters.");
                    openRenameHomeTextInput(player, home);
                    return;
                }
                plugin.getHomeManager().renameHome(player, home.getName(), trimmed);
                openMainHomesMenu(player);
            });
        });
    }

    @Override
    public void openIconBrowser(Player player, Home home, boolean isLocked, int page, String searchQuery, String category) {
        Inventory inv = Bukkit.createInventory(new HomesHolder(HomesHolder.Type.ICON_BROWSER, home, isLocked, page, searchQuery, category), 54, ChatColor.DARK_BLUE + "Select Icon - Page " + (page + 1));

        List<Material> filtered = filterMaterials(searchQuery, category);
        int itemsPerPage = 45;
        int totalPages = (filtered.size() + itemsPerPage - 1) / itemsPerPage;
        if (totalPages == 0) totalPages = 1;

        int finalPage = Math.max(0, Math.min(page, totalPages - 1));
        int startIndex = finalPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, filtered.size());

        // Fill materials in slots 0-44
        for (int i = 0; i < 45; i++) {
            int itemIndex = startIndex + i;
            if (itemIndex < endIndex) {
                Material mat = filtered.get(itemIndex);
                inv.setItem(i, createItem(mat, "&a" + mat.name().replace('_', ' '), "&7Click to select as home icon"));
            } else {
                inv.setItem(i, createItem(Material.AIR, " "));
            }
        }

        // Fill border in slots 45-53
        ItemStack border = createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, border);
        }

        // Category button (slot 45)
        inv.setItem(45, createItem(Material.BOOKSHELF, "&e📁 Category: " + category.toUpperCase(), "&7Click to cycle categories: ALL -> BLOCKS -> FOOD -> TOOLS -> MISC"));

        // Search button (slot 46)
        inv.setItem(46, createItem(Material.ANVIL, "&e🔍 Search Query: &f" + (searchQuery.isEmpty() ? "[None]" : searchQuery), "&7Click to search for item names in chat"));

        // Page Indicator & Pagination
        if (finalPage > 0) {
            inv.setItem(48, createItem(Material.ARROW, "&a◀ Previous Page"));
        }
        inv.setItem(49, createItem(Material.BOOK, "&bPage " + (finalPage + 1) + " of " + totalPages, "&7Total matches: " + filtered.size()));
        if (finalPage < totalPages - 1) {
            inv.setItem(50, createItem(Material.ARROW, "&aNext Page ▶"));
        }

        // Return button (slot 53)
        inv.setItem(53, createItem(Material.BARRIER, "&c⬅ Back to Home Management"));

        player.openInventory(inv);
    }

    public void openIconSearchTextInput(Player player, Home home, boolean isLocked, String category) {
        player.closeInventory();
        AnvilInput.open(player, "Search Icon", "", search -> {
            runSync(player, () -> {
                String trimmed = search.trim();
                if (trimmed.equalsIgnoreCase("cancel")) {
                    openIconBrowser(player, home, isLocked, 0, "", category);
                    return;
                }
                openIconBrowser(player, home, isLocked, 0, trimmed, category);
            });
        });
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

    @Override
    public void openDeleteConfirmation(Player player, Home home) {
        Inventory inv = Bukkit.createInventory(new HomesHolder(HomesHolder.Type.DELETE_CONFIRM, home, false), 27, ChatColor.DARK_RED + "Delete Home?");

        ItemStack border = createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        inv.setItem(11, createItem(Material.LIME_CONCRETE, "&aYES - Confirm Delete", "&cThis action is permanent!"));
        inv.setItem(15, createItem(Material.RED_CONCRETE, "&cNO - Keep Home", "&7Cancel deletion"));

        player.openInventory(inv);
    }
}
