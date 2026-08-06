package dev.lovelace.loveleaderboards.gui;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
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
            case "type-player" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjU3YzdlOTZhODAyYzI3MDgwYzdmODA1MzgxNDM2OGVhOTRkZjg2NDQ1OTEyMGU1MTU1NzE4YjUwM2MzZWQ3In19fQ==";
            case "type-clan" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjJiNWY5NjhjYzg4ZDNlOTg2NWQ2ZTdhOGQ1YmU3NWVhNzNhMGEzOTRiNTFlYWE1Zjk0YzA0NzU5ZGNkYTAyZCJ9fX0=";
            case "period" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzVmYzFkYWRhOWM2NWE3YWJjZTM0MjQxNDBkM2FiMjI0ZmNjNTM5OGNiOGNmZDY3NWY0MzY4NjhiZTZmNTRmZCJ9fX0=";
            case "category" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTQ3YWUyMDllOGE1MjU5MWNjMjBhYzBjOWVjNmE1Y2IzZGMwNGYyMzhhYzJkNzQzYjFkNTRmMTFlOWM1Yzg1In19fQ==";
            case "comparison" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdhZWU5YTc1YmYwZGY3ODk3MTgzMDE1Y2NhMGIyZDdiNzliYjNjMzRlYTU0MjRjNjc5NGJiNGZhOTVjMTZiZiJ9fX0=";
            case "prev-page" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdhZWU5YTc1YmYwZGY3ODk3MTgzMDE1Y2NhMGIyZDdiNzliYjNjMzRlYTU0MjRjNjc5NGJiNGZhOTVjMTZiZiJ9fX0=";
            case "next-page" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjgyYWQxYjRjY2RiNjZkNTkyNzJmMTc3YWMwZGRhZDE4YzA0NzJjNzcyNTM1ZTUwZmE5ZDkxNGIyMjFhNjc5NSJ9fX0=";
            case "back" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODY1MmM2NTEyMjI1NWMwNDY3ZmFlNzA5ODcyODRmOTc2YWMxYWUzN2VjZTQ2YmMzZmNhMjdjZTMyN2JiMWE3ZCJ9fX0=";
            case "close" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjc2NDMzZjRmZWQ2ZmMyYThjMzU5YzExZTUwOTZhZGE5OWU4ZjQxNGZmZmNmNzlkZDAxY2MyYjIzZDkyNGZhNyJ9fX0=";
            case "no-clan" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTgzNTJhZjkyYWNhNzc0N2FjMjllNTE0MmFlOTEyYWVlMWViY2E5MDhiMzFjMWYxZTY4YzU5MmFhZjkyZTYzNiJ9fX0=";
            case "empty-slot" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTgzNTJhZjkyYWNhNzc0N2FjMjllNTE0MmFlOTEyYWVlMWViY2E5MDhiMzFjMWYxZTY4YzU5MmFhZjkyZTYzNiJ9fX0=";
            default -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ4MWRmZTJiMmY5OWUzZGVjZTRjMzQ3MjY0MzM1ZjUzMTgzZjEzYjE4YTkxN2RkYjcyMzEzZTlkMDc0NjNmZCJ9fX0=";
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
