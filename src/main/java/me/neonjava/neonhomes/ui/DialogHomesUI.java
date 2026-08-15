package me.neonjava.neonhomes.ui;

import me.neonjava.neonhomes.NeonHomesPlugin;
import me.neonjava.neonhomes.config.ConfigManager;
import me.neonjava.neonhomes.model.Home;
import me.neonjava.neonhomes.util.UnicodeFont;
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
    private final NeonHomesPlugin plugin;
    private final ClickCallback.Options defaultOptions;

    public DialogHomesUI(NeonHomesPlugin plugin) {
        this.plugin = plugin;
        this.defaultOptions = ClickCallback.Options.builder()
                .lifetime(Duration.ofMinutes(10))
                .uses(ClickCallback.UNLIMITED_USES)
                .build();
    }

    private void runSync(Player player, Runnable runnable) {
        player.getScheduler().run(plugin, task -> runnable.run(), null);
    }

    private Component parseColor(String text) {
        if (plugin.getConfigManager().isAllowColoredNames()) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        } else {
            return Component.text(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text)));
        }
    }

    private Component getLockedTooltip(int slotIndex) {
        ConfigManager.RankRequirement req = plugin.getConfigManager().getRankRequirementForSlot(slotIndex);
        String msg = plugin.getConfigManager().getMessageLockedTooltip()
                .replace("<rank>", req.getRankName())
                .replace("<slots>", String.valueOf(req.getSlots()));
        return LegacyComponentSerializer.legacyAmpersand().deserialize(msg);
    }

    @Override
    public void openMainHomesMenu(Player player) {
        openMainHomesMenu(player, 0);
    }

    /**
     * Paper Dialog Main Homes selection menu:
     * Displays home slots up to the visible limit (defaults to player limit + next rank step)
     * with an extra "+ Show More" button to expand to the next rank tier until maxHomes.
     */
    @Override
    public void openMainHomesMenu(Player player, int visibleSlots) {
        List<Home> homes = plugin.getHomeManager().getHomes(player.getUniqueId());
        int limit = plugin.getHomeManager().getPlayerHomeLimit(player);
        int maxHomes = plugin.getConfigManager().getMaxHomes();

        // If visibleSlots is 0 (default call), only show the slots the player can access (player limit)
        if (visibleSlots <= 0) {
            visibleSlots = limit;
        }
        visibleSlots = Math.min(visibleSlots, maxHomes);

        List<ActionButton> buttons = new ArrayList<>();

        for (int i = 0; i < visibleSlots; i++) {
            final int slotIndex = i;
            if (i < homes.size()) {
                Home home = homes.get(i);
                boolean isLocked = i >= limit;

                Component label = isLocked
                        ? Component.text(UnicodeFont.toSmallCaps("Locked"), NamedTextColor.RED)
                        : parseColor(home.getName());
                Component tooltip = isLocked ? getLockedTooltip(slotIndex) : Component.text(home.getName() + "\nClick to manage");

                DialogAction action = DialogAction.customClick((response, audience) -> {
                    runSync(player, () -> {
                        openHomeManagementMenu(player, home, isLocked, slotIndex);
                    });
                }, defaultOptions);

                buttons.add(ActionButton.create(label, tooltip, 75, action));
            } else if (i < limit) {
                Component label = Component.text("+ " + UnicodeFont.toSmallCaps("New Home"), NamedTextColor.GREEN);
                Component tooltip = Component.text("Empty Home Slot. Click to set a home at your current location.");
                DialogAction action = DialogAction.customClick((response, audience) -> {
                    runSync(player, () -> {
                        openCreateHomeTextInput(player);
                    });
                }, defaultOptions);
                buttons.add(ActionButton.create(label, tooltip, 75, action));
            } else {
                Component label = Component.text(UnicodeFont.toSmallCaps("Locked"), NamedTextColor.RED);
                Component tooltip = getLockedTooltip(slotIndex);
                DialogAction action = DialogAction.customClick((response, audience) -> {
                    runSync(player, () -> {
                        ConfigManager.RankRequirement req = plugin.getConfigManager().getRankRequirementForSlot(slotIndex);
                        String warning = plugin.getConfigManager().getMessageBuyRanksWarning()
                                .replace("<rank>", req.getRankName())
                                .replace("<slots>", String.valueOf(req.getSlots()));
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', warning));
                    });
                }, defaultOptions);
                buttons.add(ActionButton.create(label, tooltip, 75, action));
            }
        }

        // If there are more slots available to reveal up to maxHomes, add the "Show More" button
        if (visibleSlots < maxHomes) {
            int nextVisible = maxHomes;
            java.util.Map<String, Integer> ranks = plugin.getConfigManager().getRanks();
            for (int rankCap : ranks.values()) {
                if (rankCap > visibleSlots) {
                    nextVisible = Math.min(rankCap, maxHomes);
                    break;
                }
            }
            final int targetSlots = nextVisible;

            Component showMoreLabel = Component.text("▶ " + UnicodeFont.toSmallCaps("Show More"), NamedTextColor.YELLOW);
            Component showMoreTooltip = Component.text("Click to reveal more rank slots (up to " + targetSlots + " slots)");
            DialogAction showMoreAction = DialogAction.customClick((response, audience) -> {
                runSync(player, () -> {
                    openMainHomesMenu(player, targetSlots);
                });
            }, defaultOptions);

            buttons.add(ActionButton.create(showMoreLabel, showMoreTooltip, 85, showMoreAction));
        }

        String title = UnicodeFont.toSmallCaps("Homes");
        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(DialogBase.builder(Component.text(title))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(DialogBody.plainMessage(Component.text("Select a home slot:"))))
                    .build());
            // 6 columns
            builder.type(DialogType.multiAction(buttons, null, 6));
        });

        player.showDialog(dialog);
    }

    @Override
    public void openCreateHomeFlow(Player player, int slotIndex) {
        openCreateHomeTextInput(player);
    }

    private void openCreateHomeTextInput(Player player) {
        String defaultName = plugin.getHomeManager().getNextAvailableHomeName(player.getUniqueId());
        String title = UnicodeFont.toSmallCaps("Create Home");
        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(DialogBase.builder(Component.text(title))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(DialogBody.plainMessage(Component.text("Type your home name below:"))))
                    .inputs(List.of(DialogInput.text("name", Component.text("Home Name"))
                            .initial(defaultName)
                            .maxLength(16)
                            .build()))
                    .build());

            ActionButton confirmBtn = ActionButton.create(Component.text(UnicodeFont.toSmallCaps("Confirm"), NamedTextColor.GREEN), null, 120,
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

            ActionButton cancelBtn = ActionButton.create(Component.text(UnicodeFont.toSmallCaps("Cancel"), NamedTextColor.RED), null, 120,
                    DialogAction.customClick((response, audience) -> {
                        runSync(player, () -> {
                            openMainHomesMenu(player);
                        });
                    }, defaultOptions));

            builder.type(DialogType.confirmation(confirmBtn, cancelBtn));
        });

        player.showDialog(dialog);
    }

    /**
     * Dialog Management Menu (Image 3 format):
     * [ Teleport ] [ Change Icon ]
     * [ Rename   ] [ Delete      ]
     *        [ Back ]
     */
    @Override
    public void openHomeManagementMenu(Player player, Home home, boolean isLocked) {
        openHomeManagementMenu(player, home, isLocked, -1);
    }

    public void openHomeManagementMenu(Player player, Home home, boolean isLocked, int slotIndex) {
        List<ActionButton> buttons = new ArrayList<>();

        String teleportLabel = UnicodeFont.toSmallCaps("Teleport");
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
                            ConfigManager.RankRequirement req = slotIndex >= 0 
                                    ? plugin.getConfigManager().getRankRequirementForSlot(slotIndex)
                                    : new ConfigManager.RankRequirement("VIP", 5);

                            if (remaining <= 0) {
                                String noChancesMsg = plugin.getConfigManager().getMessageHomeNoChances()
                                        .replace("<rank>", req.getRankName())
                                        .replace("<slots>", String.valueOf(req.getSlots()));
                                player.sendMessage(ChatColor.translateAlternateColorCodes('&', noChancesMsg));
                                return;
                            }
                            int nextRemaining = remaining - 1;
                            home.setRemainingTeleports(nextRemaining);
                            plugin.getDatabaseManager().updateRemainingTeleports(player.getUniqueId(), home.getName(), nextRemaining);

                            String expiredMsg = plugin.getConfigManager().getMessageHomeExpired()
                                    .replace("<remaining>", String.valueOf(nextRemaining))
                                    .replace("<rank>", req.getRankName());
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&', expiredMsg));
                        }
                        plugin.getHomeManager().teleportPlayer(player, home);
                    });
                }, defaultOptions)));

        if (!isLocked) {
            buttons.add(ActionButton.create(Component.text(UnicodeFont.toSmallCaps("Rename")), Component.text("Click to rename"), 120,
                    DialogAction.customClick((res, aud) -> {
                        runSync(player, () -> {
                            openRenameHomeTextInput(player, home);
                        });
                    }, defaultOptions)));
        }

        buttons.add(ActionButton.create(Component.text(UnicodeFont.toSmallCaps("Delete"), NamedTextColor.RED), Component.text("Click to delete"), 120,
                DialogAction.customClick((res, aud) -> {
                    runSync(player, () -> {
                        openDeleteConfirmation(player, home);
                    });
                }, defaultOptions)));

        buttons.add(ActionButton.create(Component.text(UnicodeFont.toSmallCaps("Back")), Component.text("Return to homes list"), 120,
                DialogAction.customClick((res, aud) -> {
                    runSync(player, () -> {
                        openMainHomesMenu(player);
                    });
                }, defaultOptions)));

        Component title = isLocked ? Component.text(UnicodeFont.toSmallCaps(home.getName()) + " (" + UnicodeFont.toSmallCaps("Locked") + ")") : parseColor(home.getName());

        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(DialogBase.builder(title)
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(DialogBody.plainMessage(Component.text("Select an option:"))))
                    .build());
            builder.type(DialogType.multiAction(buttons, null, 2));
        });

        player.showDialog(dialog);
    }

    private void openRenameHomeTextInput(Player player, Home home) {
        String title = UnicodeFont.toSmallCaps("Rename Home");
        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(DialogBase.builder(Component.text(title))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(DialogBody.plainMessage(Component.text("Enter new name for home '" + home.getName() + "':"))))
                    .inputs(List.of(DialogInput.text("name", Component.text("New Name"))
                            .initial(home.getName())
                            .maxLength(16)
                            .build()))
                    .build());

            ActionButton confirmBtn = ActionButton.create(Component.text(UnicodeFont.toSmallCaps("Confirm"), NamedTextColor.GREEN), null, 120,
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

            ActionButton cancelBtn = ActionButton.create(Component.text(UnicodeFont.toSmallCaps("Cancel"), NamedTextColor.RED), null, 120,
                    DialogAction.customClick((response, audience) -> {
                        runSync(player, () -> {
                            openHomeManagementMenu(player, home, false);
                        });
                    }, defaultOptions));

            builder.type(DialogType.confirmation(confirmBtn, cancelBtn));
        });

        player.showDialog(dialog);
    }

    /**
     * Huge full-screen Icon Selection Grid in 4 columns (Image 5 format):
     * Top Controls: [ Search ] [ Default ] [ Back ]
     * Followed by 4-column material buttons!
     */
    @Override
    public void openIconBrowser(Player player, Home home, boolean isLocked, int page, String searchQuery, String category) {
        List<Material> filtered = filterMaterials(searchQuery, category);
        List<ActionButton> buttons = new ArrayList<>();

        // Top Control Buttons (Image 5 format)
        buttons.add(ActionButton.create(Component.text(UnicodeFont.toSmallCaps("Search")), Component.text("Search materials"), 120,
                DialogAction.customClick((res, aud) -> {
                    runSync(player, () -> {
                        openSearchTextInput(player, home, isLocked, category);
                    });
                }, defaultOptions)));

        buttons.add(ActionButton.create(Component.text(UnicodeFont.toSmallCaps("Default")), Component.text("Reset filter"), 120,
                DialogAction.customClick((res, aud) -> {
                    runSync(player, () -> {
                        openIconBrowser(player, home, isLocked, 0, "", "all");
                    });
                }, defaultOptions)));

        buttons.add(ActionButton.create(Component.text(UnicodeFont.toSmallCaps("Back")), Component.text("Return to home"), 120,
                DialogAction.customClick((res, aud) -> {
                    runSync(player, () -> {
                        openHomeManagementMenu(player, home, isLocked);
                    });
                }, defaultOptions)));

        // Add empty spacer button for 4-column alignment
        buttons.add(ActionButton.create(Component.text(" "), null, 120, DialogAction.customClick((r, a) -> {}, defaultOptions)));

        // Add all filtered materials in 4 columns (Image 5 format)
        for (Material mat : filtered) {
            String iconIndicator = plugin.getItemsDatabase().getIconIndicator(mat);
            String displayName = UnicodeFont.toSmallCaps(mat.name().replace('_', ' ').toLowerCase(Locale.ROOT));
            DialogAction selectAction = DialogAction.customClick((res, aud) -> {
                runSync(player, () -> {
                    plugin.getHomeManager().updateIcon(player, home.getName(), mat.name());
                    Home updated = plugin.getHomeManager().getHome(player.getUniqueId(), home.getName());
                    openHomeManagementMenu(player, updated != null ? updated : home, isLocked);
                });
            }, defaultOptions);
            Component label = Component.text(iconIndicator + " ").append(Component.translatable(mat.translationKey()));
            buttons.add(ActionButton.create(label, Component.text(displayName + "\nClick to select"), 120, selectAction));
        }

        String title = UnicodeFont.toSmallCaps("Choose Icon");

        Material currentMat = Material.matchMaterial(home.getIconMaterial());
        if (currentMat == null) currentMat = Material.WHITE_BED;
        org.bukkit.inventory.ItemStack currentItem = new org.bukkit.inventory.ItemStack(currentMat);

        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(DialogBase.builder(Component.text(title))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(
                            DialogBody.item(currentItem).showDecorations(true).showTooltip(true).build(),
                            DialogBody.plainMessage(Component.text("Select an icon below:"))
                    ))
                    .build());
            // 4 columns as in Image 5
            builder.type(DialogType.multiAction(buttons, null, 4));
        });

        player.showDialog(dialog);
    }

    private void openSearchTextInput(Player player, Home home, boolean isLocked, String category) {
        String title = UnicodeFont.toSmallCaps("Search Icon");
        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(DialogBase.builder(Component.text(title))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(DialogBody.plainMessage(Component.text("Type material name to search:"))))
                    .inputs(List.of(DialogInput.text("query", Component.text("Search"))
                            .initial("")
                            .maxLength(20)
                            .build()))
                    .build());

            ActionButton confirmBtn = ActionButton.create(Component.text(UnicodeFont.toSmallCaps("Search"), NamedTextColor.GREEN), null, 120,
                    DialogAction.customClick((response, audience) -> {
                        String q = response.getText("query");
                        runSync(player, () -> {
                            openIconBrowser(player, home, isLocked, 0, q != null ? q.trim() : "", category);
                        });
                    }, defaultOptions));

            ActionButton cancelBtn = ActionButton.create(Component.text(UnicodeFont.toSmallCaps("Cancel"), NamedTextColor.RED), null, 120,
                    DialogAction.customClick((response, audience) -> {
                        runSync(player, () -> {
                            openIconBrowser(player, home, isLocked, 0, "", category);
                        });
                    }, defaultOptions));

            builder.type(DialogType.confirmation(confirmBtn, cancelBtn));
        });

        player.showDialog(dialog);
    }

    private List<Material> filterMaterials(String query, String category) {
        List<Material> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (Material mat : Material.values()) {
            if (!mat.isItem() || mat.isAir()) continue;
            String name = mat.name().toLowerCase(Locale.ROOT);
            if (!name.contains(lowerQuery)) continue;
            results.add(mat);
        }
        return results;
    }

    @Override
    public void openDeleteConfirmation(Player player, Home home) {
        String title = UnicodeFont.toSmallCaps("Confirm Deletion");
        Dialog dialog = Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(DialogBase.builder(Component.text(title))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(DialogBody.plainMessage(Component.text("Delete Home '" + home.getName() + "'?"))))
                    .build());

            ActionButton yesBtn = ActionButton.create(Component.text(UnicodeFont.toSmallCaps("YES - Delete"), NamedTextColor.GREEN), null, 120,
                    DialogAction.customClick((res, aud) -> {
                        runSync(player, () -> {
                            plugin.getHomeManager().deleteHome(player, home.getName());
                            openMainHomesMenu(player);
                        });
                    }, defaultOptions));

            ActionButton noBtn = ActionButton.create(Component.text(UnicodeFont.toSmallCaps("NO - Cancel"), NamedTextColor.RED), null, 120,
                    DialogAction.customClick((res, aud) -> {
                        runSync(player, () -> {
                            openHomeManagementMenu(player, home, false);
                        });
                    }, defaultOptions));

            builder.type(DialogType.confirmation(yesBtn, noBtn));
        });

        player.showDialog(dialog);
    }
}
