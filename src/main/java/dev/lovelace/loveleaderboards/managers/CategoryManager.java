package dev.lovelace.loveleaderboards.managers;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.Category;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CategoryManager {
    private final LoveLeaderboards plugin;
    private final Map<String, Category> categories = new HashMap<>();

    public CategoryManager(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    public void loadCategories() {
        categories.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("categories");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String name = key.toLowerCase();
            String displayName = section.getString(key + ".display-name", name);
            String description = section.getString(key + ".description", "");
            boolean enabled = section.getBoolean(key + ".enabled", true);
            int sortOrder = section.getInt(key + ".sort-order", 0);
            String timePeriod = section.getString(key + ".time-period", "both");
            String integration = section.getString(key + ".integration", "none");

            Category category = new Category(name, displayName, description, enabled, sortOrder, timePeriod, integration);
            categories.put(name, category);
        }
        
        plugin.getLogger().info("Loaded " + categories.size() + " categories.");
    }

    public Optional<Category> getCategory(String name) {
        return Optional.ofNullable(categories.get(name.toLowerCase()));
    }

    public Collection<Category> getAllCategories() {
        return categories.values();
    }
}
