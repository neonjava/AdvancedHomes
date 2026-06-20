package me.neonjava.advancedhomes.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class AnvilInput implements Listener {
    private static final Map<UUID, AnvilSession> sessions = new HashMap<>();

    public static class AnvilSession {
        private final Player player;
        private final String title;
        private final String initialText;
        private final Consumer<String> callback;
        private final Inventory inventory;
        private final int originalLevel;
        private final float originalExp;

        public AnvilSession(Player player, String title, String initialText, Consumer<String> callback) {
            this.player = player;
            this.title = title;
            this.initialText = initialText == null ? "" : initialText;
            this.callback = callback;
            this.originalLevel = player.getLevel();
            this.originalExp = player.getExp();
            this.inventory = Bukkit.createInventory(player, org.bukkit.event.inventory.InventoryType.ANVIL, Component.text(title));
            
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta meta = paper.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(this.initialText));
                paper.setItemMeta(meta);
            }
            this.inventory.setItem(0, paper);
        }

        public void open() {
            if (player.getLevel() < 5) {
                player.setLevel(5);
            }
            player.openInventory(inventory);
            sessions.put(player.getUniqueId(), this);
        }
    }

    public static void open(Player player, String title, String initialText, Consumer<String> callback) {
        new AnvilSession(player, title, initialText, callback).open();
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent event) {
        Player p = (Player) event.getView().getPlayer();
        AnvilSession session = sessions.get(p.getUniqueId());
        if (session == null || !session.inventory.equals(event.getInventory())) return;

        AnvilInventory anvilInv = event.getInventory();
        ItemStack leftItem = anvilInv.getItem(0);
        if (leftItem == null) return;

        ItemStack result = leftItem.clone();
        event.setResult(result);
        anvilInv.setRepairCost(0);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();
        AnvilSession session = sessions.get(p.getUniqueId());
        if (session == null) return;
        if (!event.getInventory().equals(session.inventory)) return;

        event.setCancelled(true);

        if (event.getRawSlot() == 2) {
            String resultText = null;
            if (event.getInventory() instanceof AnvilInventory anvilInv) {
                resultText = anvilInv.getRenameText();
            }
            if (resultText == null) {
                ItemStack item = event.getInventory().getItem(2);
                if (item != null && item.hasItemMeta()) {
                    resultText = item.getItemMeta().getDisplayName();
                }
            }
            if (resultText == null) resultText = "";

            p.closeInventory();
            sessions.remove(p.getUniqueId());
            p.setLevel(session.originalLevel);
            p.setExp(session.originalExp);
            
            final String finalResult = resultText;
            session.callback.accept(finalResult);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player p = (Player) event.getPlayer();
        AnvilSession session = sessions.remove(p.getUniqueId());
        if (session != null) {
            p.setLevel(session.originalLevel);
            p.setExp(session.originalExp);
        }
    }
}
