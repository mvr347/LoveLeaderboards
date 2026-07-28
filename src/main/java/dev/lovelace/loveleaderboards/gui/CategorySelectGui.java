package dev.lovelace.loveleaderboards.gui;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.Category;
import dev.lovelace.loveleaderboards.models.TimePeriod;
import dev.lovelace.loveleaderboards.utils.ItemBuilder;
import dev.lovelace.loveleaderboards.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

public class CategorySelectGui extends BaseGui {
    private final LoveLeaderboards plugin;
    private final Player viewer;
    private final TimePeriod currentPeriod;
    private final OfflinePlayer compareTarget;
    private final List<Category> categoryList;

    public CategorySelectGui(LoveLeaderboards plugin, Player viewer, TimePeriod currentPeriod) {
        this(plugin, viewer, currentPeriod, null);
    }

    public CategorySelectGui(LoveLeaderboards plugin, Player viewer, TimePeriod currentPeriod, OfflinePlayer compareTarget) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.currentPeriod = currentPeriod;
        this.compareTarget = compareTarget;
        this.categoryList = new ArrayList<>(plugin.getCategoryManager().getAllCategories().stream().filter(Category::enabled).toList());

        String title = plugin.getConfig().getString("gui.category-select.title", "&6📁 Выбор категории");
        this.inventory = Bukkit.createInventory(this, 27, TextUtil.parse(title));
        setup();
    }

    private void setup() {
        // gui-gen-4: Header 0-8 (Slot 0 head, 1 glass, 8 glass)
        inventory.setItem(0, new ItemBuilder(Material.PLAYER_HEAD)
            .skullOwner(viewer.getUniqueId())
            .name("&e" + viewer.getName())
            .lore("&7Выбор категории лидерборда")
            .build());
        inventory.setItem(1, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        
        if (compareTarget != null) {
            String compareB64 = plugin.getConfig().getString("gui.buttons.comparison", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdhZWU5YTc1YmYwZGY3ODk3MTgzMDE1Y2NhMGIyZDdiNzliYjNjMzRlYTU0MjRjNjc5NGJiNGZhOTVjMTZiZiJ9fX0=");
            inventory.setItem(4, new ItemBuilder(Material.PLAYER_HEAD)
                .base64Head(compareB64)
                .name("&6⚔️ Сравнение с: &e" + compareTarget.getName())
                .lore("&7Выберите категорию для сравнения")
                .build());
        } else {
            String categoryB64 = plugin.getConfig().getString("gui.buttons.category", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ4MWRmZTJiMmY5OWUzZGVjZTRjMzQ3MjY0MzM1ZjUzMTgzZjEzYjE4YTkxN2RkYjcyMzEzZTlkMDc0NjNmZCJ9fX0=");
            inventory.setItem(4, new ItemBuilder(Material.PLAYER_HEAD)
                .base64Head(categoryB64)
                .name("&eДоступные категории")
                .lore("&7Всего доступно категорий: &f" + categoryList.size())
                .build());
        }

        inventory.setItem(8, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        // Category items in row 1 (10..16)
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < categoryList.size() && i < slots.length; i++) {
            Category cat = categoryList.get(i);
            String catHead = getHeadForCategory(cat.name());
            ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD)
                .base64Head(catHead)
                .name(cat.displayName())
                .lore(
                    cat.description(),
                    "&7Тип: &f" + (cat.isClanCategory() ? "Кланы" : "Игроки"),
                    "",
                    "&a▶ Нажмите, чтобы выбрать"
                );
            inventory.setItem(slots[i], builder.build());
        }

        // Footer (Row 2: 18-26)
        for (int i = 18; i < 25; i++) {
            inventory.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }
        
        String backB64 = plugin.getConfig().getString("gui.buttons.back", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODY1MmM2NTEyMjI1NWMwNDY3ZmFlNzA5ODcyODRmOTc2YWMxYWUzN2VjZTQ2YmMzZmNhMjdjZTMyN2JiMWE3ZCJ9fX0=");
        // Slot 25: Back
        inventory.setItem(25, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(backB64)
            .name("&e◀ Назад")
            .lore("&7Вернуться к предыдущему меню")
            .build());

        String closeB64 = plugin.getConfig().getString("gui.buttons.close", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjc2NDMzZjRmZWQ2ZmMyYThjMzU5YzExZTUwOTZhZGE5OWU4ZjQxNGZmZmNmNzlkZDAxY2MyYjIzZDkyNGZhNyJ9fX0=");
        // Slot 26: Close
        inventory.setItem(26, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(closeB64)
            .name("&cЗакрыть")
            .build());
    }

    private String getHeadForCategory(String categoryName) {
        String defaultB64 = switch (categoryName.toLowerCase()) {
            case "kills" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdhZWU5YTc1YmYwZGY3ODk3MTgzMDE1Y2NhMGIyZDdiNzliYjNjMzRlYTU0MjRjNjc5NGJiNGZhOTVjMTZiZiJ9fX0=";
            case "bounty-completed" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ4MWRmZTJiMmY5OWUzZGVjZTRjMzQ3MjY0MzM1ZjUzMTgzZjEzYjE4YTkxN2RkYjcyMzEzZTlkMDc0NjNmZCJ9fX0=";
            case "clan-power" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGZjZWUzYTg4YmI1NGMwZjZlZTY2YjQ0YWM3NGZmOTdjZDkyYTA4ZjE0Y2NjMTdhMjYyMzcxZjBhYTg5MjEifX19";
            default -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ4MWRmZTJiMmY5OWUzZGVjZTRjMzQ3MjY0MzM1ZjUzMTgzZjEzYjE4YTkxN2RkYjcyMzEzZTlkMDc0NjNmZCJ9fX0=";
        };
        return plugin.getConfig().getString("gui.buttons.categories." + categoryName, defaultB64);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == 25) {
            // Back
            if (compareTarget != null) {
                viewer.openInventory(new PlayerComparisonGui(plugin, viewer, compareTarget, plugin.getCategoryManager().getAllCategories().stream().findFirst().map(Category::name).orElse("kills"), currentPeriod).getInventory());
            } else {
                viewer.openInventory(new LeaderboardMainGui(plugin, viewer, plugin.getCategoryManager().getAllCategories().stream().findFirst().map(Category::name).orElse("kills"), currentPeriod, 1).getInventory());
            }
            return;
        }

        if (slot == 26) {
            viewer.closeInventory();
            return;
        }

        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < slots.length && i < categoryList.size(); i++) {
            if (slots[i] == slot) {
                Category selected = categoryList.get(i);
                if (compareTarget != null) {
                    viewer.openInventory(new PlayerComparisonGui(plugin, viewer, compareTarget, selected.name(), currentPeriod).getInventory());
                } else {
                    viewer.openInventory(new LeaderboardMainGui(plugin, viewer, selected.name(), currentPeriod, 1).getInventory());
                }
                return;
            }
        }
    }
}
