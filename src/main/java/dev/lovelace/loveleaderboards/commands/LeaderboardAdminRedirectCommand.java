package dev.lovelace.loveleaderboards.commands;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.utils.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Команда {@code /leaderboardadmin} (и её алиасы {@code lba}, {@code lbadmin}) больше не
 * выполняет административную логику сама — вся она переехала под единую команду
 * {@link LoveLeaderboardsAdminCommand} ({@code /loveleaderboardsadmin}).
 * <p>
 * Этот класс оставлен как редирект, чтобы не «ломать» привычку тех, кто по старой памяти
 * продолжит набирать старое имя, вместо того чтобы молча удалить команду.
 */
public class LeaderboardAdminRedirectCommand implements CommandExecutor {
    private final LoveLeaderboards plugin;

    public LeaderboardAdminRedirectCommand(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("loveleaderboards.admin")) {
            sender.sendMessage(TextUtil.parse(plugin.getConfig().getString("messages.no-permission", "&cУ вас нет прав!")));
            return true;
        }

        sender.sendMessage(TextUtil.parse("&eКоманда &7/" + label + " &eпереехала. Используйте &a/loveleaderboardsadmin&e."));
        return true;
    }
}
