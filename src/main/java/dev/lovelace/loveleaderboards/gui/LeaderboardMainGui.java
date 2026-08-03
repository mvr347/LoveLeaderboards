package dev.lovelace.loveleaderboards.gui;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.Category;
import dev.lovelace.loveleaderboards.models.LeaderboardEntry;
import dev.lovelace.loveleaderboards.models.PlayerStats;
import dev.lovelace.loveleaderboards.models.TimePeriod;
import dev.lovelace.loveleaderboards.utils.ItemBuilder;
import dev.lovelace.loveleaderboards.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class LeaderboardMainGui extends BaseGui {
    private final LoveLeaderboards plugin;
    private final Player viewer;
    private final String currentCategory;
    private final TimePeriod currentPeriod;
    private final String entityType; // "player" or "clan"
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
            "player",
            1
        );
    }

    public LeaderboardMainGui(LoveLeaderboards plugin, Player viewer, String category, TimePeriod period, int page) {
        this(
            plugin,
            viewer,
            category,
            period,
            plugin.getCategoryManager().getCategory(category).map(Category::getEntityType).orElse("player"),
            page
        );
    }

    public LeaderboardMainGui(LoveLeaderboards plugin, Player viewer, String category, TimePeriod period, String entityType, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.currentCategory = category;
        this.currentPeriod = period;
        
        Optional<Category> catOpt = plugin.getCategoryManager().getCategory(category);
        String resolvedType = entityType != null ? entityType : catOpt.map(Category::getEntityType).orElse("player");
        this.entityType = resolvedType;
        this.page = Math.max(1, page);

        String title = plugin.getConfig().getString("gui.main.title", "&6⭐ Таблица Лидеров");
        this.inventory = Bukkit.createInventory(this, 54, TextUtil.parse(title));

        setup();
    }

    private void setup() {
        // gui-gen-4 Header (0-8):
        inventory.setItem(1, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(2, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(6, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(7, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(8, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        // Slot 3: Entity Type Switcher (Players vs Clans)
        boolean isClanView = "clan".equalsIgnoreCase(entityType);
        String typeB64 = isClanView
            ? "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjJiNWY5NjhjYzg4ZDNlOTg2NWQ2ZTdhOGQ1YmU3NWVhNzNhMGEzOTRiNTFlYWE1Zjk0YzA0NzU5ZGNkYTAyZCJ9fX0="
            : "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjU3YzdlOTZhODAyYzI3MDgwYzdmODA1MzgxNDM2OGVhOTRkZjg2NDQ1OTEyMGU1MTU1NzE4YjUwM2MzZWQ3In19fQ==";
        inventory.setItem(3, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(typeB64)
            .name("&eТип: &f" + (isClanView ? "👑 Кланы" : "👥 Игроки"))
            .lore(
                "",
                "&7Текущий режим топа: &f" + (isClanView ? "Топы Кланов" : "Топы Игроков"),
                "",
                "&a▶ Нажмите для переключения"
            ).build());

        // Slot 4: Time Period Switcher (Single toggle button)
        String periodB64 = plugin.getConfig().getString("gui.buttons.period", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGZjZWUzYTg4YmI1NGMwZjZlZTY2YjQ0YWM3NGZmOTdjZDkyYTA4ZjE0Y2NjMTdhMjYyMzcxZjBhYTg5MjEifX19");
        inventory.setItem(4, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(periodB64)
            .name("&eПериод: " + currentPeriod.getDisplayName())
            .lore(
                "",
                currentPeriod.getDescription(),
                "",
                "&a▶ Нажмите для смены периода"
            ).build());

        // Slot 5: Category Switcher (Single toggle button cycling through categories in-place)
        String categoryB64 = plugin.getConfig().getString("gui.buttons.category", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ4MWRmZTJiMmY5OWUzZGVjZTRjMzQ3MjY0MzM1ZjUzMTgzZjEzYjE4YTkxN2RkYjcyMzEzZTlkMDc0NjNmZCJ9fX0=");
        Optional<Category> catOpt = plugin.getCategoryManager().getCategory(currentCategory);
        String catName = catOpt.map(Category::displayName).orElse(currentCategory);

        inventory.setItem(5, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(categoryB64)
            .name("&eКатегория: " + catName)
            .lore(
                "",
                "&7Текущий топ: &f" + catName,
                "",
                "&aЛКМ &7— следующая категория",
                "&aПКМ &7— предыдущая категория"
            ).build());

        // Row 1 (9-17): PURE GLASS ROW (gui-gen-4 Rule 4)
        for (int i = 9; i <= 17; i++) {
            inventory.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        // Footer (45-53):
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
            Optional<Category> catOpt = plugin.getCategoryManager().getCategory(currentCategory);
            Category cat = catOpt.orElse(new Category(currentCategory, currentCategory, "", true, 0, "both", "none"));
            boolean isClanView = "clan".equalsIgnoreCase(entityType);
            String scoreUnit = cat.getScoreUnit();

            // 1. Fetch player rank/score for Slot 0
            Optional<PlayerStats> playerStats = isClanView ? Optional.empty()
                : plugin.getLeaderboardManager().getPlayerStats(viewer.getUniqueId(), currentCategory, currentPeriod.getDbKey());

            // 2. Fetch Top 50 entries for selected entityType ("clan" or "player")
            List<LeaderboardEntry> top50 = plugin.getLeaderboardManager().getTop(currentCategory, entityType, currentPeriod.getDbKey(), TOTAL_LIMIT);

            int maxPages = (int) Math.ceil((double) Math.min(top50.size(), TOTAL_LIMIT) / ITEMS_PER_PAGE);
            if (maxPages < 1) maxPages = 1;
            int actualPage = Math.min(page, maxPages);

            int startIndex = (actualPage - 1) * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, top50.size());
            List<LeaderboardEntry> pageEntries = (startIndex < top50.size()) ? top50.subList(startIndex, endIndex) : Collections.emptyList();

            int finalMaxPages = maxPages;
            int finalActualPage = actualPage;

            // 3. Pre-build Slot 0 item ASYNCHRONOUSLY
            org.bukkit.inventory.ItemStack slot0Item;
            if (isClanView) {
                String clanName = plugin.getPlayerClanName(viewer.getUniqueId());
                slot0Item = new ItemBuilder(Material.RED_BANNER)
                    .name("&eКланы: " + cat.displayName())
                    .lore(
                        "",
                        "&7Категория: &f" + cat.displayName(),
                        "&7Период: " + currentPeriod.getDisplayName(),
                        "",
                        clanName != null ? "&7Ваш клан: &a" + clanName : "&7Ваш клан: &cНе состоите в клане"
                    ).build();
            } else {
                slot0Item = new ItemBuilder(Material.PLAYER_HEAD)
                    .skullOwner(viewer.getUniqueId())
                    .name("&e" + viewer.getName())
                    .lore(
                        "",
                        "&7Категория: &f" + cat.displayName(),
                        "&7Период: " + currentPeriod.getDisplayName(),
                        "",
                        playerStats.map(s -> s.rank() > 0 ? "&7Ваше место: &e#" + s.rank() : "&7Ваше место: &cНе в топе").orElse("&7Ваше место: &cНе в топе"),
                        playerStats.map(s -> "&7" + scoreUnit + ": &a" + (long)s.score()).orElse("&7" + scoreUnit + ": &a0"),
                        "",
                        "&a▶ Нажмите, чтобы открыть статистику"
                    ).build();
            }

            // 4. Pre-build Grid Items ASYNCHRONOUSLY
            List<org.bukkit.inventory.ItemStack> gridItems = new ArrayList<>();
            for (int i = 0; i < pageEntries.size(); i++) {
                LeaderboardEntry entry = pageEntries.get(i);
                String color = entry.rank() == 1 ? "&6&l" : (entry.rank() <= 3 ? "&e&l" : "&7");

                ItemBuilder itemBuilder;
                if (entry.entityId().equals("empty")) {
                    // Empty rank item: WHITE BANNER (not skeleton skull!)
                    itemBuilder = new ItemBuilder(Material.WHITE_BANNER)
                        .name("&7#" + entry.rank() + " &8Свободное место")
                        .lore("", "&7Позиция: &f#" + entry.rank(), "&7Статус: &8Пусто");
                } else if (isClanView) {
                    // Clan entry item: RED BANNER (instead of player head!)
                    itemBuilder = new ItemBuilder(Material.RED_BANNER)
                        .name(color + "#" + entry.rank() + " &f" + entry.entityName())
                        .lore(
                            "",
                            "&7Позиция клана: &f#" + entry.rank(),
                            "&7" + scoreUnit + ": &a" + (long) entry.score()
                        );
                } else {
                    itemBuilder = new ItemBuilder(Material.PLAYER_HEAD)
                        .name(color + "#" + entry.rank() + " &f" + entry.entityName())
                        .lore(
                            "",
                            "&7Позиция: &f#" + entry.rank(),
                            "&7" + scoreUnit + ": &a" + (long) entry.score(),
                            "",
                            "&aЛКМ &7— статистику игрока",
                            "&aПКМ &7— сравнить со мной"
                        );
                    try {
                        java.util.UUID uuid = java.util.UUID.fromString(entry.entityId());
                        itemBuilder.playerProfile(uuid, entry.entityName());
                    } catch (Exception ignored) {
                        itemBuilder.skullOwner(entry.entityName());
                    }
                }
                gridItems.add(itemBuilder.build());
            }

            // 5. Pre-build Pagination Items ASYNCHRONOUSLY
            org.bukkit.inventory.ItemStack prevItem = null;
            if (finalActualPage > 1) {
                String prevB64 = plugin.getConfig().getString("gui.buttons.prev-page", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdhZWU5YTc1YmYwZGY3ODk3MTgzMDE1Y2NhMGIyZDdiNzliYjNjMzRlYTU0MjRjNjc5NGJiNGZhOTVjMTZiZiJ9fX0=");
                prevItem = new ItemBuilder(Material.PLAYER_HEAD)
                    .base64Head(prevB64)
                    .name("&e◀ Предыдущая страница")
                    .lore("", "&7Страница " + (finalActualPage - 1) + " из " + finalMaxPages)
                    .build();
            }

            org.bukkit.inventory.ItemStack nextItem = null;
            if (finalActualPage < finalMaxPages) {
                String nextB64 = plugin.getConfig().getString("gui.buttons.next-page", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjgyYWQxYjRjY2RiNjZkNTkyNzJmMTc3YWMwZGRhZDE0YzA0NzJjNzcyNTM1ZTUwZmE5ZDkxNGIyMjFhNjc5NSJ9fX0=");
                nextItem = new ItemBuilder(Material.PLAYER_HEAD)
                    .base64Head(nextB64)
                    .name("&eСледующая страница ▶")
                    .lore("", "&7Страница " + (finalActualPage + 1) + " из " + finalMaxPages)
                    .build();
            }

            org.bukkit.inventory.ItemStack finalPrevItem = prevItem;
            org.bukkit.inventory.ItemStack finalNextItem = nextItem;

            // 6. Apply to inventory on main thread instantly
            Bukkit.getScheduler().runTask(plugin, () -> {
                inventory.setItem(0, slot0Item);

                for (int slot : GRID_SLOTS) {
                    inventory.setItem(slot, null);
                }

                for (int i = 0; i < gridItems.size(); i++) {
                    inventory.setItem(GRID_SLOTS[i], gridItems.get(i));
                }

                inventory.setItem(36, finalPrevItem);
                inventory.setItem(44, finalNextItem);
            });
        });
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        GuiNavigationManager.GuiState currentState = new GuiNavigationManager.GuiState(
            GuiNavigationManager.GuiState.GuiType.MAIN_LEADERBOARD,
            currentCategory, currentPeriod, entityType, null, page
        );

        if (slot == 0) {
            // Slot 0 click: Open Player Statistics (PlayerStatsGui)
            GuiNavigationManager.pushState(viewer, currentState);
            viewer.openInventory(new PlayerStatsGui(plugin, viewer, viewer, currentCategory, currentPeriod).getInventory());
            return;
        }

        if (slot == 3) {
            // Toggle entity type (player <-> clan)
            String nextType = "clan".equalsIgnoreCase(entityType) ? "player" : "clan";
            Optional<Category> firstForType = plugin.getCategoryManager().getAllCategories().stream()
                .filter(Category::enabled)
                .filter(c -> c.getEntityType().equalsIgnoreCase(nextType))
                .findFirst();
            String nextCat = firstForType.map(Category::name).orElse(currentCategory);

            viewer.openInventory(new LeaderboardMainGui(plugin, viewer, nextCat, currentPeriod, nextType, 1).getInventory());
            return;
        }

        if (slot == 4) {
            // Cycle period
            TimePeriod nextPeriod = currentPeriod.next();
            viewer.openInventory(new LeaderboardMainGui(plugin, viewer, currentCategory, nextPeriod, entityType, 1).getInventory());
            return;
        }

        if (slot == 5) {
            // Cycle category in-place (Single toggle button like period!)
            boolean forward = !event.isRightClick();
            String nextCat = plugin.getCategoryManager().getNextCategory(currentCategory, entityType, forward);
            viewer.openInventory(new LeaderboardMainGui(plugin, viewer, nextCat, currentPeriod, entityType, 1).getInventory());
            return;
        }

        if (slot == 36 && page > 1) {
            // Previous page
            viewer.openInventory(new LeaderboardMainGui(plugin, viewer, currentCategory, currentPeriod, entityType, page - 1).getInventory());
            return;
        }

        if (slot == 44) {
            // Next page
            int maxPages = (int) Math.ceil((double) TOTAL_LIMIT / ITEMS_PER_PAGE);
            if (page < maxPages) {
                viewer.openInventory(new LeaderboardMainGui(plugin, viewer, currentCategory, currentPeriod, entityType, page + 1).getInventory());
            }
            return;
        }

        // Grid clicks: player head entry
        if (!"clan".equalsIgnoreCase(entityType)) {
            for (int i = 0; i < GRID_SLOTS.length; i++) {
                if (GRID_SLOTS[i] == slot) {
                    List<LeaderboardEntry> top50 = plugin.getLeaderboardManager().getTop(currentCategory, entityType, currentPeriod.getDbKey(), TOTAL_LIMIT);
                    int startIndex = (page - 1) * ITEMS_PER_PAGE;
                    int index = startIndex + i;
                    if (index < top50.size()) {
                        LeaderboardEntry entry = top50.get(index);
                        if (!entry.entityId().equals("empty")) {
                            try {
                                java.util.UUID uuid = java.util.UUID.fromString(entry.entityId());
                                OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(uuid);
                                GuiNavigationManager.pushState(viewer, currentState);

                                if (event.isRightClick()) {
                                    // Compare directly
                                    viewer.openInventory(new PlayerComparisonGui(plugin, viewer, targetPlayer, currentCategory, currentPeriod).getInventory());
                                } else {
                                    // Open target player stats
                                    viewer.openInventory(new PlayerStatsGui(plugin, viewer, targetPlayer, currentCategory, currentPeriod).getInventory());
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    return;
                }
            }
        }

        if (slot == 52) {
            // Back button
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
