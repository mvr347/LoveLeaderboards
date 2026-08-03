package dev.lovelace.loveleaderboards.managers;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.Category;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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
            String scoreUnit = section.getString(key + ".score-unit", null);
            String entityType = section.getString(key + ".entity-type", null);
            String icon = section.getString(key + ".icon", null);

            Category category = new Category(
                name, displayName, description, enabled, sortOrder,
                timePeriod, integration, scoreUnit, entityType, icon
            );
            categories.put(name, category);
        }
        
        plugin.getLogger().info("Loaded " + categories.size() + " categories from config.");
    }

    public Optional<Category> getCategory(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(categories.get(name.toLowerCase()));
    }

    public Collection<Category> getAllCategories() {
        return categories.values();
    }

    public String getNextCategory(String currentCategory, String entityType, boolean forward) {
        String targetType = entityType != null ? entityType : "player";
        List<Category> matching = categories.values().stream()
            .filter(Category::enabled)
            .filter(c -> c.getEntityType().equalsIgnoreCase(targetType))
            .sorted(Comparator.comparingInt(Category::sortOrder))
            .toList();

        if (matching.isEmpty()) return currentCategory;

        int currentIndex = -1;
        for (int i = 0; i < matching.size(); i++) {
            if (matching.get(i).name().equalsIgnoreCase(currentCategory)) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) return matching.get(0).name();

        int nextIndex;
        if (forward) {
            nextIndex = (currentIndex + 1) % matching.size();
        } else {
            nextIndex = (currentIndex - 1 + matching.size()) % matching.size();
        }
        return matching.get(nextIndex).name();
    }
}
