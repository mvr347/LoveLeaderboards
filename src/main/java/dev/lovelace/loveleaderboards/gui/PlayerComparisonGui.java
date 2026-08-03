package dev.lovelace.loveleaderboards.gui;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.Category;
import dev.lovelace.loveleaderboards.models.Comparison;
import dev.lovelace.loveleaderboards.models.PlayerStats;
import dev.lovelace.loveleaderboards.models.TimePeriod;
import dev.lovelace.loveleaderboards.utils.ItemBuilder;
import dev.lovelace.loveleaderboards.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;
import java.util.Optional;

public class PlayerComparisonGui extends BaseGui {
    private final LoveLeaderboards plugin;
    private final Player viewer;
    private final OfflinePlayer target;
    private final String currentCategory;
    private final TimePeriod currentPeriod;

    public PlayerComparisonGui(LoveLeaderboards plugin, Player viewer, OfflinePlayer target) {
        this(
            plugin,
            viewer,
            target,
            plugin.getCategoryManager().getAllCategories().stream().findFirst().map(Category::name).orElse("kills"),
            TimePeriod.ALL_TIME
        );
    }

    public PlayerComparisonGui(LoveLeaderboards plugin, Player viewer, OfflinePlayer target, String category, TimePeriod period) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.target = target;
        this.currentCategory = category != null ? category : "kills";
        this.currentPeriod = period != null ? period : TimePeriod.ALL_TIME;

        Optional<Category> catOpt = plugin.getCategoryManager().getCategory(currentCategory);
        boolean isClan = catOpt.map(Category::isClanCategory).orElse(false);

        String titleKey = isClan ? "gui.comparison.clan-title" : "gui.comparison.title";
        String defaultTitle = isClan ? "&6⚔️ Сравнение кланов" : "&6⚔️ Сравнение игроков";
        String title = plugin.getConfig().getString(titleKey, defaultTitle);
        
        this.inventory = Bukkit.createInventory(this, 54, TextUtil.parse(title));
        setup();
    }

    private void setup() {
        // gui-gen-4 Header (0-8)
        inventory.setItem(0, new ItemBuilder(Material.PLAYER_HEAD)
            .skullOwner(viewer.getUniqueId())
            .name("&e" + viewer.getName())
            .lore("", "&7Ваш профиль")
            .build());

        inventory.setItem(1, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(2, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(3, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        String periodB64 = plugin.getConfig().getString("gui.buttons.period", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGZjZWUzYTg4YmI1NGMwZjZlZTY2YjQ0YWM3NGZmOTdjZDkyYTA4ZjE0Y2NjMTdhMjYyMzcxZjBhYTg5MjEifX19");
        inventory.setItem(4, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(periodB64)
            .name("&eПериод: " + currentPeriod.getDisplayName())
            .lore("", currentPeriod.getDescription(), "", "&a▶ Нажмите для смены периода")
            .build());

        Optional<Category> catOpt = plugin.getCategoryManager().getCategory(currentCategory);
        String catName = catOpt.map(Category::displayName).orElse(currentCategory);

        String categoryB64 = plugin.getConfig().getString("gui.buttons.category", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ4MWRmZTJiMmY5OWUzZGVjZTRjMzQ3MjY0MzM1ZjUzMTgzZjEzYjE4YTkxN2RkYjcyMzEzZTlkMDc0NjNmZCJ9fX0=");
        inventory.setItem(5, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(categoryB64)
            .name("&eКатегория: " + catName)
            .lore(
                "",
                "&7Текущая категория: &f" + catName,
                "",
                "&aЛКМ &7— следующая категория",
                "&aПКМ &7— предыдущая категория"
            ).build());

        inventory.setItem(6, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(7, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        inventory.setItem(8, new ItemBuilder(Material.PLAYER_HEAD)
            .skullOwner(target.getUniqueId())
            .name("&c" + (target.getName() != null ? target.getName() : "Unknown"))
            .lore("", "&7Профиль оппонента")
            .build());

        // Row 1 (9-17): PURE GLASS ROW (gui-gen-4 Rule 4)
        for (int i = 9; i <= 17; i++) {
            inventory.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        // Footer (45-53)
        for (int i = 45; i < 52; i++) {
            inventory.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        // Slot 52: Back button
        if (GuiNavigationManager.hasHistory(viewer)) {
            String backB64 = plugin.getConfig().getString("gui.buttons.back", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODY1MmM2NTEyMjI1NWMwNDY3ZmFlNzA5ODcyODRmOTc2YWMxYWUzN2VjZTQ2YmMzZmNhMjdjZTMyN2JiMWE3ZCJ9fX0=");
            inventory.setItem(52, new ItemBuilder(Material.PLAYER_HEAD)
                .base64Head(backB64)
                .name("&e◀ Назад")
                .lore("", "&7Вернуться в предыдущее меню", "", "&a▶ Нажмите для возврата")
                .build());
        } else {
            inventory.setItem(52, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        // Slot 53: Close button
        String closeB64 = plugin.getConfig().getString("gui.buttons.close", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjc2NDMzZjRmZWQ2ZmMyYThjMzU5YzExZTUwOTZhZGE5OWU4ZjQxNGZmZmNmNzlkZDAxY2MyYjIzZDkyNGZhNyJ9fX0=");
        inventory.setItem(53, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(closeB64)
            .name("&cЗакрыть")
            .build());

        loadContent();
    }

    private void loadContent() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Comparison comp = plugin.getLeaderboardManager().compareWithPlayer(
                viewer.getUniqueId(), viewer.getName(),
                target.getUniqueId(), target.getName() == null ? "Unknown" : target.getName(),
                currentCategory,
                currentPeriod.getDbKey()
            );

            PlayerStats s1 = comp.stats1();
            PlayerStats s2 = comp.stats2();

            Optional<Category> catOpt = plugin.getCategoryManager().getCategory(currentCategory);
            String catName = catOpt.map(Category::displayName).orElse(currentCategory);
            String scoreUnit = catOpt.map(Category::getScoreUnit).orElse("Очки");
            boolean isClan = catOpt.map(Category::isClanCategory).orElse(false);

            double diff = s1.score() - s2.score();
            String diffStr = diff == 0 ? "&7Показатели одинаковы!" : (diff > 0 ? "&aВы опережаете на " + (long)diff + " " + scoreUnit : "&cВы отстаете на " + (long)Math.abs(diff) + " " + scoreUnit);

            Bukkit.getScheduler().runTask(plugin, () -> {
                // Clear work area (18-44)
                for (int i = 18; i <= 44; i++) {
                    inventory.setItem(i, null);
                }

                // Left side: Viewer stats (Slot 20)
                inventory.setItem(20, new ItemBuilder(Material.PLAYER_HEAD)
                    .skullOwner(viewer.getUniqueId())
                    .name("&e" + viewer.getName())
                    .lore(
                        "",
                        "&7Категория: &f" + catName,
                        "&7Период: " + currentPeriod.getDisplayName(),
                        "",
                        isClan ? "&7Ранг вашего клана: &e#" + (s1.rank() > 0 ? s1.rank() : "Не в топе")
                               : "&7Ваш ранг: &e#" + (s1.rank() > 0 ? s1.rank() : "Не в топе"),
                        "&7" + scoreUnit + ": &a" + (long) s1.score()
                    ).build());

                // Center: Comparison Summary (Slot 22)
                inventory.setItem(22, new ItemBuilder(Material.PAPER)
                    .name("&6⚔️ Итог сравнения")
                    .lore(
                        "",
                        "&e" + viewer.getName() + " &7vs &c" + (target.getName() != null ? target.getName() : "Unknown"),
                        "&7Ранг: &e#" + (s1.rank() > 0 ? s1.rank() : "N/A") + " &7| &c#" + (s2.rank() > 0 ? s2.rank() : "N/A"),
                        "&7" + scoreUnit + ": &e" + (long) s1.score() + " &7| &c" + (long) s2.score(),
                        "",
                        diffStr
                    ).build());

                // Right side: Target stats (Slot 24)
                inventory.setItem(24, new ItemBuilder(Material.PLAYER_HEAD)
                    .skullOwner(target.getUniqueId())
                    .name("&c" + (target.getName() != null ? target.getName() : "Unknown"))
                    .lore(
                        "",
                        "&7Категория: &f" + catName,
                        "&7Период: " + currentPeriod.getDisplayName(),
                        "",
                        isClan ? "&7Ранг клана: &c#" + (s2.rank() > 0 ? s2.rank() : "Не в топе")
                               : "&7Ранг: &c#" + (s2.rank() > 0 ? s2.rank() : "Не в топе"),
                        "&7" + scoreUnit + ": &c" + (long) s2.score()
                    ).build());

                // Quick category switching row (Slots 29..33)
                List<Category> allCats = plugin.getCategoryManager().getAllCategories().stream()
                    .filter(Category::enabled)
                    .toList();

                int[] quickSlots = {29, 30, 31, 32, 33};
                for (int i = 0; i < quickSlots.length && i < allCats.size(); i++) {
                    Category cat = allCats.get(i);
                    boolean isSelected = cat.name().equalsIgnoreCase(currentCategory);
                    Material mat = cat.isClanCategory() ? Material.RED_BANNER : Material.PLAYER_HEAD;
                    ItemBuilder builder = new ItemBuilder(mat);

                    if (!cat.isClanCategory()) {
                        String defaultCatB64 = switch (cat.name().toLowerCase()) {
                            case "kills" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdhZWU5YTc1YmYwZGY3ODk3MTgzMDE1Y2NhMGIyZDdiNzliYjNjMzRlYTU0MjRjNjc5NGJiNGZhOTVjMTZiZiJ9fX0=";
                            case "bounty-completed" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ4MWRmZTJiMmY5OWUzZGVjZTRjMzQ3MjY0MzM1ZjUzMTgzZjEzYjE4YTkxN2RkYjcyMzEzZTlkMDc0NjNmZCJ9fX0=";
                            default -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ4MWRmZTJiMmY5OWUzZGVjZTRjMzQ3MjY0MzM1ZjUzMTgzZjEzYjE4YTkxN2RkYjcyMzEzZTlkMDc0NjNmZCJ9fX0=";
                        };
                        String catHead = plugin.getConfig().getString("gui.buttons.categories." + cat.name(), defaultCatB64);
                        builder.base64Head(catHead);
                    }

                    builder.name((isSelected ? "&a&l▶ " : "&e") + cat.displayName())
                        .lore(
                            "",
                            "&7Статус: " + (isSelected ? "&aВыбрано" : "&7Выбрать"),
                            "",
                            "&a▶ Нажмите для сравнения в этой категории"
                        );

                    inventory.setItem(quickSlots[i], builder.build());
                }
            });
        });
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == 4) {
            TimePeriod nextPeriod = currentPeriod.next();
            viewer.openInventory(new PlayerComparisonGui(plugin, viewer, target, currentCategory, nextPeriod).getInventory());
            return;
        }

        if (slot == 5) {
            // Cycle category in-place
            boolean forward = !event.isRightClick();
            String nextCat = plugin.getCategoryManager().getNextCategory(currentCategory, "player", forward);
            viewer.openInventory(new PlayerComparisonGui(plugin, viewer, target, nextCat, currentPeriod).getInventory());
            return;
        }

        // Quick category selection (29..33)
        List<Category> allCats = plugin.getCategoryManager().getAllCategories().stream()
            .filter(Category::enabled)
            .toList();

        int[] quickSlots = {29, 30, 31, 32, 33};
        for (int i = 0; i < quickSlots.length && i < allCats.size(); i++) {
            if (quickSlots[i] == slot) {
                Category cat = allCats.get(i);
                viewer.openInventory(new PlayerComparisonGui(plugin, viewer, target, cat.name(), currentPeriod).getInventory());
                return;
            }
        }

        if (slot == 52) {
            if (GuiNavigationManager.hasHistory(viewer)) {
                GuiNavigationManager.goBack(plugin, viewer);
            }
            return;
        }

        if (slot == 53) {
            GuiNavigationManager.clearHistory(viewer);
            viewer.closeInventory();
        }
    }
}
