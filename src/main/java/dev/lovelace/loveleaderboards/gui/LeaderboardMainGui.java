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
import java.util.UUID;

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
        String typeB64 = getButtonHead(plugin, isClanView ? "type-clan" : "type-player");

        inventory.setItem(3, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(typeB64)
            .name("&eТип: &f" + (isClanView ? "👑 Кланы" : "👥 Игроки"))
            .lore(
                "",
                "&7Текущий режим топа: &f" + (isClanView ? "Топы Кланов" : "Топы Игроков"),
                "",
                "&a▶ Нажмите для переключения"
            ).build());

        // Slot 4: Time Period Switcher
        String periodB64 = getButtonHead(plugin, "period");
        inventory.setItem(4, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(periodB64)
            .name("&eПериод: " + currentPeriod.getDisplayName())
            .lore(
                "",
                currentPeriod.getDescription(),
                "",
                "&a▶ Нажмите для смены периода"
            ).build());

        // Slot 5: Category Switcher
        String categoryB64 = getButtonHead(plugin, "category");
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
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<Category> catOpt = plugin.getCategoryManager().getCategory(currentCategory);
            Category cat = catOpt.orElse(new Category(currentCategory, currentCategory, "", true, 0, "both", "none"));
            boolean isClanView = "clan".equalsIgnoreCase(entityType);
            String scoreUnit = cat.getScoreUnit();

            // 1. Fetch player / clan rank & score for Slot 0
            String clanName = plugin.getPlayerClanName(viewer.getUniqueId());
            String clanId = plugin.getPlayerClanId(viewer.getUniqueId());
            boolean hasClan = clanName != null && !clanName.isEmpty();

            Optional<PlayerStats> slot0Stats;
            if (isClanView) {
                if (hasClan) {
                    String searchId = clanId != null ? clanId : clanName;
                    slot0Stats = plugin.getLeaderboardManager().getEntityStats("clan", searchId, currentCategory, currentPeriod.getDbKey());
                } else {
                    slot0Stats = Optional.empty();
                }
            } else {
                slot0Stats = plugin.getLeaderboardManager().getPlayerStats(viewer.getUniqueId(), currentCategory, currentPeriod.getDbKey());
            }

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

            // 3. Pre-build Slot 0 item ASYNCHRONOUSLY using config settings
            org.bukkit.inventory.ItemStack slot0Item;
            if (isClanView) {
                if (hasClan) {
                    String nameTemplate = plugin.getConfig().getString("gui.slot-0.clan.name", "&eВаш клан: &a%clan_name%");
                    List<String> loreTemplate = plugin.getConfig().getStringList("gui.slot-0.clan.lore");
                    if (loreTemplate.isEmpty()) {
                        loreTemplate = List.of(
                            "",
                            "&7Категория: &f%category_name%",
                            "&7Период: %time_period%",
                            "",
                            "&7Место вашего клана: &e#%rank%",
                            "&7%unit%: &a%score%",
                            "",
                            "&a⭐ Это ваш клан!"
                        );
                    }
                    String rankStr = slot0Stats.map(s -> s.rank() > 0 ? String.valueOf(s.rank()) : "Не в топе").orElse("Не в топе");
                    String scoreStr = slot0Stats.map(s -> String.valueOf((long)s.score())).orElse("0");

                    ItemBuilder builder = new ItemBuilder(Material.RED_BANNER)
                        .name(nameTemplate.replace("%clan_name%", clanName));
                    for (String line : loreTemplate) {
                        builder.lore(line
                            .replace("%category_name%", cat.displayName())
                            .replace("%time_period%", currentPeriod.getDisplayName())
                            .replace("%rank%", rankStr)
                            .replace("%unit%", scoreUnit)
                            .replace("%score%", scoreStr)
                            .replace("%clan_name%", clanName)
                        );
                    }
                    slot0Item = builder.build();
                } else {
                    String noClanB64 = getButtonHead(plugin, "no-clan");
                    String nameTemplate = plugin.getConfig().getString("gui.slot-0.no-clan.name", "&cУ вас ещё нет клана");
                    List<String> loreTemplate = plugin.getConfig().getStringList("gui.slot-0.no-clan.lore");
                    if (loreTemplate.isEmpty()) {
                        loreTemplate = List.of(
                            "",
                            "&7Категория: &f%category_name%",
                            "&7Период: %time_period%",
                            "",
                            "&7Статус: &cВы не состоите в клане",
                            "",
                            "&cВступите или создайте клан для участия"
                        );
                    }

                    ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD)
                        .base64Head(noClanB64)
                        .name(nameTemplate);
                    for (String line : loreTemplate) {
                        builder.lore(line
                            .replace("%category_name%", cat.displayName())
                            .replace("%time_period%", currentPeriod.getDisplayName())
                        );
                    }
                    slot0Item = builder.build();
                }
            } else {
                String nameTemplate = plugin.getConfig().getString("gui.slot-0.player.name", "&e%player_name%");
                List<String> loreTemplate = plugin.getConfig().getStringList("gui.slot-0.player.lore");
                if (loreTemplate.isEmpty()) {
                    loreTemplate = List.of(
                        "",
                        "&7Категория: &f%category_name%",
                        "&7Период: %time_period%",
                        "",
                        "&7Ваше место: &e#%rank%",
                        "&7%unit%: &a%score%",
                        "",
                        "&a⭐ Это вы!",
                        "&a▶ Нажмите, чтобы открыть статистику"
                    );
                }
                String rankStr = slot0Stats.map(s -> s.rank() > 0 ? String.valueOf(s.rank()) : "Не в топе").orElse("Не в топе");
                String scoreStr = slot0Stats.map(s -> String.valueOf((long)s.score())).orElse("0");

                ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD)
                    .skullOwner(viewer.getUniqueId())
                    .name(nameTemplate.replace("%player_name%", viewer.getName()));
                for (String line : loreTemplate) {
                    builder.lore(line
                        .replace("%category_name%", cat.displayName())
                        .replace("%time_period%", currentPeriod.getDisplayName())
                        .replace("%rank%", rankStr)
                        .replace("%unit%", scoreUnit)
                        .replace("%score%", scoreStr)
                        .replace("%player_name%", viewer.getName())
                    );
                }
                slot0Item = builder.build();
            }

            // 4. Pre-build Grid Items ASYNCHRONOUSLY
            List<org.bukkit.inventory.ItemStack> gridItems = new ArrayList<>();
            for (int i = 0; i < pageEntries.size(); i++) {
                LeaderboardEntry entry = pageEntries.get(i);
                String color = entry.rank() == 1 ? "&6&l" : (entry.rank() <= 3 ? "&e&l" : "&7");

                ItemBuilder itemBuilder;
                if (entry.entityId().equals("empty")) {
                    String emptyB64 = getButtonHead(plugin, "empty-slot");
                    String nameTemplate = plugin.getConfig().getString("gui.empty-slot.name", "&7#%rank% &8Свободное место");
                    List<String> loreTemplate = plugin.getConfig().getStringList("gui.empty-slot.lore");
                    if (loreTemplate.isEmpty()) {
                        loreTemplate = List.of("", "&7Позиция: &f#%rank%", "&7Статус: &8Пусто");
                    }

                    ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD)
                        .base64Head(emptyB64)
                        .name(nameTemplate.replace("%rank%", String.valueOf(entry.rank())));
                    for (String line : loreTemplate) {
                        builder.lore(line.replace("%rank%", String.valueOf(entry.rank())));
                    }
                    itemBuilder = builder;
                } else if (isClanView) {
                    boolean isSelfClan = hasClan && entry.entityName().equalsIgnoreCase(clanName);
                    if (isSelfClan) {
                        itemBuilder = new ItemBuilder(Material.RED_BANNER)
                            .name(color + "#" + entry.rank() + " &f" + entry.entityName() + " &a(Ваш клан)")
                            .lore(
                                "",
                                "&7Позиция клана: &f#" + entry.rank(),
                                "&7" + scoreUnit + ": &a" + (long) entry.score(),
                                "",
                                "&a⭐ Это ваш клан!",
                                "&7(Сравнить свой клан с собой нельзя)"
                            );
                    } else if (hasClan) {
                        itemBuilder = new ItemBuilder(Material.RED_BANNER)
                            .name(color + "#" + entry.rank() + " &f" + entry.entityName())
                            .lore(
                                "",
                                "&7Позиция клана: &f#" + entry.rank(),
                                "&7" + scoreUnit + ": &a" + (long) entry.score(),
                                "",
                                "&a▶ Нажмите для сравнения с вашим кланом"
                            );
                    } else {
                        // Viewer has NO clan -> NO compare option in lore!
                        itemBuilder = new ItemBuilder(Material.RED_BANNER)
                            .name(color + "#" + entry.rank() + " &f" + entry.entityName())
                            .lore(
                                "",
                                "&7Позиция клана: &f#" + entry.rank(),
                                "&7" + scoreUnit + ": &a" + (long) entry.score(),
                                "",
                                "&c(У вас ещё нет клана)"
                            );
                    }
                } else {
                    boolean isSelfPlayer = entry.entityId().equalsIgnoreCase(viewer.getUniqueId().toString())
                        || entry.entityName().equalsIgnoreCase(viewer.getName());
                    if (isSelfPlayer) {
                        itemBuilder = new ItemBuilder(Material.PLAYER_HEAD)
                            .name(color + "#" + entry.rank() + " &f" + entry.entityName() + " &a(Вы)")
                            .lore(
                                "",
                                "&7Позиция: &f#" + entry.rank(),
                                "&7" + scoreUnit + ": &a" + (long) entry.score(),
                                "",
                                "&a⭐ Это вы!",
                                "&a▶ Нажмите для просмотра статистики"
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
                    }
                    try {
                        UUID uuid = UUID.fromString(entry.entityId());
                        itemBuilder.playerProfile(uuid, entry.entityName());
                    } catch (IllegalArgumentException e) {
                        itemBuilder.skullOwner(entry.entityName());
                    }
                }
                gridItems.add(itemBuilder.build());
            }

            // 5. Pre-build Pagination Items ASYNCHRONOUSLY
            org.bukkit.inventory.ItemStack prevItem = null;
            if (finalActualPage > 1) {
                String prevB64 = getButtonHead(plugin, "prev-page");
                prevItem = new ItemBuilder(Material.PLAYER_HEAD)
                    .base64Head(prevB64)
                    .name("&e◀ Предыдущая страница")
                    .lore("", "&7Страница " + (finalActualPage - 1) + " из " + finalMaxPages)
                    .build();
            }

            org.bukkit.inventory.ItemStack nextItem = null;
            if (finalActualPage < finalMaxPages) {
                String nextB64 = getButtonHead(plugin, "next-page");
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
            if ("clan".equalsIgnoreCase(entityType)) {
                String clanName = plugin.getPlayerClanName(viewer.getUniqueId());
                if (clanName == null || clanName.isEmpty()) {
                    String noClanMsg = plugin.getConfig().getString("messages.no-clan", "&cУ вас ещё нет клана!");
                    viewer.sendMessage(TextUtil.parse(noClanMsg));
                    return;
                }
            }
            // Slot 0 click: Open Statistics (PlayerStatsGui)
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
            // Cycle category in-place
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

        // Grid clicks
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            if (GRID_SLOTS[i] == slot) {
                List<LeaderboardEntry> top50 = plugin.getLeaderboardManager().getTop(currentCategory, entityType, currentPeriod.getDbKey(), TOTAL_LIMIT);
                int startIndex = (page - 1) * ITEMS_PER_PAGE;
                int index = startIndex + i;
                if (index < top50.size()) {
                    LeaderboardEntry entry = top50.get(index);
                    if (!entry.entityId().equals("empty")) {
                        if ("clan".equalsIgnoreCase(entityType)) {
                            String viewerClanName = plugin.getPlayerClanName(viewer.getUniqueId());
                            if (viewerClanName == null || viewerClanName.isEmpty()) {
                                String noClanMsg = plugin.getConfig().getString("messages.no-clan", "&cУ вас ещё нет клана!");
                                viewer.sendMessage(TextUtil.parse(noClanMsg));
                                return;
                            }
                            if (entry.entityName().equalsIgnoreCase(viewerClanName)) {
                                String selfClanMsg = plugin.getConfig().getString("messages.comparison-self", "&cНельзя сравнивать свой клан с самим собой!");
                                viewer.sendMessage(TextUtil.parse(selfClanMsg));
                                return;
                            }
                            GuiNavigationManager.pushState(viewer, currentState);
                            // Open Clan Comparison!
                            viewer.openInventory(new PlayerComparisonGui(plugin, viewer, viewerClanName, entry.entityName(), currentCategory, currentPeriod).getInventory());
                        } else {
                            try {
                                UUID uuid = UUID.fromString(entry.entityId());
                                OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(uuid);

                                boolean isSelf = targetPlayer.getUniqueId().equals(viewer.getUniqueId());
                                GuiNavigationManager.pushState(viewer, currentState);
                                if (event.isRightClick()) {
                                    if (isSelf) {
                                        String selfMsg = plugin.getConfig().getString("messages.comparison-self", "&cНельзя сравнивать себя с самим собой!");
                                        viewer.sendMessage(TextUtil.parse(selfMsg));
                                        return;
                                    }
                                    // Compare directly
                                    viewer.openInventory(new PlayerComparisonGui(plugin, viewer, targetPlayer, currentCategory, currentPeriod).getInventory());
                                } else {
                                    // Open target player stats
                                    viewer.openInventory(new PlayerStatsGui(plugin, viewer, targetPlayer, currentCategory, currentPeriod).getInventory());
                                }
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().fine("Entity ID is not a valid UUID: " + entry.entityId());
                            } catch (Exception e) {
                                plugin.getLogger().warning("Error handling leaderboard grid click: " + e.getMessage());
                            }
                        }
                    }
                }
                return;
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
