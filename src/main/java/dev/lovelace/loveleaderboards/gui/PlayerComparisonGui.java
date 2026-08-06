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
import java.util.UUID;

public class PlayerComparisonGui extends BaseGui {
    private final LoveLeaderboards plugin;
    private final Player viewer;
    private final OfflinePlayer targetPlayer;
    private final String viewerClanName;
    private final String targetClanName;
    private final String entityType; // "player" or "clan"
    private final String currentCategory;
    private final TimePeriod currentPeriod;

    // Constructor for Player vs Player comparison
    public PlayerComparisonGui(LoveLeaderboards plugin, Player viewer, OfflinePlayer target) {
        this(
            plugin,
            viewer,
            target,
            plugin.getCategoryManager().getAllCategories().stream()
                .filter(Category::enabled)
                .filter(c -> "player".equalsIgnoreCase(c.getEntityType()))
                .findFirst().map(Category::name).orElse("kills"),
            TimePeriod.ALL_TIME
        );
    }

    public PlayerComparisonGui(LoveLeaderboards plugin, Player viewer, OfflinePlayer target, String category, TimePeriod period) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetPlayer = target;
        this.viewerClanName = null;
        this.targetClanName = null;
        this.entityType = "player";
        this.currentCategory = category != null ? category : "kills";
        this.currentPeriod = period != null ? period : TimePeriod.ALL_TIME;

        String title = plugin.getConfig().getString("gui.comparison.title", "&6⚔️ Сравнение игроков");
        int size = plugin.getConfig().getInt("gui.comparison.size", 27);
        this.inventory = Bukkit.createInventory(this, size, TextUtil.parse(title));
        setup();
    }

    // Constructor for Clan vs Clan comparison
    public PlayerComparisonGui(LoveLeaderboards plugin, Player viewer, String viewerClanName, String targetClanName, String category, TimePeriod period) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetPlayer = null;
        this.viewerClanName = viewerClanName;
        this.targetClanName = targetClanName;
        this.entityType = "clan";
        this.currentCategory = category != null ? category : "clan.wealth";
        this.currentPeriod = period != null ? period : TimePeriod.ALL_TIME;

        String title = plugin.getConfig().getString("gui.comparison.clan-title", "&6⚔️ Сравнение кланов");
        int size = plugin.getConfig().getInt("gui.comparison.size", 27);
        this.inventory = Bukkit.createInventory(this, size, TextUtil.parse(title));
        setup();
    }

    private void setup() {
        boolean isClanMode = "clan".equalsIgnoreCase(entityType);

        // Slot 0: Viewer Profile or Clan Banner
        if (isClanMode) {
            inventory.setItem(0, new ItemBuilder(Material.RED_BANNER)
                .name("&eВаш клан: &a" + (viewerClanName != null ? viewerClanName : "Без клана"))
                .lore("", "&7Профиль вашего клана")
                .build());
        } else {
            inventory.setItem(0, new ItemBuilder(Material.PLAYER_HEAD)
                .skullOwner(viewer.getUniqueId())
                .name("&e" + viewer.getName())
                .lore("", "&7Ваш профиль", "&a⭐ Это вы!")
                .build());
        }

        inventory.setItem(1, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(2, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        inventory.setItem(3, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        // Slot 4: Period selector
        String periodB64 = getButtonHead(plugin, "period");
        inventory.setItem(4, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(periodB64)
            .name("&eПериод: " + currentPeriod.getDisplayName())
            .lore("", currentPeriod.getDescription(), "", "&a▶ Нажмите для смены периода")
            .build());

        // Slot 5: Category selector (Filter)
        Optional<Category> catOpt = plugin.getCategoryManager().getCategory(currentCategory);
        String catName = catOpt.map(Category::displayName).orElse(currentCategory);
        String categoryB64 = getButtonHead(plugin, "category");
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

        // Slot 8: Target Profile or Target Clan Banner
        if (isClanMode) {
            inventory.setItem(8, new ItemBuilder(Material.RED_BANNER)
                .name("&cКлан оппонента: &f" + (targetClanName != null ? targetClanName : "Unknown"))
                .lore("", "&7Профиль клана оппонента")
                .build());
        } else {
            String targetName = targetPlayer != null && targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
            inventory.setItem(8, new ItemBuilder(Material.PLAYER_HEAD)
                .skullOwner(targetPlayer != null ? targetPlayer.getUniqueId() : viewer.getUniqueId())
                .name("&c" + targetName)
                .lore("", "&7Профиль оппонента")
                .build());
        }

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

    private void loadContent() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean isClanMode = "clan".equalsIgnoreCase(entityType);
            Comparison comp;

            if (isClanMode) {
                String vClan = viewerClanName != null ? viewerClanName : "Empty";
                String tClan = targetClanName != null ? targetClanName : "Empty";
                String vId = plugin.getPlayerClanId(viewer.getUniqueId());
                if (vId == null) vId = vClan;
                String tId = tClan;

                comp = plugin.getLeaderboardManager().compareEntities("clan", vId, vClan, tId, tClan, currentCategory, currentPeriod.getDbKey());
            } else {
                String tName = targetPlayer != null && targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
                UUID tUuid = targetPlayer != null ? targetPlayer.getUniqueId() : viewer.getUniqueId();

                comp = plugin.getLeaderboardManager().compareWithPlayer(viewer.getUniqueId(), viewer.getName(), tUuid, tName, currentCategory, currentPeriod.getDbKey());
            }

            PlayerStats s1 = comp.stats1();
            PlayerStats s2 = comp.stats2();

            Optional<Category> catOpt = plugin.getCategoryManager().getCategory(currentCategory);
            String catName = catOpt.map(Category::displayName).orElse(currentCategory);
            String scoreUnit = catOpt.map(Category::getScoreUnit).orElse(isClanMode ? "Мощь" : "Очки");

            double diff = s1.score() - s2.score();
            String diffStr = diff == 0 ? "&7Показатели одинаковы!" : (diff > 0 ? "&aВы опережаете на " + (long)diff + " " + scoreUnit : "&cВы отстаете на " + (long)Math.abs(diff) + " " + scoreUnit);

            Bukkit.getScheduler().runTask(plugin, () -> {
                // Clear work area (9-17) for 27-slot menu (gui-gen-4 Rule 8)
                for (int i = 9; i <= 17; i++) {
                    inventory.setItem(i, null);
                }

                // Left side: Viewer / Viewer Clan stats (Slot 11)
                if (isClanMode) {
                    inventory.setItem(11, new ItemBuilder(Material.RED_BANNER)
                        .name("&eКлан: " + (viewerClanName != null ? viewerClanName : "Без клана"))
                        .lore(
                            "",
                            "&7Категория: &f" + catName,
                            "&7Период: " + currentPeriod.getDisplayName(),
                            "",
                            "&7Ранг вашего клана: &e#" + (s1.rank() > 0 ? s1.rank() : "Не в топе"),
                            "&7" + scoreUnit + ": &a" + (long) s1.score()
                        ).build());
                } else {
                    inventory.setItem(11, new ItemBuilder(Material.PLAYER_HEAD)
                        .skullOwner(viewer.getUniqueId())
                        .name("&e" + viewer.getName())
                        .lore(
                            "",
                            "&7Категория: &f" + catName,
                            "&7Период: " + currentPeriod.getDisplayName(),
                            "",
                            "&7Ваш ранг: &e#" + (s1.rank() > 0 ? s1.rank() : "Не в топе"),
                            "&7" + scoreUnit + ": &a" + (long) s1.score()
                        ).build());
                }

                // Center: Comparison Summary (Slot 13)
                String leftName = isClanMode ? viewerClanName : viewer.getName();
                String rightName = isClanMode ? targetClanName : (targetPlayer != null && targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown");
                inventory.setItem(13, new ItemBuilder(Material.PAPER)
                    .name("&6⚔️ Итог сравнения")
                    .lore(
                        "",
                        "&e" + leftName + " &7vs &c" + rightName,
                        "&7Ранг: &e#" + (s1.rank() > 0 ? s1.rank() : "N/A") + " &7| &c#" + (s2.rank() > 0 ? s2.rank() : "N/A"),
                        "&7" + scoreUnit + ": &e" + (long) s1.score() + " &7| &c" + (long) s2.score(),
                        "",
                        diffStr
                    ).build());

                // Right side: Target / Target Clan stats (Slot 15)
                if (isClanMode) {
                    inventory.setItem(15, new ItemBuilder(Material.RED_BANNER)
                        .name("&cКлан: " + (targetClanName != null ? targetClanName : "Unknown"))
                        .lore(
                            "",
                            "&7Категория: &f" + catName,
                            "&7Период: " + currentPeriod.getDisplayName(),
                            "",
                            "&7Ранг клана оппонента: &c#" + (s2.rank() > 0 ? s2.rank() : "Не в топе"),
                            "&7" + scoreUnit + ": &c" + (long) s2.score()
                        ).build());
                } else {
                    inventory.setItem(15, new ItemBuilder(Material.PLAYER_HEAD)
                        .skullOwner(targetPlayer != null ? targetPlayer.getUniqueId() : viewer.getUniqueId())
                        .name("&c" + rightName)
                        .lore(
                            "",
                            "&7Категория: &f" + catName,
                            "&7Период: " + currentPeriod.getDisplayName(),
                            "",
                            "&7Ранг: &c#" + (s2.rank() > 0 ? s2.rank() : "Не в топе"),
                            "&7" + scoreUnit + ": &c" + (long) s2.score()
                        ).build());
                }
            });
        });
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        boolean isClanMode = "clan".equalsIgnoreCase(entityType);

        if (slot == 4) {
            TimePeriod nextPeriod = currentPeriod.next();
            if (isClanMode) {
                viewer.openInventory(new PlayerComparisonGui(plugin, viewer, viewerClanName, targetClanName, currentCategory, nextPeriod).getInventory());
            } else {
                viewer.openInventory(new PlayerComparisonGui(plugin, viewer, targetPlayer, currentCategory, nextPeriod).getInventory());
            }
            return;
        }

        if (slot == 5) {
            // Cycle category in-place for active entityType
            boolean forward = !event.isRightClick();
            String nextCat = plugin.getCategoryManager().getNextCategory(currentCategory, entityType, forward);
            if (isClanMode) {
                viewer.openInventory(new PlayerComparisonGui(plugin, viewer, viewerClanName, targetClanName, nextCat, currentPeriod).getInventory());
            } else {
                viewer.openInventory(new PlayerComparisonGui(plugin, viewer, targetPlayer, nextCat, currentPeriod).getInventory());
            }
            return;
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
