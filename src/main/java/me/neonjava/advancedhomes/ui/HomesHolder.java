package me.neonjava.advancedhomes.ui;

import me.neonjava.advancedhomes.model.Home;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class HomesHolder implements InventoryHolder {
    private final Type type;
    private final Home home;
    private final boolean isLocked;
    private final int page;
    private final String searchQuery;
    private final String category;
    private final int slotIndex;

    public enum Type {
        MAIN, CREATE, MANAGEMENT, ICON_BROWSER, DELETE_CONFIRM
    }

    public HomesHolder(Type type) {
        this(type, null, false, 0, "", "all", -1);
    }

    public HomesHolder(Type type, int slotIndex) {
        this(type, null, false, 0, "", "all", slotIndex);
    }

    public HomesHolder(Type type, Home home, boolean isLocked) {
        this(type, home, isLocked, 0, "", "all", -1);
    }

    public HomesHolder(Type type, Home home, boolean isLocked, int page, String searchQuery, String category) {
        this(type, home, isLocked, page, searchQuery, category, -1);
    }

    private HomesHolder(Type type, Home home, boolean isLocked, int page, String searchQuery, String category, int slotIndex) {
        this.type = type;
        this.home = home;
        this.isLocked = isLocked;
        this.page = page;
        this.searchQuery = searchQuery;
        this.category = category;
        this.slotIndex = slotIndex;
    }

    public Type getType() {
        return type;
    }

    public Home getHome() {
        return home;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public int getPage() {
        return page;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public String getCategory() {
        return category;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
