package me.neonjava.neonhomes.ui;

import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class CheckHomesHolder implements InventoryHolder {
    private final OfflinePlayer target;

    public CheckHomesHolder(OfflinePlayer target) {
        this.target = target;
    }

    public OfflinePlayer getTarget() {
        return target;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
