package dev.lovelace.loveleaderboards.commands;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.gui.LeaderboardMainGui;
import dev.lovelace.loveleaderboards.gui.PlayerComparisonGui;
import dev.lovelace.loveleaderboards.models.Category;
import dev.lovelace.loveleaderboards.models.TimePeriod;
import dev.lovelace.loveleaderboards.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardCommand implements TabExecutor {
    private final LoveLeaderboards plugin;

    public LeaderboardCommand(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.openInventory(new LeaderboardMainGui(plugin, player).getInventory());
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("help")) {
            sendHelp(player);
            return true;
        }

        if (sub.equals("compare")) {
            if (args.length < 2) {
                player.sendMessage(TextUtil.parse("&cИспользование: /leaderboard compare <игрок>"));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (target == null) {
                target = Bukkit.getOfflinePlayer(args[1]);
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(TextUtil.parse("&cНельзя сравнивать себя с самим собой!"));
                return true;
            }

            if (target.getName() == null && !target.hasPlayedBefore()) {
                player.sendMessage(TextUtil.parse("&cИгрок '" + args[1] + "' не найден!"));
                return true;
            }

            player.openInventory(new PlayerComparisonGui(plugin, player, target).getInventory());
            return true;
        }

        // Try as category or open main GUI
        if (plugin.getCategoryManager().getCategory(sub).isPresent()) {
            player.openInventory(new LeaderboardMainGui(plugin, player, sub, TimePeriod.ALL_TIME, 1).getInventory());
            return true;
        }

        player.openInventory(new LeaderboardMainGui(plugin, player).getInventory());
        return true;
    }

    /**
     * Единый формат списка команд (шапка/тело/подвал), в том же стиле, что и
     * {@code /loveleaderboardsadmin} и справка других плагинов Love*-экосистемы.
     */
    private void sendHelp(Player player) {
        player.sendMessage(TextUtil.parse("&6==========================================="));
        player.sendMessage(TextUtil.parse("&6★ &lLoveLeaderboards &6★"));
        player.sendMessage(TextUtil.parse("&e/leaderboard &7- Открыть главное меню лидербордов"));
        player.sendMessage(TextUtil.parse("&e/leaderboard <категория> &7- Открыть топ по категории"));
        player.sendMessage(TextUtil.parse("&e/leaderboard compare <игрок> &7- Сравнить статистику с игроком"));
        player.sendMessage(TextUtil.parse("&e/leaderboard help &7- Показать это меню"));
        if (player.hasPermission("loveleaderboards.admin")) {
            player.sendMessage(TextUtil.parse("&e/loveleaderboardsadmin &7- Команды администратора"));
        }
        player.sendMessage(TextUtil.parse("&7Алиасы: &f/lb, /top, /loveleaderboards, /топ"));
        player.sendMessage(TextUtil.parse("&6==========================================="));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("compare");
            options.add("help");
            for (Category cat : plugin.getCategoryManager().getAllCategories()) {
                if (cat.enabled()) {
                    options.add(cat.name());
                }
            }
            return filter(options, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("compare")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }

        return List.of();
    }

    private List<String> filter(List<String> list, String input) {
        String lower = input.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(lower)) {
                result.add(s);
            }
        }
        return result;
    }
}
