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

import java.util.List;

public class CategorySelectGui extends BaseGui {
    private final LoveLeaderboards plugin;
    private final Player viewer;
    private final TimePeriod currentPeriod;
    private String entityTypeFilter;
    private final OfflinePlayer compareTarget;

    private static final int[] GRID_SLOTS = {
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    public CategorySelectGui(LoveLeaderboards plugin, Player viewer, TimePeriod currentPeriod) {
        this(plugin, viewer, currentPeriod, "player", null);
    }

    public CategorySelectGui(LoveLeaderboards plugin, Player viewer, TimePeriod currentPeriod, OfflinePlayer compareTarget) {
        this(plugin, viewer, currentPeriod, "player", compareTarget);
    }

    public CategorySelectGui(LoveLeaderboards plugin, Player viewer, TimePeriod currentPeriod, String entityTypeFilter, OfflinePlayer compareTarget) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.currentPeriod = currentPeriod != null ? currentPeriod : TimePeriod.ALL_TIME;
        this.entityTypeFilter = entityTypeFilter != null ? entityTypeFilter : "player";
        this.compareTarget = compareTarget;

        String title = plugin.getConfig().getString("gui.category-select.title", "&6📁 Выбор категории");
        this.inventory = Bukkit.createInventory(this, 54, TextUtil.parse(title));
        setup();
    }

    private void setup() {
        // gui-gen-4 Header (0-8)
        inventory.setItem(0, new ItemBuilder(Material.PLAYER_HEAD)
            .skullOwner(viewer.getUniqueId())
            .name("&e" + viewer.getName())
            .lore("", "&7Выбор категории лидерборда")
            .build());

        inventory.setItem(1, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(2, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        boolean isClanView = "clan".equalsIgnoreCase(entityTypeFilter);
        String typeB64 = getButtonHead(plugin, isClanView ? "type-clan" : "type-player");
        inventory.setItem(3, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(typeB64)
            .name("&eТип: &f" + (isClanView ? "👑 Кланы" : "👥 Игроки"))
            .lore("", "&7Фильтр отображаемых категорий", "", "&a▶ Нажмите для переключения")
            .build());

        if (compareTarget != null) {
            String compareB64 = getButtonHead(plugin, "comparison");
            inventory.setItem(5, new ItemBuilder(Material.PLAYER_HEAD)
                .base64Head(compareB64)
                .name("&6⚔️ Сравнение с: &e" + (compareTarget.getName() != null ? compareTarget.getName() : "Игрок"))
                .lore("", "&7Выберите категорию для сравнения")
                .build());
        } else {
            String categoryB64 = getButtonHead(plugin, "category");
            inventory.setItem(5, new ItemBuilder(Material.PLAYER_HEAD)
                .base64Head(categoryB64)
                .name("&eДоступные категории")
                .lore("", "&7Выберите категорию топа")
                .build());
        }

        inventory.setItem(4, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(6, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(7, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(8, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

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
            String backB64 = getButtonHead(plugin, "back");
            inventory.setItem(52, new ItemBuilder(Material.PLAYER_HEAD)
                .base64Head(backB64)
                .name("&e◀ Назад")
                .lore("", "&7Вернуться в предыдущее меню", "", "&a▶ Нажмите для возврата")
                .build());
        } else {
            inventory.setItem(52, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        // Slot 53: Close button
        String closeB64 = getButtonHead(plugin, "close");
        inventory.setItem(53, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(closeB64)
            .name("&cЗакрыть")
            .build());

        loadContent();
    }

    private void loadContent() {
        List<Category> matching = plugin.getCategoryManager().getAllCategories().stream()
            .filter(Category::enabled)
            .filter(c -> c.getEntityType().equalsIgnoreCase(entityTypeFilter))
            .toList();

        // Clear grid center slots (Rule 8)
        for (int slot : GRID_SLOTS) {
            inventory.setItem(slot, null);
        }

        for (int i = 0; i < matching.size() && i < GRID_SLOTS.length; i++) {
            Category cat = matching.get(i);
            String catHead = getCategoryHead(plugin, cat.name());
            ItemBuilder builder;
            if (catHead != null && !catHead.isEmpty()) {
                builder = new ItemBuilder(Material.PLAYER_HEAD).base64Head(catHead);
            } else {
                Material mat = cat.isClanCategory() ? Material.RED_BANNER : Material.PLAYER_HEAD;
                builder = new ItemBuilder(mat);
            }

            builder.name(cat.displayName())
                .lore(
                    "",
                    cat.description(),
                    "&7Тип: &f" + (cat.isClanCategory() ? "Кланы" : "Игроки"),
                    "",
                    "&a▶ Нажмите для выбора"
                );
            inventory.setItem(GRID_SLOTS[i], builder.build());
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        GuiNavigationManager.GuiState currentState = new GuiNavigationManager.GuiState(
            GuiNavigationManager.GuiState.GuiType.CATEGORY_SELECT,
            null, currentPeriod, entityTypeFilter, compareTarget, 1
        );

        if (slot == 2) {
            GuiNavigationManager.pushState(viewer, currentState);
            viewer.openInventory(new PlayerStatsGui(plugin, viewer, viewer, "kills", currentPeriod).getInventory());
            return;
        }

        if (slot == 3) {
            this.entityTypeFilter = "clan".equalsIgnoreCase(entityTypeFilter) ? "player" : "clan";
            setup();
            return;
        }

        List<Category> matching = plugin.getCategoryManager().getAllCategories().stream()
            .filter(Category::enabled)
            .filter(c -> c.getEntityType().equalsIgnoreCase(entityTypeFilter))
            .toList();

        for (int i = 0; i < GRID_SLOTS.length && i < matching.size(); i++) {
            if (GRID_SLOTS[i] == slot) {
                Category selected = matching.get(i);
                GuiNavigationManager.pushState(viewer, currentState);
                if (compareTarget != null) {
                    viewer.openInventory(new PlayerComparisonGui(plugin, viewer, compareTarget, selected.name(), currentPeriod).getInventory());
                } else {
                    viewer.openInventory(new LeaderboardMainGui(plugin, viewer, selected.name(), currentPeriod, selected.getEntityType(), 1).getInventory());
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
