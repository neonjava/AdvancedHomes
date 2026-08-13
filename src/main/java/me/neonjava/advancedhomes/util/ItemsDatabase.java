package me.neonjava.advancedhomes.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads items.json resource for icon color indicators and material categories.
 */
public class ItemsDatabase {

    private final Map<String, String> iconColors = new HashMap<>();
    private final Map<String, List<String>> categories = new HashMap<>();
    private String defaultIcon = "■";

    public ItemsDatabase(Plugin plugin) {
        load(plugin);
    }

    private void load(Plugin plugin) {
        try (InputStream is = plugin.getResource("items.json")) {
            if (is == null) {
                plugin.getLogger().warning("[ItemsDatabase] items.json not found in resources.");
                return;
            }

            Gson gson = new Gson();
            JsonObject root = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);

            if (root.has("default_icon")) {
                defaultIcon = root.get("default_icon").getAsString();
            }

            if (root.has("icon_colors")) {
                JsonObject colorsObj = root.getAsJsonObject("icon_colors");
                for (Map.Entry<String, JsonElement> entry : colorsObj.entrySet()) {
                    iconColors.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue().getAsString());
                }
            }

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

            plugin.getLogger().info("[ItemsDatabase] Loaded " + iconColors.size() + " icon colors and " + categories.size() + " categories.");
        } catch (Exception e) {
            plugin.getLogger().warning("[ItemsDatabase] Failed to load items.json: " + e.getMessage());
        }
    }

    /**
     * Returns a colored Unicode indicator character for a material (e.g. "🟩" for GREEN_BED, "■" for unknown).
     */
    public String getIconIndicator(String materialName) {
        if (materialName == null) return defaultIcon;
        String upper = materialName.toUpperCase(Locale.ROOT);
        return iconColors.getOrDefault(upper, defaultIcon);
    }

    /**
     * Returns a colored Unicode indicator for a Material enum.
     */
    public String getIconIndicator(Material material) {
        if (material == null) return defaultIcon;
        return getIconIndicator(material.name());
    }

    /**
     * Returns materials in a given category, or empty list if not found.
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

    public String getDefaultIcon() {
        return defaultIcon;
    }
}
