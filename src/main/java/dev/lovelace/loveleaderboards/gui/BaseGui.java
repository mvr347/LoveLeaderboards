package dev.lovelace.loveleaderboards.gui;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.textures.HeadTextures;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

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
            case "comparison", "prev-page" -> HeadTextures.GUI_COMPARISON;
            case "next-page" -> HeadTextures.GUI_NEXT_PAGE;
            case "back" -> HeadTextures.GUI_BACK;
            case "close" -> HeadTextures.GUI_CLOSE;
            case "no-clan", "empty-slot" -> HeadTextures.GUI_NO_CLAN;
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
}
