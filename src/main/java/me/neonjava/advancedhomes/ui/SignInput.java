package me.neonjava.advancedhomes.ui;

import me.neonjava.advancedhomes.AdvancedHomesPlugin;
import io.papermc.paper.math.Position;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.math.BlockPosition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockType;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * Virtual sign-board input GUI that works across all client versions (including 1.21.1).
 * Replaces AnvilInput which breaks on older clients.
 */
@SuppressWarnings("UnstableApiUsage")
public class SignInput implements Listener {

    private static final HashMap<Player, SignSession> sessions = new HashMap<>();
    private static Plugin plugin;

    public record SignSession(Consumer<String> callback, BlockPosition pos) {}

    public SignInput(Plugin plugin) {
        SignInput.plugin = plugin;
    }

    /**
     * Opens a virtual sign GUI for the player.
     *
     * @param player   the player to show the sign to
     * @param lines    4 lines to pre-fill on the sign (line 0 = top, player edits this)
     * @param callback called with the text from line 0 when the player closes the sign
     */
    public static void open(Player player, List<String> lines, Consumer<String> callback) {
        if (sessions.containsKey(player)) return;

        final int x = (int) Math.floor(player.getX());
        int y = (int) Math.ceil(player.getY());
        final int z = (int) Math.floor(player.getZ());
        if (player.getPitch() < 0) {
            y -= 4;
        } else {
            y += 5;
        }

        BlockType blockType = BlockType.OAK_SIGN;
        Sign signState = (Sign) blockType.createBlockData().createBlockState();
        var frontSide = signState.getSide(Side.FRONT);
        for (int i = 0; i < 4; i++) {
            frontSide.line(i, Component.text(lines.get(i)));
        }

        Location loc = new Location(player.getWorld(), x, y, z);
        player.sendBlockChange(loc, signState.getBlockData());
        player.sendBlockUpdate(loc, signState);

        sessions.put(player, new SignSession(callback, Position.block(loc)));

        try {
            player.openSign(signState);
        } catch (Throwable t1) {
            try {
                player.openVirtualSign(Position.block(loc), Side.FRONT);
            } catch (Throwable t2) {
                try {
                    java.lang.reflect.Method m = player.getClass().getMethod("openVirtualSign", Location.class, Side.class);
                    m.invoke(player, loc, Side.FRONT);
                } catch (Throwable t3) {
                    sessions.remove(player);
                    ((AdvancedHomesPlugin) plugin).getChatInputManager().promptPlayer(player, callback);
                    player.sendMessage(org.bukkit.ChatColor.YELLOW + "Please type your home name in chat:");
                }
            }
        }
    }

    /**
     * Convenience method: opens a sign with a title and prompt text on lines 2-3, line 0 for user input.
     */
    public static void open(Player player, String title, String defaultText, Consumer<String> callback) {
        List<String> lines = List.of(
                defaultText != null ? defaultText : "",
                "^^^^^^^^^^^^",
                title,
                ""
        );
        open(player, lines, callback);
    }

    @EventHandler
    public void onSignChange(@NonNull UncheckedSignChangeEvent e) {
        final Player player = e.getPlayer();
        final SignSession session = sessions.get(player);
        if (session == null) return;
        final BlockPosition pos = session.pos();
        if (!e.getEditedBlockPosition().equals(pos)) return;

        final String firstLine = e.lines().isEmpty()
                ? ""
                : PlainTextComponentSerializer.plainText().serialize(e.lines().get(0));
        session.callback().accept(firstLine);

        sessions.remove(player);

        var world = player.getWorld();
        var loc = new Location(world, pos.blockX(), pos.blockY(), pos.blockZ());

        // Restore the original block using the region scheduler (Folia-safe)
        Bukkit.getRegionScheduler().run(plugin, loc, t -> {
            player.sendBlockChange(loc, world.getBlockData(loc));
            if (world.getBlockState(loc) instanceof TileState tile) {
                player.sendBlockUpdate(loc, tile);
            }
        });

        e.setCancelled(true);
    }

    public static void shutdown() {
        sessions.clear();
    }
}
