package dev.lovelace.loveleaderboards.gui;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.Category;
import dev.lovelace.loveleaderboards.models.PlayerStats;
import dev.lovelace.loveleaderboards.utils.ItemBuilder;
import dev.lovelace.loveleaderboards.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Optional;

public class PlayerStatsGui extends BaseGui {
    private final LoveLeaderboards plugin;
    private final Player viewer;

    public PlayerStatsGui(LoveLeaderboards plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        String title = plugin.getConfig().getString("gui.player-stats.title", "&6📊 Моя Статистика");
        this.inventory = Bukkit.createInventory(this, 27, TextUtil.parse(title));
        setup();
    }

    private void setup() {
        // gui-gen-4 Header (0-8)
        inventory.setItem(0, new ItemBuilder(Material.PLAYER_HEAD)
            .name("&e" + viewer.getName())
            .skullOwner(viewer.getUniqueId())
            .build());
        inventory.setItem(1, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        String statsB64 = plugin.getConfig().getString("gui.buttons.my-stats", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjc2YjNiY2RmZDIxMmZhNTEzNDVlYWUwNDc1YjFjYzlkMjg0YWNlNTI0MjJmYjI2Yzg4NDFjYmE5NGEzOTJjNCJ9fX0=");
        inventory.setItem(4, new ItemBuilder(Material.PLAYER_HEAD)
            .base64Head(statsB64)
            .name("&eВаши показатели по категориям")
            .lore("&7Персональная статистика")
            .build());

        inventory.setItem(8, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        // Footer (18-26)
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
            int[] slots = {10, 11, 12, 13, 14, 15, 16};
            int index = 0;
            for (Category cat : plugin.getCategoryManager().getAllCategories()) {
                if (index >= slots.length) break;

                Optional<PlayerStats> statsOpt = plugin.getLeaderboardManager().getPlayerStats(viewer.getUniqueId(), cat.name());
                PlayerStats stats = statsOpt.orElse(new PlayerStats(viewer.getUniqueId(), viewer.getName(), 0, 0));

                String defaultCatB64 = switch (cat.name().toLowerCase()) {
                    case "kills" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdhZWU5YTc1YmYwZGY3ODk3MTgzMDE1Y2NhMGIyZDdiNzliYjNjMzRlYTU0MjRjNjc5NGJiNGZhOTVjMTZiZiJ9fX0=";
                    case "bounty-completed" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ4MWRmZTJiMmY5OWUzZGVjZTRjMzQ3MjY0MzM1ZjUzMTgzZjEzYjE4YTkxN2RkYjcyMzEzZTlkMDc0NjNmZCJ9fX0=";
                    case "clan-power" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGZjZWUzYTg4YmI1NGMwZjZlZTY2YjQ0YWM3NGZmOTdjZDkyYTA4ZjE0Y2NjMTdhMjYyMzcxZjBhYTg5MjEifX19";
                    default -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ4MWRmZTJiMmY5OWUzZGVjZTRjMzQ3MjY0MzM1ZjUzMTgzZjEzYjE4YTkxN2RkYjcyMzEzZTlkMDc0NjNmZCJ9fX0=";
                };
                String catHead = plugin.getConfig().getString("gui.buttons.categories." + cat.name(), defaultCatB64);

                ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD)
                    .base64Head(catHead)
                    .name(cat.displayName())
                    .lore(
                        "&7Категория: &f" + cat.displayName(),
                        "&7Тип: &f" + (cat.isClanCategory() ? "Кланы" : "Игроки"),
                        "",
                        cat.isClanCategory() ? "&7Место вашего клана: &e#" + (stats.rank() > 0 ? stats.rank() : "Нет")
                                             : "&7Ваш ранг: &e#" + (stats.rank() > 0 ? stats.rank() : "Нет"),
                        "&7" + cat.getScoreUnit() + ": &a" + (long) stats.score()
                    );

                int targetSlot = slots[index];
                Bukkit.getScheduler().runTask(plugin, () -> inventory.setItem(targetSlot, builder.build()));
                index++;
            }
        });
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == 25) {
            viewer.openInventory(new LeaderboardMainGui(plugin, viewer).getInventory());
        } else if (slot == 26) {
            viewer.closeInventory();
        }
    }
}
