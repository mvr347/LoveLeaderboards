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

    public PlayerStatsGui(LoveLeaderboards plugin, Player viewer) {
        this(plugin, viewer, viewer,
                plugin.getCategoryManager().getAllCategories().stream().findFirst().map(Category::name).orElse("kills"),
                TimePeriod.ALL_TIME);
    }

    public PlayerStatsGui(LoveLeaderboards plugin, Player viewer, OfflinePlayer targetPlayer, String category,
            TimePeriod period) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetPlayer = targetPlayer != null ? targetPlayer : viewer;
        this.currentCategory = category;
        this.currentPeriod = period != null ? period : TimePeriod.ALL_TIME;
        this.entityTypeFilter = "player";

        boolean isSelf = this.targetPlayer.getUniqueId().equals(viewer.getUniqueId());
        String titleKey = isSelf ? "gui.player-stats.title" : "gui.player-stats.target-title";
        String defaultTitle = isSelf ? "&6📊 Моя Статистика"
                : "&6📊 Статистика: &e"
                        + (this.targetPlayer.getName() != null ? this.targetPlayer.getName() : "Игрока");
        String title = plugin.getConfig().getString(titleKey, defaultTitle);

        int size = plugin.getConfig().getInt("gui.player-stats.size", 27);
        this.inventory = Bukkit.createInventory(this, size, TextUtil.parse(title));
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
                            "&7Клан: &a" + clanName,
                            "",
                            "&a⭐ Это ваш клан!")
                    .build());
        } else {
            inventory.setItem(0, new ItemBuilder(Material.PLAYER_HEAD)
                    .skullOwner(targetPlayer.getUniqueId())
                    .name("&e" + (targetPlayer.getName() != null ? targetPlayer.getName() : "Игрок"))
                    .lore(
                            "",
                            "&7Профиль: &f" + (targetPlayer.getName() != null ? targetPlayer.getName() : "Игрок"),
                            isSelf ? "&7Личная статистика по категориям" : "&7Статистика оппонента",
                            "",
                            isSelf ? "&a⭐ Это вы!" : "&a▶ Нажмите для сравнения со мной")
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
                                "&a▶ Нажмите для перехода к \"Я\"")
                        .build());
            } else {
                String playerB64 = getButtonHead(plugin, "type-player");
                inventory.setItem(3, new ItemBuilder(Material.PLAYER_HEAD)
                        .base64Head(playerB64)
                        .name("&eРежим: &f👤 Я")
                        .lore(
                                "",
                                "&7Текущий режим: &fЛичная статистика (Я)",
                                "&7Клан: &a" + clanName,
                                "",
                                "&a▶ Нажмите для перехода к \"Мой клан\"")
                        .build());
            }
        } else {
            // Player has no clan -> show inactive Mode button
            String playerB64 = getButtonHead(plugin, "type-player");
            inventory.setItem(3, new ItemBuilder(Material.PLAYER_HEAD)
                    .base64Head(playerB64)
                    .name("&eРежим: &f👤 Я")
                    .lore(
                            "",
                            "&7Текущий режим: &fЛичная статистика (Я)",
                            "&7Клан: &cВы не состоите в клане",
                            "",
                            "&cВступление в клан разблокирует режим клана")
                    .build());
        }

        inventory.setItem(4, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        // Slot 5: Period selector
        String periodB64 = getButtonHead(plugin, "period");
        inventory.setItem(5, new ItemBuilder(Material.PLAYER_HEAD)
                .base64Head(periodB64)
                .name("&eПериод: " + currentPeriod.getDisplayName())
                .lore("", currentPeriod.getDescription(), "", "&a▶ Нажмите для смены периода")
                .build());

        // Footer (18-26) for 27-slot menu (gui-gen-4 Rule 7)
        for (int i = 18; i < 25; i++) {
            inventory.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        // Slot 25: Back button
        if (GuiNavigationManager.hasHistory(viewer)) {
            String backB64 = getButtonHead(plugin, "back");
            inventory.setItem(25, new ItemBuilder(Material.PLAYER_HEAD)
                    .base64Head(backB64)
                    .name("&e◀ Назад")
                    .lore("", "&7Вернуться в предыдущее меню", "", "&a▶ Нажмите для возврата")
                    .build());
        } else {
            inventory.setItem(25, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        // Slot 26: Close button (always last slot in 27-slot menu)
        String closeB64 = getButtonHead(plugin, "close");
        inventory.setItem(26, new ItemBuilder(Material.PLAYER_HEAD)
                .base64Head(closeB64)
                .name("&cЗакрыть")
                .build());

        loadContent();
    }

    private int[] getGridSlots(int count) {
        if (count <= 7) {
            return switch (count) {
                case 1 -> new int[] { 13 };
                case 2 -> new int[] { 12, 14 };
                case 3 -> new int[] { 12, 13, 14 };
                case 4 -> new int[] { 11, 12, 14, 15 };
                case 5 -> new int[] { 11, 12, 13, 14, 15 };
                case 6 -> new int[] { 10, 11, 12, 14, 15, 16 };
                case 7 -> new int[] { 10, 11, 12, 13, 14, 15, 16 };
                default -> new int[] { 10, 11, 12, 13, 14, 15, 16 };
            };
        }
        return new int[] { 9, 10, 11, 12, 13, 14, 15, 16, 17 };
    }

    private void loadContent() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Category> matchingCats = plugin.getCategoryManager().getAllCategories().stream()
                    .filter(Category::enabled)
                    .filter(c -> c.getEntityType().equalsIgnoreCase(entityTypeFilter))
                    .toList();

            List<org.bukkit.inventory.ItemStack> builtItems = new ArrayList<>();
            String clanName = plugin.getPlayerClanName(targetPlayer.getUniqueId());
            String clanId = plugin.getPlayerClanId(targetPlayer.getUniqueId());
            boolean isSelf = targetPlayer.getUniqueId().equals(viewer.getUniqueId());

            for (Category cat : matchingCats) {
                PlayerStats stats;
                if (cat.isClanCategory() && clanName != null) {
                    String searchId = clanId != null ? clanId : clanName;
                    Optional<PlayerStats> statsOpt = plugin.getLeaderboardManager().getEntityStats("clan", searchId,
                            cat.name(), currentPeriod.getDbKey());
                    UUID displayUuid;
                    try {
                        displayUuid = clanId != null ? UUID.fromString(clanId)
                                : UUID.nameUUIDFromBytes(clanName.getBytes());
                    } catch (Exception e) {
                        displayUuid = UUID.nameUUIDFromBytes(clanName.getBytes());
                    }
                    stats = statsOpt.orElse(new PlayerStats(displayUuid, clanName, 0, 0));
                } else {
                    Optional<PlayerStats> statsOpt = plugin.getLeaderboardManager()
                            .getPlayerStats(targetPlayer.getUniqueId(), cat.name(), currentPeriod.getDbKey());
                    stats = statsOpt.orElse(new PlayerStats(targetPlayer.getUniqueId(),
                            targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown", 0, 0));
                }

                String catHead = getCategoryHead(plugin, cat.name());
                ItemBuilder builder;
                if (catHead != null && !catHead.isEmpty()) {
                    builder = new ItemBuilder(Material.PLAYER_HEAD).base64Head(catHead);
                } else {
                    Material mat = cat.isClanCategory() ? Material.RED_BANNER : Material.PLAYER_HEAD;
                    builder = new ItemBuilder(mat);
                }

                if (isSelf) {
                    builder.name(cat.displayName())
                            .lore(
                                    "",
                                    "&7Категория: &f" + cat.displayName(),
                                    "&7Период: " + currentPeriod.getDisplayName(),
                                    cat.isClanCategory()
                                            ? "&7Место вашего клана: &e#"
                                                    + (stats.rank() > 0 ? stats.rank() : "Не в топе")
                                            : "&7Ваше место: &e#" + (stats.rank() > 0 ? stats.rank() : "Не в топе"),
                                    "&7" + cat.getScoreUnit() + ": &a" + (long) stats.score(),
                                    "",
                                    "&a▶ Нажмите для открытия этого топа");
                } else {
                    builder.name(cat.displayName())
                            .lore(
                                    "",
                                    "&7Категория: &f" + cat.displayName(),
                                    "&7Период: " + currentPeriod.getDisplayName(),
                                    cat.isClanCategory()
                                            ? "&7Место клана: &e#" + (stats.rank() > 0 ? stats.rank() : "Не в топе")
                                            : "&7Место: &e#" + (stats.rank() > 0 ? stats.rank() : "Не в топе"),
                                    "&7" + cat.getScoreUnit() + ": &a" + (long) stats.score(),
                                    "",
                                    "&aЛКМ &7— открыть этот топ",
                                    "&aПКМ &7— сравнить показания");
                }
                builtItems.add(builder.build());
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                // Clear grid center slots (9-17) (Rule 8)
                for (int i = 9; i <= 17; i++) {
                    inventory.setItem(i, null);
                }

                int[] gridSlots = getGridSlots(builtItems.size());
                for (int i = 0; i < builtItems.size() && i < gridSlots.length; i++) {
                    inventory.setItem(gridSlots[i], builtItems.get(i));
                }
            });
        });
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        GuiNavigationManager.GuiState currentState = new GuiNavigationManager.GuiState(
                GuiNavigationManager.GuiState.GuiType.PLAYER_STATS,
                currentCategory, currentPeriod, entityTypeFilter, targetPlayer, 1);

        if (slot == 0 && !targetPlayer.getUniqueId().equals(viewer.getUniqueId())) {
            // Clicking slot 0 on another player opens comparison
            GuiNavigationManager.pushState(viewer, currentState);
            viewer.openInventory(new PlayerComparisonGui(plugin, viewer, targetPlayer, currentCategory, currentPeriod)
                    .getInventory());
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

        if (slot == 5) {
            // Slot 5 click -> cycle period
            TimePeriod nextPeriod = currentPeriod.next();
            viewer.openInventory(
                    new PlayerStatsGui(plugin, viewer, targetPlayer, currentCategory, nextPeriod).getInventory());
            return;
        }

        // Category grid clicks
        List<Category> matchingCats = plugin.getCategoryManager().getAllCategories().stream()
                .filter(Category::enabled)
                .filter(c -> c.getEntityType().equalsIgnoreCase(entityTypeFilter))
                .toList();

        int[] gridSlots = getGridSlots(matchingCats.size());
        for (int i = 0; i < gridSlots.length && i < matchingCats.size(); i++) {
            if (gridSlots[i] == slot) {
                Category cat = matchingCats.get(i);
                GuiNavigationManager.pushState(viewer, currentState);
                if (event.isRightClick() && !targetPlayer.getUniqueId().equals(viewer.getUniqueId())) {
                    viewer.openInventory(
                            new PlayerComparisonGui(plugin, viewer, targetPlayer, cat.name(), currentPeriod)
                                    .getInventory());
                } else {
                    viewer.openInventory(
                            new LeaderboardMainGui(plugin, viewer, cat.name(), currentPeriod, cat.getEntityType(), 1)
                                    .getInventory());
                }
                return;
            }
        }

        if (slot == 25) {
            if (GuiNavigationManager.hasHistory(viewer)) {
                GuiNavigationManager.goBack(plugin, viewer);
            }
            return;
        }

        if (slot == 26) {
            GuiNavigationManager.clearHistory(viewer);
            viewer.closeInventory();
        }
    }
}
