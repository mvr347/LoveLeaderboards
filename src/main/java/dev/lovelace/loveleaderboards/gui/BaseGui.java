package dev.lovelace.loveleaderboards.gui;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.textures.HeadTextures;
import dev.lovelace.loveleaderboards.models.Category;
import dev.lovelace.loveleaderboards.models.TimePeriod;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public abstract class BaseGui implements InventoryHolder {
    protected Inventory inventory;

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public abstract void handleClick(InventoryClickEvent event);

    public void open(org.bukkit.entity.Player player) {
        if (inventory != null && player != null) {
            player.openInventory(inventory);
        }
    }

    protected String getButtonHead(LoveLeaderboards plugin, String key) {
        if (plugin == null || key == null) return "";
        String k = key.toLowerCase().trim();

        // 1. Check heads.yml via HeadManager
        if (plugin.getHeadManager() != null) {
            String head = plugin.getHeadManager().getHead(k);
            if (head != null && !head.isEmpty()) return head;
        }

        // 2. Check gui.buttons.<key> in config.yml
        String val = plugin.getConfig().getString("gui.buttons." + k);
        if (val != null && !val.isEmpty()) return val;

        // 3. Check gui.<key>.head-b64 / icon / head
        String emptyB64 = plugin.getConfig().getString("gui." + k + ".head-b64");
        if (emptyB64 != null && !emptyB64.isEmpty()) return emptyB64;

        // 4. Check gui.slot-0.<key>.head-b64 / icon / head
        String slot0B64 = plugin.getConfig().getString("gui.slot-0." + k + ".head-b64");
        if (slot0B64 != null && !slot0B64.isEmpty()) return slot0B64;

        String slot0Icon = plugin.getConfig().getString("gui.slot-0." + k + ".icon");
        if (slot0Icon != null && !slot0Icon.isEmpty()) return slot0Icon;

        String slot0Head = plugin.getConfig().getString("gui.slot-0." + k + ".head");
        if (slot0Head != null && !slot0Head.isEmpty()) return slot0Head;

        return switch (k) {
            case "type-player" -> HeadTextures.GUI_TYPE_PLAYER;
            case "type-clan" -> HeadTextures.GUI_TYPE_CLAN;
            case "period" -> HeadTextures.GUI_PERIOD;
            case "category" -> HeadTextures.GUI_CATEGORY;
            case "comparison" -> HeadTextures.GUI_COMPARISON;
            case "prev-page" -> HeadTextures.GUI_PREV_PAGE;
            case "next-page" -> HeadTextures.GUI_NEXT_PAGE;
            case "back" -> HeadTextures.GUI_BACK;
            case "close" -> HeadTextures.GUI_CLOSE;
            case "no-clan" -> HeadTextures.GUI_NO_CLAN;
            case "empty-slot" -> HeadTextures.GUI_EMPTY_SLOT;
            default -> HeadTextures.GUI_DEFAULT;
        };
    }

    protected String getCategoryHead(LoveLeaderboards plugin, String categoryName) {
        if (plugin == null || categoryName == null) return getButtonHead(plugin, "category");
        String key = categoryName.toLowerCase().trim();

        // 1. Check heads.yml via HeadManager
        if (plugin.getHeadManager() != null) {
            String head = plugin.getHeadManager().getHead(key);
            if (head != null && !head.isEmpty()) return head;
        }

        // 2. Check category object icon
        dev.lovelace.loveleaderboards.models.Category cat = plugin.getCategoryManager().getCategory(key).orElse(null);
        if (cat != null && cat.icon() != null && !cat.icon().isEmpty()) {
            return cat.icon();
        }

        // 3. Check config.yml fallback
        String catIcon = plugin.getConfig().getString("categories." + key + ".icon");
        if (catIcon != null && !catIcon.isEmpty()) return catIcon;

        String catHeadB64 = plugin.getConfig().getString("categories." + key + ".head-b64");
        if (catHeadB64 != null && !catHeadB64.isEmpty()) return catHeadB64;

        String path = "gui.buttons.categories." + key;
        String val = plugin.getConfig().getString(path);
        if (val != null && !val.isEmpty()) return val;

        return getButtonHead(plugin, "category");
    }

    // ---- Header switchers ("members menu" style, see SwitcherLore) ----

    protected static final String PERIOD_SWITCH_NAME = "&6Период";
    protected static final String CATEGORY_SWITCH_NAME = "&6Категория";
    protected static final String MODE_SWITCH_NAME = "&6Режим";

    /** Lore for the period switcher: every period, current one marked. */
    protected List<String> periodSwitchLore(TimePeriod current) {
        List<String> names = Arrays.stream(TimePeriod.values()).map(TimePeriod::getDisplayName).toList();
        return SwitcherLore.build(names, current.ordinal());
    }

    /** LMB = next period, RMB = previous period. */
    protected TimePeriod cyclePeriod(TimePeriod current, boolean rightClick) {
        TimePeriod[] values = TimePeriod.values();
        return values[SwitcherLore.cycle(current.ordinal(), values.length, rightClick)];
    }

    /** Enabled categories of a type, in the same order CategoryManager#getNextCategory cycles through. */
    protected List<Category> switchableCategories(LoveLeaderboards plugin, String entityType) {
        String type = entityType != null ? entityType : "player";
        return plugin.getCategoryManager().getAllCategories().stream()
            .filter(Category::enabled)
            .filter(c -> c.getEntityType().equalsIgnoreCase(type))
            .sorted(Comparator.comparingInt(Category::sortOrder))
            .toList();
    }

    /** Lore for the category switcher; windowed to +-3 around the current one when there are more than 7. */
    protected List<String> categorySwitchLore(LoveLeaderboards plugin, String currentCategory, String entityType) {
        List<Category> cats = switchableCategories(plugin, entityType);
        int current = -1;
        for (int i = 0; i < cats.size(); i++) {
            if (cats.get(i).name().equalsIgnoreCase(currentCategory)) {
                current = i;
                break;
            }
        }
        return SwitcherLore.build(cats.stream().map(Category::displayName).toList(), current);
    }

    /** Lore for a two-option mode switcher (both clicks toggle). */
    protected List<String> modeSwitchLore(List<String> options, int current) {
        return SwitcherLore.build(options, current);
    }
}
