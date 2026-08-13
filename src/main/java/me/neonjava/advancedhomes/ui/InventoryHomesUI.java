package me.neonjava.advancedhomes.ui;

import me.neonjava.advancedhomes.AdvancedHomesPlugin;
import me.neonjava.advancedhomes.model.Home;
import me.neonjava.advancedhomes.util.UnicodeFont;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class InventoryHomesUI implements HomesUI {
    private final AdvancedHomesPlugin plugin;

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
            if (lore.length > 0) {
                List<String> loreList = new ArrayList<>();
                for (String line : lore) {
                    loreList.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(loreList);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void openMainHomesMenu(Player player) {
        openMainHomesMenu(player, 0);
    }

    @Override
    public void openMainHomesMenu(Player player, int page) {
        String title = UnicodeFont.toSmallCaps("Homes");
        Inventory inv = Bukkit.createInventory(new HomesHolder(HomesHolder.Type.MAIN, page, "", "all", -1), 54, title);

        List<Home> homes = plugin.getHomeManager().getHomes(player.getUniqueId());
        int limit = plugin.getHomeManager().getPlayerHomeLimit(player);
        int maxHomes = plugin.getConfigManager().getMaxHomes();

        int itemsPerPage = 45;
        int totalPages = (maxHomes + itemsPerPage - 1) / itemsPerPage;
        if (totalPages == 0) totalPages = 1;
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));

        int startHomeIndex = currentPage * itemsPerPage;

        for (int i = 0; i < 45; i++) {
            int globalSlotIndex = startHomeIndex + i;
            if (globalSlotIndex >= maxHomes) {
                inv.setItem(i, createItem(Material.AIR, " "));
                continue;
            }

            if (globalSlotIndex < homes.size()) {
                Home home = homes.get(globalSlotIndex);
                boolean isLocked = globalSlotIndex >= limit;

                Material iconMat = Material.matchMaterial(home.getIconMaterial());
                if (iconMat == null) iconMat = Material.WHITE_BED;

                if (isLocked) {
                    inv.setItem(i, createItem(
                            Material.RED_STAINED_GLASS_PANE,
                            "&c" + UnicodeFont.toSmallCaps("Locked"),
                            plugin.getConfigManager().getMessageLockedTooltip()
                    ));
                } else {
                    inv.setItem(i, createItem(
                            iconMat,
                            "&f" + home.getName(),
                            "&f&oClick to manage"
                    ));
                }
            } else if (globalSlotIndex < limit) {
                inv.setItem(i, createItem(
                        Material.WHITE_BED,
                        "&a+ " + UnicodeFont.toSmallCaps("Create Home"),
                        "&7Empty Home Slot",
                        "&eClick to create a home here"
                ));
            } else {
                inv.setItem(i, createItem(
                        Material.RED_STAINED_GLASS_PANE,
                        "&c" + UnicodeFont.toSmallCaps("Locked"),
                        plugin.getConfigManager().getMessageLockedTooltip()
                ));
            }
        }

        inv.setItem(49, createItem(Material.GRAY_BED, " "));

        if (currentPage > 0) {
            inv.setItem(45, createItem(Material.ARROW, "&f◀ " + UnicodeFont.toSmallCaps("Previous Page")));
        }

        if (currentPage < totalPages - 1) {
            inv.setItem(53, createItem(Material.ARROW, "&f" + UnicodeFont.toSmallCaps("Next Page") + " ▶"));
        }

        player.openInventory(inv);
    }

    public void openCheckHomesMenu(Player admin, OfflinePlayer target) {
        String title = UnicodeFont.toSmallCaps(target.getName() + "'s Homes");
        Inventory inv = Bukkit.createInventory(new CheckHomesHolder(target), 54, title);

        List<Home> homes = plugin.getHomeManager().getHomes(target.getUniqueId());

        for (int i = 0; i < 45; i++) {
            if (i < homes.size()) {
                Home home = homes.get(i);
                Material iconMat = Material.matchMaterial(home.getIconMaterial());
                if (iconMat == null) iconMat = Material.WHITE_BED;

                inv.setItem(i, createItem(
                        iconMat,
                        "&a" + home.getName(),
                        "&7World: " + home.getWorldName(),
                        "&7X: " + (int)home.getX() + " Y: " + (int)home.getY() + " Z: " + (int)home.getZ(),
                        "&eClick to teleport"
                ));
            }
        }

        admin.openInventory(inv);
    }

    @Override
    public void openCreateHomeFlow(Player player, int slotIndex) {
        openCreateHomeTextInput(player);
    }

    public void openCreateHomeTextInput(Player player) {
        player.closeInventory();
        SignInput.open(player, UnicodeFont.toSmallCaps("Create Home"), "Home", name -> {
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

    /**
     * Exact 9-slot action row UI as shown in Image 2:
     * [Slot 2: Lodestone (Teleport)] [Slot 3: Writable Book (Rename)] [Slot 4: White Bed (Icon)] [Slot 5: Air] [Slot 6: Lava Bucket (Delete)]
     */
    @Override
    public void openHomeManagementMenu(Player player, Home home, boolean isLocked) {
        String title = UnicodeFont.toSmallCaps(home.getName());
        Inventory inv = Bukkit.createInventory(new HomesHolder(HomesHolder.Type.MANAGEMENT, home, isLocked), 9, title);

        if (!isLocked) {
            inv.setItem(2, createItem(Material.LODESTONE, "&f" + UnicodeFont.toSmallCaps("Teleport"), "&f&oClick to teleport"));
            inv.setItem(3, createItem(Material.WRITABLE_BOOK, "&f" + UnicodeFont.toSmallCaps("Rename"), "&f&oClick to rename"));
            inv.setItem(4, createItem(Material.WHITE_BED, "&f" + UnicodeFont.toSmallCaps("Icon"), "&f&oClick to select home icon"));
            inv.setItem(5, createItem(Material.AIR, " "));
            inv.setItem(6, createItem(Material.LAVA_BUCKET, "&c" + UnicodeFont.toSmallCaps("Delete"), "&f&oClick to delete"));
        } else {
            int remaining = home.getRemainingTeleports();
            inv.setItem(2, createItem(Material.LODESTONE, "&f" + UnicodeFont.toSmallCaps("Teleport") + " (" + remaining + " left)", "&f&oClick to teleport"));
            inv.setItem(3, createItem(Material.AIR, " "));
            inv.setItem(4, createItem(Material.AIR, " "));
            inv.setItem(5, createItem(Material.AIR, " "));
            inv.setItem(6, createItem(Material.LAVA_BUCKET, "&c" + UnicodeFont.toSmallCaps("Delete"), "&f&oClick to delete"));
        }

        player.openInventory(inv);
    }

    public void openRenameHomeTextInput(Player player, Home home) {
        player.closeInventory();
        SignInput.open(player, UnicodeFont.toSmallCaps("Rename Home"), home.getName(), name -> {
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
        String title = UnicodeFont.toSmallCaps("Select Icon - Page " + (page + 1));
        Inventory inv = Bukkit.createInventory(new HomesHolder(HomesHolder.Type.ICON_BROWSER, home, isLocked, page, searchQuery, category), 54, title);

        List<Material> filtered = filterMaterials(searchQuery, category);
        int itemsPerPage = 45;
        int totalPages = (filtered.size() + itemsPerPage - 1) / itemsPerPage;
        if (totalPages == 0) totalPages = 1;

        int finalPage = Math.max(0, Math.min(page, totalPages - 1));
        int startIndex = finalPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, filtered.size());

        for (int i = 0; i < 45; i++) {
            int itemIndex = startIndex + i;
            if (itemIndex < endIndex) {
                Material mat = filtered.get(itemIndex);
                inv.setItem(i, createItem(mat, "&a" + UnicodeFont.toSmallCaps(mat.name().replace('_', ' ')), "&7Click to select as home icon"));
            } else {
                inv.setItem(i, createItem(Material.AIR, " "));
            }
        }

        ItemStack border = createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, border);
        }

        inv.setItem(45, createItem(Material.BOOKSHELF, "&e📁 Category: " + category.toUpperCase(), "&7Click to cycle categories"));
        inv.setItem(46, createItem(Material.ANVIL, "&e🔍 Search: &f" + (searchQuery.isEmpty() ? "[None]" : searchQuery), "&7Click to search"));

        if (finalPage > 0) {
            inv.setItem(48, createItem(Material.ARROW, "&a◀ " + UnicodeFont.toSmallCaps("Previous Page")));
        }
        inv.setItem(49, createItem(Material.BOOK, "&bPage " + (finalPage + 1) + " of " + totalPages));
        if (finalPage < totalPages - 1) {
            inv.setItem(50, createItem(Material.ARROW, "&a" + UnicodeFont.toSmallCaps("Next Page") + " ▶"));
        }

        inv.setItem(53, createItem(Material.BARRIER, "&c⬅ " + UnicodeFont.toSmallCaps("Back")));

        player.openInventory(inv);
    }

    public void openIconSearchTextInput(Player player, Home home, boolean isLocked, String category) {
        player.closeInventory();
        SignInput.open(player, UnicodeFont.toSmallCaps("Search Icon"), "", search -> {
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

    /**
     * Delete confirmation with LIME_STAINED_GLASS_PANE for Confirm and RED_STAINED_GLASS_PANE for Cancel
     */
    @Override
    public void openDeleteConfirmation(Player player, Home home) {
        String title = UnicodeFont.toSmallCaps("Delete Home?");
        Inventory inv = Bukkit.createInventory(new HomesHolder(HomesHolder.Type.DELETE_CONFIRM, home, false), 27, title);

        ItemStack border = createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        inv.setItem(11, createItem(Material.LIME_STAINED_GLASS_PANE, "&a&l" + UnicodeFont.toSmallCaps("YES - Confirm Delete"), "&cThis action is permanent!"));
        inv.setItem(15, createItem(Material.RED_STAINED_GLASS_PANE, "&c&l" + UnicodeFont.toSmallCaps("NO - Cancel"), "&7Cancel deletion"));

        player.openInventory(inv);
    }
}
