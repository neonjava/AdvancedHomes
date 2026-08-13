package me.neonjava.neonhomes.listener;

import me.neonjava.neonhomes.NeonHomesPlugin;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;

public class PaperChatListener implements Listener {
    private final NeonHomesPlugin plugin;

    public PaperChatListener(NeonHomesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPaperChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
        if (plugin.getChatInputManager().handleChat(player.getUniqueId(), message)) {
            event.setCancelled(true);
        }
    }
}
