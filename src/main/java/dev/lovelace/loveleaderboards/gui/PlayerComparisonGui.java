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
        this.currentCategory = category;
        this.currentPeriod = period;

        Optional<Category> catOpt = plugin.getCategoryManager().getCategory(currentCategory);
        boolean isClan = catOpt.map(Category::isClanCategory).orElse(false);

        String titleKey = isClan ? "gui.comparison.clan-title" : "gui.comparison.title";
        String defaultTitle = isClan ? "&6⚔️ Сравнение кланов" : "&6⚔️ Сравнение игроков";
        String title = plugin.getConfig().getString(titleKey, defaultTitle);
        
        this.inventory = Bukkit.createInventory(this, 27, TextUtil.parse(title));
        setup();
    }

    private void setup() {
        // gui-gen-4: Header (0-8)
        inventory.setItem(0, new ItemBuilder(Material.PLAYER_HEAD)
            .name("&e" + viewer.getName())
            .skullOwner(viewer.getUniqueId())
            .build());
        inventory.setItem(1, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(2, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(3, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(5, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(7, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        String periodB64 = plugin.getConfig().getString("gui.buttons.period", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGZjZWUzYTg4YmI1NGMwZjZlZTY2YjQ0YWM3NGZmOTdjZDkyYTA4ZjE0Y2NjMTdhMjYyMzcxZjBhYTg5MjEifX19");
        inventory.setItem(4, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(periodB64)
            .name("&eПериод: " + currentPeriod.getDisplayName())
            .lore(
                currentPeriod.getDescription(),
                "",
                "&a▶ Нажмите для переключения периода"
            ).build());

        Optional<Category> catOpt = plugin.getCategoryManager().getCategory(currentCategory);
        String catName = catOpt.map(Category::displayName).orElse(currentCategory);

        String categoryB64 = plugin.getConfig().getString("gui.buttons.category", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ4MWRmZTJiMmY5OWUzZGVjZTRjMzQ3MjY0MzM1ZjUzMTgzZjEzYjE4YTkxN2RkYjcyMzEzZTlkMDc0NjNmZCJ9fX0=");
        inventory.setItem(6, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(categoryB64)
            .name("&eКатегория: " + catName)
            .lore(
                "&7Текущая категория: &f" + catName,
                "",
                "&a▶ Нажмите для смены категории"
            ).build());

        inventory.setItem(8, new ItemBuilder(Material.PLAYER_HEAD)
            .name("&c" + (target.getName() != null ? target.getName() : "Unknown"))
            .skullOwner(target.getUniqueId())
            .build());

        // Footer buttons (Row 2: 18-26)
        for (int i = 18; i < 25; i++) {
            inventory.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        String backB64 = plugin.getConfig().getString("gui.buttons.back", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODY1MmM2NTEyMjI1NWMwNDY3ZmFlNzA5ODcyODRmOTc2YWMxYWUzN2VjZTQ2YmMzZmNhMjdjZTMyN2JiMWE3ZCJ9fX0=");
        inventory.setItem(25, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(backB64)
            .name("&e◀ Назад в топ")
            .lore("&7Вернуться в главную таблицу лидеров")
            .build());
            
        String closeB64 = plugin.getConfig().getString("gui.buttons.close", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjc2NDMzZjRmZWQ2ZmMyYThjMzU5YzExZTUwOTZhZGE5OWU4ZjQxNGZmZmNmNzlkZDAxY2MyYjIzZDkyNGZhNyJ9fX0=");
        inventory.setItem(26, new ItemBuilder(Material.PLAYER_HEAD)
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
                // Left side: Viewer stats (Slot 10)
                inventory.setItem(10, new ItemBuilder(Material.PLAYER_HEAD)
                    .skullOwner(viewer.getUniqueId())
                    .name("&e" + viewer.getName())
                    .lore(
                        "&7Категория: &f" + catName,
                        "&7Период: " + currentPeriod.getDisplayName(),
                        "",
                        isClan ? "&7Ранг вашего клана: &e#" + (s1.rank() > 0 ? s1.rank() : "Не в топе")
                               : "&7Ваш ранг: &e#" + (s1.rank() > 0 ? s1.rank() : "Не в топе"),
                        "&7" + scoreUnit + ": &a" + (long) s1.score()
                    ).build());

                // Center: Comparison Summary (Slot 13)
                inventory.setItem(13, new ItemBuilder(Material.PAPER)
                    .name("&6⚔️ Итог сравнения")
                    .lore(
                        "&e" + viewer.getName() + " &7vs &c" + (target.getName() != null ? target.getName() : "Unknown"),
                        "&7Ранг: &e#" + (s1.rank() > 0 ? s1.rank() : "N/A") + " &7| &c#" + (s2.rank() > 0 ? s2.rank() : "N/A"),
                        "&7" + scoreUnit + ": &e" + (long) s1.score() + " &7| &c" + (long) s2.score(),
                        "",
                        diffStr
                    ).build());

                // Right side: Target stats (Slot 16)
                inventory.setItem(16, new ItemBuilder(Material.PLAYER_HEAD)
                    .skullOwner(target.getUniqueId())
                    .name("&c" + (target.getName() != null ? target.getName() : "Unknown"))
                    .lore(
                        "&7Категория: &f" + catName,
                        "&7Период: " + currentPeriod.getDisplayName(),
                        "",
                        isClan ? "&7Ранг клана: &c#" + (s2.rank() > 0 ? s2.rank() : "Не в топе")
                               : "&7Ранг: &c#" + (s2.rank() > 0 ? s2.rank() : "Не в топе"),
                        "&7" + scoreUnit + ": &c" + (long) s2.score()
                    ).build());
            });
        });
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == 4) {
            // Cycle period (stay in comparison GUI with target player)
            TimePeriod nextPeriod = currentPeriod.next();
            viewer.openInventory(new PlayerComparisonGui(plugin, viewer, target, currentCategory, nextPeriod).getInventory());
        } else if (slot == 6) {
            // Category selector (pass target player so selecting a category stays in comparison view!)
            viewer.openInventory(new CategorySelectGui(plugin, viewer, currentPeriod, target).getInventory());
        } else if (slot == 25) {
            // Back to main leaderboard
            viewer.openInventory(new LeaderboardMainGui(plugin, viewer, currentCategory, currentPeriod, 1).getInventory());
        } else if (slot == 26) {
            viewer.closeInventory();
        }
    }
}
