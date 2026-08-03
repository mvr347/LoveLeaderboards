package dev.lovelace.loveleaderboards.gui;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.Category;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PlayerStatsGui extends BaseGui {
    private final LoveLeaderboards plugin;
    private final Player viewer;
    private final OfflinePlayer targetPlayer;
    private final String currentCategory;
    private final TimePeriod currentPeriod;
    private String entityTypeFilter;

    private static final int[] GRID_SLOTS = {
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    public PlayerStatsGui(LoveLeaderboards plugin, Player viewer) {
        this(plugin, viewer, viewer, plugin.getCategoryManager().getAllCategories().stream().findFirst().map(Category::name).orElse("kills"), TimePeriod.ALL_TIME);
    }

    public PlayerStatsGui(LoveLeaderboards plugin, Player viewer, OfflinePlayer targetPlayer, String category, TimePeriod period) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetPlayer = targetPlayer != null ? targetPlayer : viewer;
        this.currentCategory = category;
        this.currentPeriod = period != null ? period : TimePeriod.ALL_TIME;
        this.entityTypeFilter = "player";

        boolean isSelf = this.targetPlayer.getUniqueId().equals(viewer.getUniqueId());
        String titleKey = isSelf ? "gui.player-stats.title" : "gui.player-stats.target-title";
        String defaultTitle = isSelf ? "&6📊 Моя Статистика" : "&6📊 Статистика: &e" + (this.targetPlayer.getName() != null ? this.targetPlayer.getName() : "Игрока");
        String title = plugin.getConfig().getString("gui.player-stats.title", defaultTitle);

        this.inventory = Bukkit.createInventory(this, 54, TextUtil.parse(title));
        setup();
    }

    private void setup() {
        String clanName = plugin.getPlayerClanName(targetPlayer.getUniqueId());
        boolean hasClan = clanName != null && !clanName.isEmpty();

        if (!hasClan) {
            this.entityTypeFilter = "player";
        }

        // gui-gen-4 Header (0-8)
        boolean isSelf = targetPlayer.getUniqueId().equals(viewer.getUniqueId());
        boolean isClanMode = "clan".equalsIgnoreCase(entityTypeFilter);

        if (isClanMode && hasClan) {
            inventory.setItem(0, new ItemBuilder(Material.RED_BANNER)
                .name("&eКлан: &a" + clanName)
                .lore(
                    "",
                    "&7Статистика клана по категориям",
                    "&7Клан: &a" + clanName
                )
                .build());
        } else {
            inventory.setItem(0, new ItemBuilder(Material.PLAYER_HEAD)
                .skullOwner(targetPlayer.getUniqueId())
                .name("&e" + (targetPlayer.getName() != null ? targetPlayer.getName() : "Игрок"))
                .lore(
                    "",
                    "&7Профиль: &f" + (targetPlayer.getName() != null ? targetPlayer.getName() : "Игрок"),
                    isSelf ? "&7Личная статистика по категориям" : "&7Нажмите для сравнения со мной",
                    "",
                    isSelf ? "&a▶ Вы смотрите свой профиль" : "&a▶ Нажмите для сравнения"
                )
                .build());
        }

        inventory.setItem(1, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(2, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(6, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(7, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(8, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        // Slot 3: Mode Switcher ("Я / Мой клан"). Hidden if player is not in a clan!
        if (hasClan) {
            if (isClanMode) {
                inventory.setItem(3, new ItemBuilder(Material.RED_BANNER)
                    .name("&eРежим: &f👑 Мой клан")
                    .lore(
                        "",
                        "&7Текущий режим: &fМой клан (&a" + clanName + "&f)",
                        "&7Показывает результаты вашего клана",
                        "",
                        "&a▶ Нажмите для перехода к \"Я\""
                    ).build());
            } else {
                String playerB64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjU3YzdlOTZhODAyYzI3MDgwYzdmODA1MzgxNDM2OGVhOTRkZjg2NDQ1OTEyMGU1MTU1NzE4YjUwM2MzZWQ3In19fQ==";
                inventory.setItem(3, new ItemBuilder(Material.PLAYER_HEAD)
                    .base64Head(playerB64)
                    .name("&eРежим: &f👤 Я")
                    .lore(
                        "",
                        "&7Текущий режим: &fЛичная статистика (Я)",
                        "&7Клан: &a" + clanName,
                        "",
                        "&a▶ Нажмите для перехода к \"Мой клан\""
                    ).build());
            }
        } else {
            // Player has no clan -> button is NOT shown (Glass)!
            inventory.setItem(3, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        // Slot 4: Period selector
        String periodB64 = plugin.getConfig().getString("gui.buttons.period", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGZjZWUzYTg4YmI1NGMwZjZlZTY2YjQ0YWM3NGZmOTdjZDkyYTA4ZjE0Y2NjMTdhMjYyMzcxZjBhYTg5MjEifX19");
        inventory.setItem(4, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(periodB64)
            .name("&eПериод: " + currentPeriod.getDisplayName())
            .lore("", currentPeriod.getDescription(), "", "&a▶ Нажмите для смены периода")
            .build());

        // Slot 5: Compare button inside stats
        String compareB64 = plugin.getConfig().getString("gui.buttons.comparison", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdhZWU5YTc1YmYwZGY3ODk3MTgzMDE1Y2NhMGIyZDdiNzliYjNjMzRlYTU0MjRjNjc5NGJiNGZhOTVjMTZiZiJ9fX0=");
        inventory.setItem(5, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(compareB64)
            .name("&6⚔️ Сравнить показатели")
            .lore("", "&7Сравнить показания " + (targetPlayer.getName() != null ? targetPlayer.getName() : "игрока"), "&7с вашей статистикой", "", "&a▶ Нажмите для сравнения")
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
            List<Category> matchingCats = plugin.getCategoryManager().getAllCategories().stream()
                .filter(Category::enabled)
                .filter(c -> c.getEntityType().equalsIgnoreCase(entityTypeFilter))
                .toList();

            List<org.bukkit.inventory.ItemStack> builtItems = new ArrayList<>();
            String clanName = plugin.getPlayerClanName(targetPlayer.getUniqueId());

            for (Category cat : matchingCats) {
                PlayerStats stats;
                if (cat.isClanCategory() && clanName != null) {
                    UUID clanUuid = UUID.nameUUIDFromBytes(clanName.getBytes());
                    Optional<PlayerStats> statsOpt = plugin.getLeaderboardManager().getPlayerStats(clanUuid, cat.name(), currentPeriod.getDbKey());
                    stats = statsOpt.orElse(new PlayerStats(clanUuid, clanName, 0, 0));
                } else {
                    Optional<PlayerStats> statsOpt = plugin.getLeaderboardManager().getPlayerStats(targetPlayer.getUniqueId(), cat.name(), currentPeriod.getDbKey());
                    stats = statsOpt.orElse(new PlayerStats(targetPlayer.getUniqueId(), targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown", 0, 0));
                }

                Material mat = cat.isClanCategory() ? Material.RED_BANNER : Material.PLAYER_HEAD;
                ItemBuilder builder = new ItemBuilder(mat);

                if (!cat.isClanCategory()) {
                    String defaultCatB64 = switch (cat.name().toLowerCase()) {
                        case "kills" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdhZWU5YTc1YmYwZGY3ODk3MTgzMDE1Y2NhMGIyZDdiNzliYjNjMzRlYTU0MjRjNjc5NGJiNGZhOTVjMTZiZiJ9fX0=";
                        case "bounty-completed" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ4MWRmZTJiMmY5OWUzZGVjZTRjMzQ3MjY0MzM1ZjUzMTgzZjEzYjE4YTkxN2RkYjcyMzEzZTlkMDc0NjNmZCJ9fX0=";
                        case "contracts-completed" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ4MWRmZTJiMmY5OWUzZGVjZTRjMzQ3MjY0MzM1ZjUzMTgzZjEzYjE4YTkxN2RkYjcyMzEzZTlkMDc0NjNmZCJ9fX0=";
                        default -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ4MWRmZTJiMmY5OWUzZGVjZTRjMzQ3MjY0MzM1ZjUzMTgzZjEzYjE4YTkxN2RkYjcyMzEzZTlkMDc0NjNmZCJ9fX0=";
                    };
                    String catHead = plugin.getConfig().getString("gui.buttons.categories." + cat.name(), defaultCatB64);
                    builder.base64Head(catHead);
                }

                builder.name(cat.displayName())
                    .lore(
                        "",
                        "&7Категория: &f" + cat.displayName(),
                        "&7Период: " + currentPeriod.getDisplayName(),
                        cat.isClanCategory() ? "&7Место клана: &e#" + (stats.rank() > 0 ? stats.rank() : "Не в топе") : "&7Место: &e#" + (stats.rank() > 0 ? stats.rank() : "Не в топе"),
                        "&7" + cat.getScoreUnit() + ": &a" + (long) stats.score(),
                        "",
                        "&aЛКМ &7— открыть этот топ",
                        "&aПКМ &7— сравнить показания"
                    );
                builtItems.add(builder.build());
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                // Clear grid center slots (Rule 8)
                for (int slot : GRID_SLOTS) {
                    inventory.setItem(slot, null);
                }

                for (int i = 0; i < builtItems.size() && i < GRID_SLOTS.length; i++) {
                    inventory.setItem(GRID_SLOTS[i], builtItems.get(i));
                }
            });
        });
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        GuiNavigationManager.GuiState currentState = new GuiNavigationManager.GuiState(
            GuiNavigationManager.GuiState.GuiType.PLAYER_STATS,
            currentCategory, currentPeriod, entityTypeFilter, targetPlayer, 1
        );

        if (slot == 0 && !targetPlayer.getUniqueId().equals(viewer.getUniqueId())) {
            // Clicking slot 0 on another player opens comparison
            GuiNavigationManager.pushState(viewer, currentState);
            viewer.openInventory(new PlayerComparisonGui(plugin, viewer, targetPlayer, currentCategory, currentPeriod).getInventory());
            return;
        }

        if (slot == 3) {
            String clanName = plugin.getPlayerClanName(targetPlayer.getUniqueId());
            if (clanName != null && !clanName.isEmpty()) {
                this.entityTypeFilter = "clan".equalsIgnoreCase(entityTypeFilter) ? "player" : "clan";
                setup();
            }
            return;
        }

        if (slot == 4) {
            // Cycle period
            TimePeriod nextPeriod = currentPeriod.next();
            viewer.openInventory(new PlayerStatsGui(plugin, viewer, targetPlayer, currentCategory, nextPeriod).getInventory());
            return;
        }

        if (slot == 5) {
            // Open comparison with targetPlayer
            if (targetPlayer.getUniqueId().equals(viewer.getUniqueId())) {
                // Target is self -> open main leaderboard
                viewer.openInventory(new LeaderboardMainGui(plugin, viewer, currentCategory, currentPeriod, entityTypeFilter, 1).getInventory());
            } else {
                GuiNavigationManager.pushState(viewer, currentState);
                viewer.openInventory(new PlayerComparisonGui(plugin, viewer, targetPlayer, currentCategory, currentPeriod).getInventory());
            }
            return;
        }

        // Category grid clicks
        List<Category> matchingCats = plugin.getCategoryManager().getAllCategories().stream()
            .filter(Category::enabled)
            .filter(c -> c.getEntityType().equalsIgnoreCase(entityTypeFilter))
            .toList();

        for (int i = 0; i < GRID_SLOTS.length && i < matchingCats.size(); i++) {
            if (GRID_SLOTS[i] == slot) {
                Category cat = matchingCats.get(i);
                GuiNavigationManager.pushState(viewer, currentState);
                if (event.isRightClick() && !targetPlayer.getUniqueId().equals(viewer.getUniqueId())) {
                    viewer.openInventory(new PlayerComparisonGui(plugin, viewer, targetPlayer, cat.name(), currentPeriod).getInventory());
                } else {
                    viewer.openInventory(new LeaderboardMainGui(plugin, viewer, cat.name(), currentPeriod, cat.getEntityType(), 1).getInventory());
                }
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
