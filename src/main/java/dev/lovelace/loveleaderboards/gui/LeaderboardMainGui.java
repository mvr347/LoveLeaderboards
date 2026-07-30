package dev.lovelace.loveleaderboards.gui;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.Category;
import dev.lovelace.loveleaderboards.models.LeaderboardEntry;
import dev.lovelace.loveleaderboards.models.PlayerStats;
import dev.lovelace.loveleaderboards.models.TimePeriod;
import dev.lovelace.loveleaderboards.utils.ItemBuilder;
import dev.lovelace.loveleaderboards.utils.TextUtil;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class LeaderboardMainGui extends BaseGui {
    private final LoveLeaderboards plugin;
    private final Player viewer;
    private final String currentCategory;
    private final TimePeriod currentPeriod;
    private final int page;

    private static final int ITEMS_PER_PAGE = 21;
    private static final int TOTAL_LIMIT = 50;

    private static final int[] GRID_SLOTS = {
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    public LeaderboardMainGui(LoveLeaderboards plugin, Player viewer) {
        this(
            plugin,
            viewer,
            plugin.getCategoryManager().getAllCategories().stream().findFirst().map(Category::name).orElse("kills"),
            TimePeriod.ALL_TIME,
            1
        );
    }

    public LeaderboardMainGui(LoveLeaderboards plugin, Player viewer, String category, TimePeriod period, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.currentCategory = category;
        this.currentPeriod = period;
        this.page = Math.max(1, page);

        String title = plugin.getConfig().getString("gui.main.title", "&6⭐ Таблица Лидеров");
        this.inventory = Bukkit.createInventory(this, 54, TextUtil.parse(title));

        setup();
    }

    private void setup() {
        // gui-gen-4 Rules:
        // 1. Header (0-8): Slot 0 Head, Slot 1 Glass, Slots 2,4,6,7 Controls (with glass at 3,5), Slot 8 Glass
        // 2. Row 1 (9-17): PURE GLASS ROW (visual separator)
        // 3. Side walls removed, Pagination only at 36, 44 if active.
        // 4. Footer: removed page item.

        // Header glass borders
        inventory.setItem(1, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(3, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(5, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(8, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        // Header controls (2, 4, 6, 7)
        String myStatsB64 = plugin.getConfig().getString("gui.buttons.my-stats", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjc2YjNiY2RmZDIxMmZhNTEzNDVlYWUwNDc1YjFjYzlkMjg0YWNlNTI0MjJmYjI2Yzg4NDFjYmE5NGEzOTJjNCJ9fX0=");
        inventory.setItem(2, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(myStatsB64)
            .name("&e📊 Моя статистика")
            .lore("&7Просмотреть свои показатели", "", "&a▶ Нажмите для просмотра")
            .build());

        String periodB64 = plugin.getConfig().getString("gui.buttons.period", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGZjZWUzYTg4YmI1NGMwZjZlZTY2YjQ0YWM3NGZmOTdjZDkyYTA4ZjE0Y2NjMTdhMjYyMzcxZjBhYTg5MjEifX19");
        inventory.setItem(4, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(periodB64)
            .name("&eПериод: " + currentPeriod.getDisplayName())
            .lore(
                currentPeriod.getDescription(),
                "",
                "&a▶ Нажмите, чтобы переключить период"
            ).build());

        String categoryB64 = plugin.getConfig().getString("gui.buttons.category", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ4MWRmZTJiMmY5OWUzZGVjZTRjMzQ3MjY0MzM1ZjUzMTgzZjEzYjE4YTkxN2RkYjcyMzEzZTlkMDc0NjNmZCJ9fX0=");
        Optional<Category> catOpt = plugin.getCategoryManager().getCategory(currentCategory);
        String catName = catOpt.map(Category::displayName).orElse(currentCategory);

        inventory.setItem(6, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(categoryB64)
            .name("&eКатегория: " + catName)
            .lore(
                "&7Текущий топ: &f" + catName,
                "",
                "&a▶ Нажмите, чтобы выбрать категорию"
            ).build());

        String compareB64 = plugin.getConfig().getString("gui.buttons.comparison", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdhZWU5YTc1YmYwZGY3ODk3MTgzMDE1Y2NhMGIyZDdiNzliYjNjMzRlYTU0MjRjNjc5NGJiNGZhOTVjMTZiZiJ9fX0=");
        inventory.setItem(7, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(compareB64)
            .name("&6⚔️ Сравнение")
            .lore(
                "&7Сравнить свои показатели с другим игроком",
                "",
                "&a▶ Нажмите для ввода ника"
            ).build());

        // Row 1 (9-17): Pure glass row separator
        for (int i = 9; i <= 17; i++) {
            inventory.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        // Footer (45-52 glass, 53 close)
        for (int i = 45; i < 53; i++) {
            inventory.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        String closeB64 = plugin.getConfig().getString("gui.buttons.close", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjc2NDMzZjRmZWQ2ZmMyYThjMzU5YzExZTUwOTZhZGE5OWU4ZjQxNGZmZmNmNzlkZDAxY2MyYjIzZDkyNGZhNyJ9fX0=");
        // Slot 53: Close button
        inventory.setItem(53, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(closeB64)
            .name("&cЗакрыть")
            .build());

        loadContent();
    }

    private void loadContent() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<Category> catOpt = plugin.getCategoryManager().getCategory(currentCategory);
            Category cat = catOpt.orElse(new Category(currentCategory, currentCategory, "", true, 0, "both", "none"));
            String entityType = cat.getEntityType();
            boolean isClan = cat.isClanCategory();
            String scoreUnit = cat.getScoreUnit();

            // 1. Fetch player rank/score for slot 0
            Optional<PlayerStats> playerStats = isClan ? Optional.empty()
                : plugin.getLeaderboardManager().getPlayerStats(viewer.getUniqueId(), currentCategory, currentPeriod.getDbKey());

            // 2. Fetch Top 50 entries for correct entityType ("clan" or "player")
            List<LeaderboardEntry> top50 = plugin.getLeaderboardManager().getTop(currentCategory, entityType, currentPeriod.getDbKey(), TOTAL_LIMIT);

            int maxPages = (int) Math.ceil((double) Math.min(top50.size(), TOTAL_LIMIT) / ITEMS_PER_PAGE);
            if (maxPages < 1) maxPages = 1;
            int actualPage = Math.min(page, maxPages);

            int startIndex = (actualPage - 1) * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, top50.size());
            List<LeaderboardEntry> pageEntries = (startIndex < top50.size()) ? top50.subList(startIndex, endIndex) : Collections.emptyList();

            int finalMaxPages = maxPages;
            int finalActualPage = actualPage;

            Bukkit.getScheduler().runTask(plugin, () -> {
                // Update Slot 0 (Viewer stats or clan stats)
                if (isClan) {
                    String clanName = plugin.getPlayerClanName(viewer.getUniqueId());
                    ItemBuilder headBuilder = new ItemBuilder(Material.PLAYER_HEAD)
                        .base64Head("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjJiNWY5NjhjYzg4ZDNlOTg2NWQ2ZTdhOGQ1YmU3NWVhNzNhMGEzOTRiNTFlYWE1Zjk0YzA0NzU5ZGNkYTAyZCJ9fX0=")
                        .name("&eКланы: " + cat.displayName())
                        .lore(
                            "&7Категория: &f" + cat.displayName(),
                            "&7Период: " + currentPeriod.getDisplayName(),
                            "",
                            clanName != null ? "&7Ваш клан: &a" + clanName : "&7Ваш клан: &cНе состоите в клане"
                        );
                    inventory.setItem(0, headBuilder.build());
                } else {
                    ItemBuilder headBuilder = new ItemBuilder(Material.PLAYER_HEAD)
                        .skullOwner(viewer.getUniqueId())
                        .name("&e" + viewer.getName())
                        .lore(
                            "&7Категория: &f" + cat.displayName(),
                            "&7Период: " + currentPeriod.getDisplayName(),
                            "",
                            playerStats.map(s -> s.rank() > 0 ? "&7Ваше место: &e#" + s.rank() : "&7Ваше место: &cНе в топе").orElse("&7Ваше место: &cНе в топе"),
                            playerStats.map(s -> "&7" + scoreUnit + ": &a" + (long)s.score()).orElse("&7" + scoreUnit + ": &a0")
                        );
                    inventory.setItem(0, headBuilder.build());
                }

                // Clear grid center slots
                for (int slot : GRID_SLOTS) {
                    inventory.setItem(slot, null);
                }

                // Render page items into grid slots
                for (int i = 0; i < pageEntries.size(); i++) {
                    LeaderboardEntry entry = pageEntries.get(i);
                    String color = entry.rank() == 1 ? "&6&l" : (entry.rank() <= 3 ? "&e&l" : "&7");

                    ItemBuilder itemBuilder;
                    if (entry.entityId().equals("empty")) {
                        itemBuilder = new ItemBuilder(Material.SKELETON_SKULL)
                            .name("&7#" + entry.rank() + " &8Свободное место")
                            .lore("&7Позиция: &f#" + entry.rank(), "&7Статус: &8Пусто");
                    } else if (isClan) {
                        itemBuilder = new ItemBuilder(Material.PLAYER_HEAD)
                            .base64Head("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjJiNWY5NjhjYzg4ZDNlOTg2NWQ2ZTdhOGQ1YmU3NWVhNzNhMGEzOTRiNTFlYWE1Zjk0YzA0NzU5ZGNkYTAyZCJ9fX0=")
                            .name(color + "#" + entry.rank() + " &f" + entry.entityName())
                            .lore(
                                "&7Позиция клана: &f#" + entry.rank(),
                                "&7" + scoreUnit + ": &a" + (long) entry.score()
                            );
                    } else {
                        itemBuilder = new ItemBuilder(Material.PLAYER_HEAD)
                            .name(color + "#" + entry.rank() + " &f" + entry.entityName())
                            .lore(
                                "&7Позиция: &f#" + entry.rank(),
                                "&7" + scoreUnit + ": &a" + (long) entry.score()
                            );
                        try {
                            java.util.UUID uuid = java.util.UUID.fromString(entry.entityId());
                            itemBuilder.playerProfile(uuid, entry.entityName());
                        } catch (Exception ignored) {
                            itemBuilder.skullOwner(entry.entityName());
                        }
                    }
                    inventory.setItem(GRID_SLOTS[i], itemBuilder.build());
                }

                // Pagination buttons (36 / 44 in side walls)
                if (finalActualPage > 1) {
                    String prevB64 = plugin.getConfig().getString("gui.buttons.prev-page", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdhZWU5YTc1YmYwZGY3ODk3MTgzMDE1Y2NhMGIyZDdiNzliYjNjMzRlYTU0MjRjNjc5NGJiNGZhOTVjMTZiZiJ9fX0=");
                    inventory.setItem(36, new ItemBuilder(Material.PLAYER_HEAD)
                        .base64Head(prevB64)
                        .name("&e◀ Предыдущая страница")
                        .lore("&7Страница " + (finalActualPage - 1) + " из " + finalMaxPages)
                        .build());
                } else {
                    inventory.setItem(36, null);
                }

                if (finalActualPage < finalMaxPages) {
                    String nextB64 = plugin.getConfig().getString("gui.buttons.next-page", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjgyYWQxYjRjY2RiNjZkNTkyNzJmMTc3YWMwZGRhZDE0YzA0NzJjNzcyNTM1ZTUwZmE5ZDkxNGIyMjFhNjc5NSJ9fX0=");
                    inventory.setItem(44, new ItemBuilder(Material.PLAYER_HEAD)
                        .base64Head(nextB64)
                        .name("&eСледующая страница ▶")
                        .lore("&7Страница " + (finalActualPage + 1) + " из " + finalMaxPages)
                        .build());
                } else {
                    inventory.setItem(44, null);
                }
            });
        });
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == 2) {
            // Player stats
            viewer.openInventory(new PlayerStatsGui(plugin, viewer).getInventory());
        } else if (slot == 4) {
            // Cycle period
            TimePeriod nextPeriod = currentPeriod.next();
            viewer.openInventory(new LeaderboardMainGui(plugin, viewer, currentCategory, nextPeriod, 1).getInventory());
        } else if (slot == 6) {
            // Open Category Selector
            viewer.openInventory(new CategorySelectGui(plugin, viewer, currentPeriod).getInventory());
        } else if (slot == 7) {
            // Open Comparison Anvil
            openComparisonAnvil();
        } else if (slot == 36 && page > 1) {
            // Previous page
            viewer.openInventory(new LeaderboardMainGui(plugin, viewer, currentCategory, currentPeriod, page - 1).getInventory());
        } else if (slot == 44) {
            // Next page
            int maxPages = (int) Math.ceil((double) TOTAL_LIMIT / ITEMS_PER_PAGE);
            if (page < maxPages) {
                viewer.openInventory(new LeaderboardMainGui(plugin, viewer, currentCategory, currentPeriod, page + 1).getInventory());
            }
        } else if (slot == 53) {
            viewer.closeInventory();
        }
    }

    private void openComparisonAnvil() {
        new AnvilGUI.Builder()
            .plugin(plugin)
            .title("Введите ник игрока")
            .text("Игрок")
            .itemLeft(new org.bukkit.inventory.ItemStack(Material.PAPER))
            .onClick((slot, stateSnapshot) -> {
                if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
                String targetName = stateSnapshot.getText().trim();
                if (targetName.equalsIgnoreCase(viewer.getName())) {
                    return List.of(AnvilGUI.ResponseAction.replaceInputText("Нельзя сравнить с собой!"));
                }

                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
                if (target == null) {
                    target = Bukkit.getOfflinePlayer(targetName);
                }

                OfflinePlayer finalTarget = target;
                return List.of(
                    AnvilGUI.ResponseAction.close(),
                    AnvilGUI.ResponseAction.run(() -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            viewer.openInventory(new PlayerComparisonGui(plugin, viewer, finalTarget, currentCategory, currentPeriod).getInventory());
                        });
                    })
                );
            })
            .open(viewer);
    }
}
