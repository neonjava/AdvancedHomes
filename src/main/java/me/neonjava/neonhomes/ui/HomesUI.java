package me.neonjava.neonhomes.ui;

import me.neonjava.neonhomes.model.Home;
import org.bukkit.entity.Player;

public interface HomesUI {
    void openMainHomesMenu(Player player);
    void openMainHomesMenu(Player player, int page);
    void openCreateHomeFlow(Player player, int slotIndex);
    void openHomeManagementMenu(Player player, Home home, boolean isLocked);
    void openIconBrowser(Player player, Home home, boolean isLocked, int page, String searchQuery, String category);
    void openDeleteConfirmation(Player player, Home home);
}
