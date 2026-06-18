package me.neonjava.advancedhomes.manager;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatInputManager {
    private final Map<UUID, Consumer<String>> activePrompts = new ConcurrentHashMap<>();

    public void promptPlayer(Player player, Consumer<String> callback) {
        activePrompts.put(player.getUniqueId(), callback);
    }

    public boolean handleChat(UUID playerUuid, String message) {
        Consumer<String> callback = activePrompts.remove(playerUuid);
        if (callback != null) {
            callback.accept(message);
            return true;
        }
        return false;
    }

    public void cancelPrompt(UUID playerUuid) {
        activePrompts.remove(playerUuid);
    }
}
