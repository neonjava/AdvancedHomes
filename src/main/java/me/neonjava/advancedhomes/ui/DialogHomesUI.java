package me.neonjava.advancedhomes.ui;

import me.neonjava.advancedhomes.AdvancedHomesPlugin;
import me.neonjava.advancedhomes.model.Home;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DialogHomesUI implements HomesUI {
    private final AdvancedHomesPlugin plugin;
    private final ClickCallback.Options defaultOptions;

    public DialogHomesUI(AdvancedHomesPlugin plugin) {
        this.plugin = plugin;
        this.defaultOptions = ClickCallback.Options.builder()
                .lifetime(Duration.ofMinutes(10))
                .uses(ClickCallback.UNLIMITED_USES)
                .build();
    }

    private void runSync(Player player, Runnable runnable) {
        player.getScheduler().run(plugin, task -> runnable.run(), null);
    }

    private String formatMaterialName(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)))
              .append(part.substring(1).toLowerCase(Locale.ROOT))
              .append(" ");
        }
        return sb.toString().trim();
    }

    private String getMaterialEmoji(String material) {
        if (material == null) return "🏡 ";
        switch (material.toUpperCase(Locale.ROOT)) {
            case "RED_BED":
            case "BLUE_BED":
            case "GREEN_BED":
            case "YELLOW_BED":
            case "WHITE_BED":
            case "ORANGE_BED":
            case "MAGENTA_BED":
            case "LIGHT_BLUE_BED":
            case "LIME_BED":
            case "PINK_BED":
            case "GRAY_BED":
            case "LIGHT_GRAY_BED":
            case "CYAN_BED":
            case "PURPLE_BED":
            case "BROWN_BED":
            case "BLACK_BED":
                return "🛏 ";
            case "COMPASS":
                return "🧭 ";
            case "CHEST":
            case "ENDER_CHEST":
            case "BARREL":
            case "SHULKER_BOX":
                return "📦 ";
            case "DIAMOND":
            case "DIAMOND_BLOCK":
                return "💎 ";
            case "EMERALD":
            case "EMERALD_BLOCK":
                return "❇ ";
            case "GOLD_INGOT":
            case "GOLD_BLOCK":
                return "🪙 ";
            case "IRON_INGOT":
            case "IRON_BLOCK":
                return "⚙ ";
            case "ENDER_PEARL":
            case "EYE_OF_ENDER":
                return "🔮 ";
            case "BOOK":
            case "WRITTEN_BOOK":
            case "WRITABLE_BOOK":
            case "ENCHANTED_BOOK":
                return "📖 ";
            case "CRAFTING_TABLE":
            case "FURNACE":
                return "🛠 ";
            case "GRASS_BLOCK":
            case "DIRT":
            case "STONE":
            case "OAK_LOG":
                return "🌱 ";
            case "FIREWORK_ROCKET":
            case "GUNPOWDER":
                return "🎆 ";
            case "IRON_SWORD":
            case "DIAMOND_SWORD":
            case "NETHERITE_SWORD":
            case "GOLDEN_SWORD":
            case "STONE_SWORD":
            case "WOODEN_SWORD":
                return "⚔ ";
            default:
                return "🏡 ";
        }
    }

    private Component parseColor(String text) {
        if (plugin.getConfigManager().isAllowColoredNames()) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        } else {
            return Component.text(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text)));
        }
    }

    private Component getRankCriteriaTooltip(int slotIndex) {
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

        String needRankMsg = plugin.getConfigManager().getMessageNeedRank().replace("<rank>", rankStr);
        String buyRankMsg = plugin.getConfigManager().getMessageBuyRank();
        return LegacyComponentSerializer.legacyAmpersand().deserialize(needRankMsg + "\n" + buyRankMsg);
    }

    @Override
    public void openMainHomesMenu(Player player) {
        List<Home> homes = plugin.getHomeManager().getHomes(player.getUniqueId());
        int limit = plugin.getHomeManager().getPlayerHomeLimit(player);

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int slotIndex = i;
            if (i < homes.size()) {
                Home home = homes.get(i);
                boolean isLocked = i >= limit;
                
                Component label;
                if (isLocked) {
                    label = Component.text("🔒 Locked Home", NamedTextColor.RED);
                } else {
                    label = parseColor(home.getName());
                }

                Component tooltip;
                if (isLocked) {
                    tooltip = Component.text("Home Name: ").append(parseColor(home.getName()))
                            .append(Component.newline())
                            .append(Component.text("STATUS: LOCKED (Upgrade rank to access)", NamedTextColor.RED))
                            .append(Component.newline().append(Component.newline()))
                            .append(getRankCriteriaTooltip(slotIndex));
                } else {
                    tooltip = Component.text("Home Name: ").append(parseColor(home.getName()))
                            .append(Component.newline())
                            .append(Component.text("STATUS: ACTIVE", NamedTextColor.GREEN));
                }

                DialogAction action = DialogAction.customClick((response, audience) -> {
                    runSync(player, () -> {
                        openHomeManagementMenu(player, home, isLocked);
                    });
                }, defaultOptions);

                buttons.add(ActionButton.create(label, tooltip, 120, action));
            } else if (i < limit) {
                Component label = Component.text("+ Create Home", NamedTextColor.GREEN);
                Component tooltip = Component.text("Empty Home Slot. Click to set a home at your current location.");
                DialogAction action = DialogAction.customClick((response, audience) -> {
                    runSync(player, () -> {
                        openCreateHomeFlow(player, slotIndex);
                    });
                }, defaultOptions);
                buttons.add(ActionButton.create(label, tooltip, 120, action));
            } else {
                Component label = Component.text("🔒 Locked Slot", NamedTextColor.DARK_GRAY);
                Component tooltip = getRankCriteriaTooltip(slotIndex);
                DialogAction action = DialogAction.customClick((response, audience) -> {
                    runSync(player, () -> {
                        String teamHomeName;
                        if (slotIndex < homes.size()) {
                            teamHomeName = homes.get(slotIndex).getName();
                        } else {
                            teamHomeName = "home" + (slotIndex + 1);
                        }
                        openTeamHomeDialog(player, teamHomeName, slotIndex);
                    });
                }, defaultOptions);
                buttons.add(ActionButton.create(label, tooltip, 120, action));
            }
        }

        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(DialogBase.builder(Component.text("Homes Menu"))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(DialogBody.plainMessage(Component.text("Click a home slot to manage it:"))))
                    .build());
            builder.type(DialogType.multiAction(buttons, ActionButton.create(Component.text("Close"), null, 100, null), 5));
        });

        player.showDialog(dialog);
    }

    @Override
    public void openCreateHomeFlow(Player player, int slotIndex) {
        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(DialogBase.builder(Component.text("Create Home"))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(DialogBody.plainMessage(Component.text("Create a home at your current location?"))))
                    .build());

            ActionButton yesBtn = ActionButton.create(Component.text("Create Home", NamedTextColor.GREEN), null, 120,
                    DialogAction.customClick((res, aud) -> {
                        runSync(player, () -> {
                            openCreateHomeTextInput(player);
                        });
                    }, defaultOptions));

            ActionButton noBtn = ActionButton.create(Component.text("Cancel", NamedTextColor.RED), null, 120,
                    DialogAction.customClick((res, aud) -> {
                        runSync(player, () -> {
                            openMainHomesMenu(player);
                        });
                    }, defaultOptions));

            builder.type(DialogType.confirmation(yesBtn, noBtn));
        });

        player.showDialog(dialog);
    }

    private void openCreateHomeTextInput(Player player) {
        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(DialogBase.builder(Component.text("Home Name Input"))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(DialogBody.plainMessage(Component.text("Type your home name below:"))))
                    .inputs(List.of(DialogInput.text("name", Component.text("Home Name"))
                            .initial("Home")
                            .maxLength(16)
                            .build()))
                    .build());

            ActionButton confirmBtn = ActionButton.create(Component.text("Confirm", NamedTextColor.GREEN), null, 120,
                    DialogAction.customClick((response, audience) -> {
                        String name = response.getText("name");
                        runSync(player, () -> {
                            if (name == null || name.trim().isEmpty()) {
                                player.sendMessage(ChatColor.RED + "Home name cannot be empty.");
                                openCreateHomeTextInput(player);
                                return;
                            }
                            plugin.getHomeManager().addHome(player, name.trim());
                        });
                    }, defaultOptions));

            ActionButton cancelBtn = ActionButton.create(Component.text("Cancel", NamedTextColor.RED), null, 120,
                    DialogAction.customClick((response, audience) -> {
                        runSync(player, () -> {
                            openMainHomesMenu(player);
                        });
                    }, defaultOptions));

            builder.type(DialogType.confirmation(confirmBtn, cancelBtn));
        });

        player.showDialog(dialog);
    }

    @Override
    public void openHomeManagementMenu(Player player, Home home, boolean isLocked) {
        List<ActionButton> buttons = new ArrayList<>();

        // Teleport button (added for both unlocked and locked homes!)
        String teleportLabel = "⚡ Teleport to Home";
        if (isLocked) {
            int remaining = home.getRemainingTeleports();
            teleportLabel += " (" + remaining + " left)";
        }
        buttons.add(ActionButton.create(Component.text(teleportLabel), Component.text("Click to teleport"), 120,
                DialogAction.customClick((res, aud) -> {
                    runSync(player, () -> {
                        player.closeInventory();
                        if (isLocked) {
                            int remaining = home.getRemainingTeleports();
                            if (remaining <= 0) {
                                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessageHomeExpired()));
                                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessageNeedRankTeleport()));
                                player.sendMessage(ChatColor.YELLOW + "Coordinates of '" + home.getName() + "': X: " + (int)home.getX() + ", Y: " + (int)home.getY() + ", Z: " + (int)home.getZ() + " in world: " + home.getWorldName());
                                return;
                            }
                            
                            int nextRemaining = remaining - 1;
                            home.setRemainingTeleports(nextRemaining);
                            plugin.getDatabaseManager().updateRemainingTeleports(player.getUniqueId(), home.getName(), nextRemaining);
                            player.sendMessage(ChatColor.GOLD + "Warning: You can teleport to this home only " + nextRemaining + " more times before it expires!");
                        }
                        plugin.getHomeManager().teleportPlayer(player, home);
                    });
                }, defaultOptions)));

        if (isLocked) {
            buttons.add(ActionButton.create(Component.text("🚩 Set Team Home", NamedTextColor.GREEN), Component.text("Run /team sethome " + home.getName()), 120,
                    DialogAction.customClick((res, aud) -> {
                        runSync(player, () -> {
                            player.closeInventory();
                            player.performCommand("team sethome " + home.getName());
                        });
                    }, defaultOptions)));

            buttons.add(ActionButton.create(Component.text("⚡ Team Teleport", NamedTextColor.YELLOW), Component.text("Run /team home " + home.getName()), 120,
                    DialogAction.customClick((res, aud) -> {
                        runSync(player, () -> {
                            player.closeInventory();
                            player.performCommand("team home " + home.getName());
                        });
                    }, defaultOptions)));

            buttons.add(ActionButton.create(Component.text("❌ Team Delete", NamedTextColor.RED), Component.text("Run /team delhome " + home.getName()), 120,
                    DialogAction.customClick((res, aud) -> {
                        runSync(player, () -> {
                            player.closeInventory();
                            player.performCommand("team delhome " + home.getName());
                        });
                    }, defaultOptions)));
        }

        if (!isLocked) {
            // Rename button
            buttons.add(ActionButton.create(Component.text("✏ Rename Home"), Component.text("Change the home name"), 120,
                    DialogAction.customClick((res, aud) -> {
                        runSync(player, () -> {
                            openRenameHomeTextInput(player, home);
                        });
                    }, defaultOptions)));
        }

        // Delete button (accessible even if locked!)
        buttons.add(ActionButton.create(Component.text("❌ Delete Home", NamedTextColor.RED), Component.text("Permanently delete this home"), 120,
                DialogAction.customClick((res, aud) -> {
                    runSync(player, () -> {
                        openDeleteConfirmation(player, home);
                    });
                }, defaultOptions)));

        // Back button
        buttons.add(ActionButton.create(Component.text("⬅ Back"), Component.text("Return to homes list"), 120,
                DialogAction.customClick((res, aud) -> {
                    runSync(player, () -> {
                        openMainHomesMenu(player);
                    });
                }, defaultOptions)));

        Component title = isLocked ? Component.text("Manage Home (Locked by Rank)") : Component.text("Manage Home: ").append(parseColor(home.getName()));

        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(DialogBase.builder(title)
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(
                            DialogBody.plainMessage(Component.text(isLocked ? "⚠️ LOCKED: Limited teleports remaining or delete home." : "Select an action below:"))
                    ))
                    .build());
            builder.type(DialogType.multiAction(buttons, null, 1));
        });

        player.showDialog(dialog);
    }

    private void openRenameHomeTextInput(Player player, Home home) {
        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(DialogBase.builder(Component.text("Rename Home"))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(DialogBody.plainMessage(Component.text("Enter new name for home '" + home.getName() + "':"))))
                    .inputs(List.of(DialogInput.text("name", Component.text("New Name"))
                            .initial(home.getName())
                            .maxLength(16)
                            .build()))
                    .build());

            ActionButton confirmBtn = ActionButton.create(Component.text("Confirm", NamedTextColor.GREEN), null, 120,
                    DialogAction.customClick((response, audience) -> {
                        String name = response.getText("name");
                        runSync(player, () -> {
                            if (name == null || name.trim().isEmpty()) {
                                player.sendMessage(ChatColor.RED + "Home name cannot be empty.");
                                openRenameHomeTextInput(player, home);
                                return;
                            }
                            plugin.getHomeManager().renameHome(player, home.getName(), name.trim());
                            openMainHomesMenu(player);
                        });
                    }, defaultOptions));

            ActionButton cancelBtn = ActionButton.create(Component.text("Cancel", NamedTextColor.RED), null, 120,
                    DialogAction.customClick((response, audience) -> {
                        runSync(player, () -> {
                            openHomeManagementMenu(player, home, false);
                        });
                    }, defaultOptions));

            builder.type(DialogType.confirmation(confirmBtn, cancelBtn));
        });

        player.showDialog(dialog);
    }

    @Override
    public void openIconBrowser(Player player, Home home, boolean isLocked, int page, String searchQuery, String category) {
        List<Material> filtered = filterMaterials(searchQuery, category);
        int itemsPerPage = 8;
        int totalPagesCalc = (filtered.size() + itemsPerPage - 1) / itemsPerPage;
        if (totalPagesCalc == 0) totalPagesCalc = 1;
        final int totalPages = totalPagesCalc;

        int finalPage = Math.max(0, Math.min(page, totalPages - 1));
        int startIndex = finalPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, filtered.size());

        List<ActionButton> buttons = new ArrayList<>();

        // Add search box query display
        Component queryInfo = Component.text("Search: " + (searchQuery.isEmpty() ? "[None]" : searchQuery) + " | Category: " + category.toUpperCase());
        
        // Match items
        for (int i = startIndex; i < endIndex; i++) {
            Material mat = filtered.get(i);
            DialogAction selectAction = DialogAction.customClick((res, aud) -> {
                runSync(player, () -> {
                    plugin.getHomeManager().updateIcon(player, home.getName(), mat.name());
                    openHomeManagementMenu(player, home, isLocked);
                });
            }, defaultOptions);
            buttons.add(ActionButton.create(Component.translatable(mat.translationKey()), Component.text("Select this material as your home icon"), 120, selectAction));
        }

        // Search options button (opens search text input)
        buttons.add(ActionButton.create(Component.text("🔍 Search Query", NamedTextColor.YELLOW), Component.text("Enter text search query"), 120,
                DialogAction.customClick((res, aud) -> {
                    runSync(player, () -> {
                        openSearchTextInput(player, home, isLocked, category);
                    });
                }, defaultOptions)));

        // Category filter buttons
        buttons.add(ActionButton.create(Component.text("📁 Category: " + category.toUpperCase(), NamedTextColor.YELLOW), Component.text("Cycle categories: ALL -> BLOCKS -> FOOD -> TOOLS -> MISC"), 120,
                DialogAction.customClick((res, aud) -> {
                    runSync(player, () -> {
                        String nextCategory = getNextCategory(category);
                        openIconBrowser(player, home, isLocked, 0, searchQuery, nextCategory);
                    });
                }, defaultOptions)));

        // Navigation
        if (finalPage > 0) {
            buttons.add(ActionButton.create(Component.text("◀ Previous Page"), null, 120,
                    DialogAction.customClick((res, aud) -> {
                        runSync(player, () -> {
                            openIconBrowser(player, home, isLocked, finalPage - 1, searchQuery, category);
                        });
                    }, defaultOptions)));
        }

        if (finalPage < totalPages - 1) {
            buttons.add(ActionButton.create(Component.text("Next Page ▶"), null, 120,
                    DialogAction.customClick((res, aud) -> {
                        runSync(player, () -> {
                            openIconBrowser(player, home, isLocked, finalPage + 1, searchQuery, category);
                        });
                    }, defaultOptions)));
        }

        buttons.add(ActionButton.create(Component.text("⬅ Back"), null, 120,
                DialogAction.customClick((res, aud) -> {
                    runSync(player, () -> {
                        openHomeManagementMenu(player, home, isLocked);
                    });
                }, defaultOptions)));

        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(DialogBase.builder(Component.text("Select Icon Material"))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(
                            DialogBody.plainMessage(queryInfo),
                            DialogBody.plainMessage(Component.text("Page " + (finalPage + 1) + " of " + totalPages + " (Total: " + filtered.size() + " items)"))
                    ))
                    .build());
            builder.type(DialogType.multiAction(buttons, null, 2));
        });

        player.showDialog(dialog);
    }

    private void openSearchTextInput(Player player, Home home, boolean isLocked, String category) {
        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(DialogBase.builder(Component.text("Search Icon Browser"))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(DialogBody.plainMessage(Component.text("Enter search query:"))))
                    .inputs(List.of(DialogInput.text("query", Component.text("Search Query"))
                            .initial("")
                            .maxLength(20)
                            .build()))
                    .build());

            ActionButton confirmBtn = ActionButton.create(Component.text("Search", NamedTextColor.GREEN), null, 120,
                    DialogAction.customClick((res, aud) -> {
                        String query = res.getText("query");
                        runSync(player, () -> {
                            openIconBrowser(player, home, isLocked, 0, query == null ? "" : query.trim(), category);
                        });
                    }, defaultOptions));

            ActionButton cancelBtn = ActionButton.create(Component.text("Cancel", NamedTextColor.RED), null, 120,
                    DialogAction.customClick((res, aud) -> {
                        runSync(player, () -> {
                            openIconBrowser(player, home, isLocked, 0, "", category);
                        });
                    }, defaultOptions));

            builder.type(DialogType.confirmation(confirmBtn, cancelBtn));
        });

        player.showDialog(dialog);
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

    @Override
    public void openDeleteConfirmation(Player player, Home home) {
        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(DialogBase.builder(Component.text("Confirm Deletion"))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(DialogBody.plainMessage(Component.text("Delete Home '" + home.getName() + "'? This action is permanent!"))))
                    .build());

            ActionButton yesBtn = ActionButton.create(Component.text("YES - Delete", NamedTextColor.GREEN), null, 120,
                    DialogAction.customClick((res, aud) -> {
                        runSync(player, () -> {
                            plugin.getHomeManager().deleteHome(player, home.getName());
                        });
                    }, defaultOptions));

            ActionButton noBtn = ActionButton.create(Component.text("NO - Keep", NamedTextColor.RED), null, 120,
                    DialogAction.customClick((res, aud) -> {
                        runSync(player, () -> {
                            openHomeManagementMenu(player, home, false);
                        });
                    }, defaultOptions));

            builder.type(DialogType.confirmation(yesBtn, noBtn));
        });

        player.showDialog(dialog);
    }

    public void openTeamHomeDialog(Player player, String homeName, int slotIndex) {
        List<ActionButton> buttons = new ArrayList<>();

        buttons.add(ActionButton.create(Component.text("🚩 Set Team Home", NamedTextColor.GREEN), Component.text("Run /team sethome " + homeName), 120,
                DialogAction.customClick((res, aud) -> {
                    runSync(player, () -> {
                        player.closeInventory();
                        player.performCommand("team sethome " + homeName);
                    });
                }, defaultOptions)));

        buttons.add(ActionButton.create(Component.text("⚡ Team Teleport", NamedTextColor.YELLOW), Component.text("Run /team home " + homeName), 120,
                DialogAction.customClick((res, aud) -> {
                    runSync(player, () -> {
                        player.closeInventory();
                        player.performCommand("team home " + homeName);
                    });
                }, defaultOptions)));

        buttons.add(ActionButton.create(Component.text("❌ Team Delete", NamedTextColor.RED), Component.text("Run /team delhome " + homeName), 120,
                DialogAction.customClick((res, aud) -> {
                    runSync(player, () -> {
                        player.closeInventory();
                        player.performCommand("team delhome " + homeName);
                    });
                }, defaultOptions)));

        buttons.add(ActionButton.create(Component.text("⬅ Back"), Component.text("Return to homes list"), 120,
                DialogAction.customClick((res, aud) -> {
                    runSync(player, () -> {
                        openMainHomesMenu(player);
                    });
                }, defaultOptions)));

        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(DialogBase.builder(Component.text("Team Home Options"))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(
                            DialogBody.plainMessage(Component.text("Manage team home for slot: " + (slotIndex + 1))),
                            DialogBody.plainMessage(getRankCriteriaTooltip(slotIndex))
                    ))
                    .build());
            builder.type(DialogType.multiAction(buttons, ActionButton.create(Component.text("Close"), null, 100, null), 2));
        });

        player.showDialog(dialog);
    }
}
