package me.neonjava.advancedhomes.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads items.json resource and provides custom font icon characters
 * mapped to Minecraft block/item textures via a resource pack font.
 *
 * Font: advancedhomes:icons
 * Each material is mapped to a Private Use Area Unicode char (\uE001-\uE0FF)
 * that references the vanilla texture via the custom font.
 */
public class ItemsDatabase {

    /** The custom font key for item icons */
    public static final Key ICON_FONT = Key.key("advancedhomes", "icons");

    /** Material name -> Unicode PUA character for font rendering */
    private static final Map<String, String> FONT_CHARS = new LinkedHashMap<>();

    static {
        // Beds
        FONT_CHARS.put("WHITE_BED", "\uE001");
        FONT_CHARS.put("ORANGE_BED", "\uE001");
        FONT_CHARS.put("MAGENTA_BED", "\uE001");
        FONT_CHARS.put("LIGHT_BLUE_BED", "\uE001");
        FONT_CHARS.put("YELLOW_BED", "\uE001");
        FONT_CHARS.put("LIME_BED", "\uE001");
        FONT_CHARS.put("PINK_BED", "\uE001");
        FONT_CHARS.put("GRAY_BED", "\uE001");
        FONT_CHARS.put("LIGHT_GRAY_BED", "\uE001");
        FONT_CHARS.put("CYAN_BED", "\uE001");
        FONT_CHARS.put("PURPLE_BED", "\uE001");
        FONT_CHARS.put("BLUE_BED", "\uE001");
        FONT_CHARS.put("BROWN_BED", "\uE001");
        FONT_CHARS.put("GREEN_BED", "\uE001");
        FONT_CHARS.put("RED_BED", "\uE001");
        FONT_CHARS.put("BLACK_BED", "\uE001");

        // Blocks
        FONT_CHARS.put("COBBLESTONE", "\uE002");
        FONT_CHARS.put("MOSSY_COBBLESTONE", "\uE002");
        FONT_CHARS.put("STONE", "\uE003");
        FONT_CHARS.put("STONE_BRICKS", "\uE003");
        FONT_CHARS.put("MOSSY_STONE_BRICKS", "\uE003");
        FONT_CHARS.put("CRACKED_STONE_BRICKS", "\uE003");
        FONT_CHARS.put("CHISELED_STONE_BRICKS", "\uE003");
        FONT_CHARS.put("GRASS_BLOCK", "\uE004");
        FONT_CHARS.put("DIRT", "\uE005");
        FONT_CHARS.put("COARSE_DIRT", "\uE005");
        FONT_CHARS.put("PODZOL", "\uE005");
        FONT_CHARS.put("MUD", "\uE005");
        FONT_CHARS.put("OAK_PLANKS", "\uE006");
        FONT_CHARS.put("SPRUCE_PLANKS", "\uE007");
        FONT_CHARS.put("BIRCH_PLANKS", "\uE008");
        FONT_CHARS.put("JUNGLE_PLANKS", "\uE006");
        FONT_CHARS.put("ACACIA_PLANKS", "\uE006");
        FONT_CHARS.put("DARK_OAK_PLANKS", "\uE007");
        FONT_CHARS.put("MANGROVE_PLANKS", "\uE006");
        FONT_CHARS.put("CHERRY_PLANKS", "\uE025");
        FONT_CHARS.put("BAMBOO_PLANKS", "\uE026");
        FONT_CHARS.put("CRIMSON_PLANKS", "\uE006");
        FONT_CHARS.put("WARPED_PLANKS", "\uE006");
        FONT_CHARS.put("OAK_LOG", "\uE009");
        FONT_CHARS.put("SPRUCE_LOG", "\uE009");
        FONT_CHARS.put("BIRCH_LOG", "\uE009");
        FONT_CHARS.put("JUNGLE_LOG", "\uE009");
        FONT_CHARS.put("ACACIA_LOG", "\uE009");
        FONT_CHARS.put("DARK_OAK_LOG", "\uE009");
        FONT_CHARS.put("MANGROVE_LOG", "\uE009");
        FONT_CHARS.put("CHERRY_LOG", "\uE009");

        // Ores & Minerals
        FONT_CHARS.put("DIAMOND_BLOCK", "\uE00A");
        FONT_CHARS.put("GOLD_BLOCK", "\uE00B");
        FONT_CHARS.put("IRON_BLOCK", "\uE00C");
        FONT_CHARS.put("EMERALD_BLOCK", "\uE00D");
        FONT_CHARS.put("REDSTONE_BLOCK", "\uE00E");
        FONT_CHARS.put("LAPIS_BLOCK", "\uE00F");
        FONT_CHARS.put("OBSIDIAN", "\uE010");
        FONT_CHARS.put("GLOWSTONE", "\uE011");
        FONT_CHARS.put("NETHERITE_BLOCK", "\uE012");
        FONT_CHARS.put("COAL_BLOCK", "\uE01F");
        FONT_CHARS.put("COPPER_BLOCK", "\uE020");
        FONT_CHARS.put("AMETHYST_BLOCK", "\uE021");

        // Building
        FONT_CHARS.put("BRICKS", "\uE013");
        FONT_CHARS.put("SAND", "\uE014");
        FONT_CHARS.put("RED_SAND", "\uE014");
        FONT_CHARS.put("SANDSTONE", "\uE014");
        FONT_CHARS.put("GRAVEL", "\uE015");
        FONT_CHARS.put("GLASS", "\uE016");
        FONT_CHARS.put("BOOKSHELF", "\uE017");
        FONT_CHARS.put("TNT", "\uE018");
        FONT_CHARS.put("SPONGE", "\uE01A");
        FONT_CHARS.put("ICE", "\uE01B");
        FONT_CHARS.put("PACKED_ICE", "\uE01B");
        FONT_CHARS.put("BLUE_ICE", "\uE01B");
        FONT_CHARS.put("SNOW_BLOCK", "\uE01C");
        FONT_CHARS.put("PUMPKIN", "\uE01D");
        FONT_CHARS.put("MELON", "\uE01E");

        // Special
        FONT_CHARS.put("LODESTONE", "\uE019");
        FONT_CHARS.put("DEEPSLATE", "\uE022");
        FONT_CHARS.put("MOSS_BLOCK", "\uE023");
        FONT_CHARS.put("SCULK", "\uE024");
        FONT_CHARS.put("PRISMARINE", "\uE027");
        FONT_CHARS.put("DARK_PRISMARINE", "\uE027");
        FONT_CHARS.put("SEA_LANTERN", "\uE028");
        FONT_CHARS.put("CRYING_OBSIDIAN", "\uE029");
        FONT_CHARS.put("PURPUR_BLOCK", "\uE02A");
        FONT_CHARS.put("END_STONE", "\uE02B");
        FONT_CHARS.put("END_STONE_BRICKS", "\uE02B");
        FONT_CHARS.put("QUARTZ_BLOCK", "\uE02C");
        FONT_CHARS.put("HONEY_BLOCK", "\uE02D");
        FONT_CHARS.put("HONEYCOMB_BLOCK", "\uE02D");
        FONT_CHARS.put("SLIME_BLOCK", "\uE02E");
    }

    private final Map<String, List<String>> categories = new HashMap<>();

    public ItemsDatabase(Plugin plugin) {
        loadCategories(plugin);
    }

    private void loadCategories(Plugin plugin) {
        try (InputStream is = plugin.getResource("items.json")) {
            if (is == null) {
                plugin.getLogger().warning("[ItemsDatabase] items.json not found in resources.");
                return;
            }
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
            if (root.has("categories")) {
                JsonObject catsObj = root.getAsJsonObject("categories");
                for (Map.Entry<String, JsonElement> entry : catsObj.entrySet()) {
                    List<String> mats = new ArrayList<>();
                    for (JsonElement el : entry.getValue().getAsJsonArray()) {
                        mats.add(el.getAsString());
                    }
                    categories.put(entry.getKey().toLowerCase(Locale.ROOT), mats);
                }
            }
            plugin.getLogger().info("[ItemsDatabase] Loaded " + FONT_CHARS.size() + " icon mappings and " + categories.size() + " categories.");
        } catch (Exception e) {
            plugin.getLogger().warning("[ItemsDatabase] Failed to load items.json: " + e.getMessage());
        }
    }

    /**
     * Returns an Adventure Component with the small item texture icon
     * rendered via the custom font. Falls back to "■" on the default font.
     */
    public Component getIconComponent(String materialName) {
        if (materialName == null) {
            return Component.text("■");
        }
        String upper = materialName.toUpperCase(Locale.ROOT);
        String ch = FONT_CHARS.get(upper);
        if (ch != null) {
            return Component.text(ch).font(ICON_FONT);
        }
        // Fallback: try to guess from material name
        return Component.text("■");
    }

    /**
     * Returns an Adventure Component with the small item texture icon
     * for a Material enum.
     */
    public Component getIconComponent(Material material) {
        if (material == null) return Component.text("■");
        return getIconComponent(material.name());
    }

    /**
     * Check if a material has a custom font icon mapping.
     */
    public boolean hasIcon(String materialName) {
        return materialName != null && FONT_CHARS.containsKey(materialName.toUpperCase(Locale.ROOT));
    }

    /**
     * Returns materials in a given category, or empty list.
     */
    public List<String> getCategory(String categoryName) {
        return categories.getOrDefault(categoryName.toLowerCase(Locale.ROOT), Collections.emptyList());
    }

    /**
     * Returns all category names.
     */
    public Set<String> getCategoryNames() {
        return categories.keySet();
    }
}
