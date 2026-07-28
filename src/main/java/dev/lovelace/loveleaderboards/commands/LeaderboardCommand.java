package dev.lovelace.loveleaderboards.commands;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.gui.LeaderboardMainGui;
import dev.lovelace.loveleaderboards.gui.PlayerComparisonGui;
import dev.lovelace.loveleaderboards.models.Category;
import dev.lovelace.loveleaderboards.models.TimePeriod;
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
        if (sub.equals("compare")) {
            if (args.length < 2) {
                player.sendMessage("§cИспользование: /leaderboard compare <игрок>");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (target == null) {
                target = Bukkit.getOfflinePlayer(args[1]);
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

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("compare");
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
