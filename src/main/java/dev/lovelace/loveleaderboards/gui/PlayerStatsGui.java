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
    private final String entityTypeFilter;

    public PlayerStatsGui(LoveLeaderboards plugin, Player viewer) {
        this(plugin, viewer, viewer,
                plugin.getCategoryManager().getAllCategories().stream().findFirst().map(Category::name).orElse("kills"),
                TimePeriod.ALL_TIME);
    }

    public PlayerStatsGui(LoveLeaderboards plugin, Player viewer, OfflinePlayer targetPlayer, String category,
            TimePeriod period) {
        this(plugin, viewer, targetPlayer, category, period, "player");
    }

    public PlayerStatsGui(LoveLeaderboards plugin, Player viewer, OfflinePlayer targetPlayer, String category,
            TimePeriod period, String entityTypeFilter) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetPlayer = targetPlayer != null ? targetPlayer : viewer;
        this.currentCategory = category;
        this.currentPeriod = period != null ? period : TimePeriod.ALL_TIME;
        String clan = plugin.getPlayerClanName(this.targetPlayer.getUniqueId());
        boolean hasClan = clan != null && !clan.isEmpty();
        this.entityTypeFilter = hasClan && "clan".equalsIgnoreCase(entityTypeFilter) ? "clan" : "player";

        boolean isSelf = this.targetPlayer.getUniqueId().equals(viewer.getUniqueId());
        String titleKey = isSelf ? "gui.player-stats.title" : "gui.player-stats.target-title";
        String defaultTitle = isSelf ? "&6📊 Моя Статистика"
                : "&6📊 Статистика: &e"
                        + (this.targetPlayer.getName() != null ? this.targetPlayer.getName() : "Игрока");
        String title = plugin.getConfig().getString(titleKey, defaultTitle);

        // Size follows the category count so the grid never needs the side walls (gui_gen rule 8):
        // <=7 -> 27 (one work row), <=14 -> 36 (two rows), more -> 54 (Row1 glass + three rows, max 21).
        int count = switchableCategories(plugin, this.entityTypeFilter).size();
        int size = count <= 7 ? 27 : (count <= 14 ? 36 : 54);
        this.inventory = Bukkit.createInventory(this, size, TextUtil.parse(title));
        setup();
    }

    private void setup() {
        String clanName = plugin.getPlayerClanName(targetPlayer.getUniqueId());
        boolean hasClan = clanName != null && !clanName.isEmpty();

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

        // Header controls: 2 buttons -> slots 3 and 5 (gui_gen rule 8); the rest is glass.
        for (int i : new int[] {1, 2, 4, 6, 7, 8}) {
            inventory.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        // Slot 3: Mode switcher ("Я" / "Мой клан"). Without a clan it only shows the current mode.
        List<String> modeLore = modeSwitchLore(List.of("Я", "Мой клан"), isClanMode ? 1 : 0);
        if (!hasClan) {
            modeLore = new ArrayList<>(modeLore.subList(0, 2));
            modeLore.add("");
            modeLore.add("&cВступление в клан разблокирует режим клана");
        }
        inventory.setItem(3, new ItemBuilder(Material.PLAYER_HEAD)
                .base64Head(getButtonHead(plugin, isClanMode ? "type-clan" : "type-player"))
                .name(MODE_SWITCH_NAME)
                .lore(modeLore)
                .build());

        // Slot 5: Period switcher
        inventory.setItem(5, new ItemBuilder(Material.PLAYER_HEAD)
                .base64Head(getButtonHead(plugin, "period"))
                .name(PERIOD_SWITCH_NAME)
                .lore(periodSwitchLore(currentPeriod))
                .build());

        int size = inventory.getSize();
        // Row 1 (9-17) is part of the header for 45/54 and is always glass.
        if (size >= 45) {
            for (int i = 9; i <= 17; i++) {
                inventory.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
            }
        }

        // Footer = last row: glass, back at local 7, close at local 8
        int footer = size - 9;
        for (int i = footer; i < footer + 7; i++) {
            inventory.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        // Slot 25: Back button
        if (GuiNavigationManager.hasHistory(viewer)) {
            String backB64 = getButtonHead(plugin, "back");
            inventory.setItem(footer + 7, new ItemBuilder(Material.PLAYER_HEAD)
                    .base64Head(backB64)
                    .name("&e◀ Назад")
                    .lore("", "&7Вернуться в предыдущее меню", "", "&a▶ Нажмите для возврата")
                    .build());
        } else {
            inventory.setItem(footer + 7, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        // Close button: always the last slot
        String closeB64 = getButtonHead(plugin, "close");
        inventory.setItem(size - 1, new ItemBuilder(Material.PLAYER_HEAD)
                .base64Head(closeB64)
                .name("&cЗакрыть")
                .build());

        loadContent();
    }

    /** Work-zone rows (first slot of each row) for the current inventory size. */
    private int[] workRows() {
        int size = inventory.getSize();
        if (size >= 45) {
            int[] rows = new int[size / 9 - 3];
            for (int r = 0; r < rows.length; r++) rows[r] = 18 + r * 9;
            return rows;
        }
        int[] rows = new int[size / 9 - 2];
        for (int r = 0; r < rows.length; r++) rows[r] = 9 + r * 9;
        return rows;
    }

    private int[] getGridSlots(int count) {
        if (count <= 7) {
            int base = workRows()[0];
            int[] cols = switch (count) {
                case 1 -> new int[] { 4 };
                case 2 -> new int[] { 3, 5 };
                case 3 -> new int[] { 3, 4, 5 };
                case 4 -> new int[] { 2, 3, 5, 6 };
                case 5 -> new int[] { 2, 3, 4, 5, 6 };
                case 6 -> new int[] { 1, 2, 3, 5, 6, 7 };
                default -> new int[] { 1, 2, 3, 4, 5, 6, 7 };
            };
            int[] slots = new int[cols.length];
            for (int i = 0; i < cols.length; i++) slots[i] = base + cols[i];
            return slots;
        }
        // Columns 1-7 only: columns 0 and 8 are work-zone walls and stay empty.
        int[] rows = workRows();
        int[] slots = new int[rows.length * 7];
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < 7; c++) slots[r * 7 + c] = rows[r] + 1 + c;
        }
        return slots;
    }

    private void loadContent() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Category> matchingCats = switchableCategories(plugin, entityTypeFilter);

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
                // Clear the whole work zone (content only, no glass)
                for (int row : workRows()) {
                    for (int i = row; i < row + 9; i++) {
                        inventory.setItem(i, null);
                    }
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
                // Two-option mode: both LMB and RMB toggle. Reopen, since the size depends on the mode.
                String nextMode = "clan".equalsIgnoreCase(entityTypeFilter) ? "player" : "clan";
                viewer.openInventory(new PlayerStatsGui(plugin, viewer, targetPlayer, currentCategory, currentPeriod,
                        nextMode).getInventory());
            }
            return;
        }

        if (slot == 5) {
            // Slot 5: LMB next period, RMB previous
            TimePeriod nextPeriod = cyclePeriod(currentPeriod, event.isRightClick());
            viewer.openInventory(new PlayerStatsGui(plugin, viewer, targetPlayer, currentCategory, nextPeriod,
                    entityTypeFilter).getInventory());
            return;
        }

        // Category grid clicks
        List<Category> matchingCats = switchableCategories(plugin, entityTypeFilter);

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

        if (slot == inventory.getSize() - 2) {
            if (GuiNavigationManager.hasHistory(viewer)) {
                GuiNavigationManager.goBack(plugin, viewer);
            }
            return;
        }

        if (slot == inventory.getSize() - 1) {
            GuiNavigationManager.clearHistory(viewer);
            viewer.closeInventory();
        }
    }
}
