package dev.lovelace.loveleaderboards.commands;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.Category;
import dev.lovelace.loveleaderboards.models.LeaderboardStand;
import dev.lovelace.loveleaderboards.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class LeaderboardAdminCommand implements TabExecutor {
    private final LoveLeaderboards plugin;

    public LeaderboardAdminCommand(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("loveleaderboards.admin")) {
            sender.sendMessage(TextUtil.parse(plugin.getConfig().getString("messages.no-permission", "&cУ вас нет прав!")));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "stand" -> handleStandSubcommand(sender, args);
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getCategoryManager().loadCategories();
                plugin.getHallOfFameManager().updateAllStands();
                sender.sendMessage("§a[LoveLeaderboards] Конфигурация и стенды Зала Славы перезагружены.");
            }
            case "add" -> handleAdd(sender, args);
            case "set" -> handleSet(sender, args);
            case "createstand" -> handleCreateStand(sender, args);
            case "removestand" -> handleRemoveStand(sender, args);
            case "reloadstands" -> {
                plugin.getHallOfFameManager().updateAllStands();
                sender.sendMessage("§a[LoveLeaderboards] Стенды Зала Славы обновлены.");
            }
            case "cache-clear" -> {
                plugin.getLeaderboardManager().invalidateAllCaches();
                sender.sendMessage("§a[LoveLeaderboards] Кэш лидербордов очищен.");
            }
            case "reset-monthly" -> {
                Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                    plugin.getDatabaseManager().resetMonthlyLeaderboards();
                    sender.sendMessage("§a[LoveLeaderboards] Месячные лидерборды сброшены.");
                });
            }
            case "give-rewards" -> {
                plugin.getRewardsManager().issueMonthlyRewards();
                sender.sendMessage("§a[LoveLeaderboards] Ежемесячные награды выданы.");
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleStandSubcommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendStandHelp(sender);
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "create" -> {
                String[] createArgs = new String[args.length - 1];
                System.arraycopy(args, 1, createArgs, 0, createArgs.length);
                handleCreateStand(sender, createArgs);
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cИспользование: /lba stand remove <id>");
                    return;
                }
                String id = args[2];
                if (plugin.getHallOfFameManager().removeStand(id)) {
                    sender.sendMessage("§a✔ Стенд '" + id + "' успешно удален.");
                } else {
                    sender.sendMessage("§cСтенд '" + id + "' не найден.");
                }
            }
            case "list" -> {
                Collection<LeaderboardStand> stands = plugin.getHallOfFameManager().getAllStands();
                if (stands.isEmpty()) {
                    sender.sendMessage("§e[Зал Славы] Стенды еще не созданы.");
                    return;
                }
                sender.sendMessage("§6===========================================");
                sender.sendMessage("§6★ Список стендов Зала Славы (" + stands.size() + ") ★");
                for (LeaderboardStand s : stands) {
                    Location loc = s.getLocation();
                    String catName = plugin.getCategoryManager().getCategory(s.getCategory())
                        .map(Category::displayName).orElse(s.getCategory());
                    sender.sendMessage("§e• " + s.getId() + " §7| Категория: §a" + catName + " §7| Место в топе: §e#" + s.getPosition()
                        + " §7| Период: §f" + s.getCurrentPeriod() + " §7(Мир: " + loc.getWorld().getName() + " X:" + loc.getBlockX() + " Y:" + loc.getBlockY() + " Z:" + loc.getBlockZ() + ")");
                }
                sender.sendMessage("§6===========================================");
            }
            case "tp" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cТолько игроки могут телепортироваться к стенду.");
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage("§cИспользование: /lba stand tp <id>");
                    return;
                }
                String id = args[2];
                LeaderboardStand s = plugin.getHallOfFameManager().getStandById(id);
                if (s == null) {
                    player.sendMessage("§cСтенд '" + id + "' не найден!");
                    return;
                }
                player.teleport(s.getLocation());
                player.sendMessage("§aТелепортированы к стенду '" + id + "'.");
            }
            case "info" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cИспользование: /lba stand info <id>");
                    return;
                }
                String id = args[2];
                LeaderboardStand s = plugin.getHallOfFameManager().getStandById(id);
                if (s == null) {
                    sender.sendMessage("§cСтенд '" + id + "' не найден.");
                    return;
                }
                Location loc = s.getLocation();
                sender.sendMessage("§6=== Данные стенда: " + id + " ===");
                sender.sendMessage("§f• Категория: §a" + s.getCategory());
                sender.sendMessage("§f• Позиция (место): §e#" + s.getPosition());
                sender.sendMessage("§f• Текущий период: §b" + s.getCurrentPeriod());
                sender.sendMessage("§f• Citizens NPC ID: §e" + (s.getNpcId() != -1 ? s.getNpcId() : "Отсутствует"));
                sender.sendMessage("§f• Локация: §7" + loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
            }
            case "reload" -> {
                plugin.getHallOfFameManager().updateAllStands();
                sender.sendMessage("§aСтенды Зала Славы обновлены.");
            }
            default -> sendStandHelp(sender);
        }
    }

    private void handleCreateStand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cТолько игроки могут создавать стенды.");
            return;
        }
        if (args.length < 3) {
            player.sendMessage("§cИспользование: /lba createstand <alltime|temporary|all> <категория> [место]");
            player.sendMessage("§7Пример: /lba createstand alltime kills 1");
            return;
        }

        String type = args[1].toLowerCase();
        if (!type.equals("alltime") && !type.equals("temporary") && !type.equals("all")) {
            player.sendMessage("§cТип должен быть alltime, temporary или all.");
            return;
        }

        String category = args[2].toLowerCase();
        int position = 1;
        if (args.length >= 4) {
            try {
                position = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {}
        }

        if (plugin.getCategoryManager().getCategory(category).isEmpty()) {
            player.sendMessage("§cКатегория '" + category + "' не найдена! Доступные категории:");
            player.sendMessage("§7" + plugin.getCategoryManager().getAllCategories().stream().map(Category::name).toList());
            return;
        }

        String id = "stand_" + type + "_" + category + "_" + position + "_" + System.currentTimeMillis() % 10000;

        boolean created = plugin.getHallOfFameManager().createStand(id, player.getLocation(), category, position, type);
        if (created) {
            String catDisplayName = plugin.getCategoryManager().getCategory(category).map(Category::displayName).orElse(category);
            player.sendMessage("§6===========================================");
            player.sendMessage("§a✔ Стенд Зала Славы '§e" + id + "§a' успешно создан!");
            player.sendMessage("§f• Категория: §a" + catDisplayName + " §7(" + category + ")");
            player.sendMessage("§f• Тип: §e" + type);
            player.sendMessage("§f• Позиция в топе: §e#" + position + " §7(например, #1 = 1-е место на пьедестале)");
            player.sendMessage("§f• Локация: §7" + player.getLocation().getWorld().getName() + " (" + player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ() + ")");
            player.sendMessage("§7(Игроки могут нажимать ПКМ по стенду/NPC для открытия главного меню лидерборда)");
            player.sendMessage("§6===========================================");
        } else {
            player.sendMessage("§cОшибка при создании стенда!");
        }
    }

    private void handleRemoveStand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /lba removestand <id>");
            return;
        }
        String id = args[1];
        if (plugin.getHallOfFameManager().removeStand(id)) {
            sender.sendMessage("§a[LoveLeaderboards] Стенд Зала Славы '" + id + "' удален!");
        } else {
            sender.sendMessage("§cСтенд с ID '" + id + "' не найден.");
        }
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§cИспользование: /lba add <player|clan> <ник/id> <категория> <количество>");
            return;
        }
        String type = args[1].toLowerCase();
        String id = args[2];
        String category = args[3].toLowerCase();
        double amount;
        try {
            amount = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНекорректное значение количества!");
            return;
        }

        if (plugin.getCategoryManager().getCategory(category).isEmpty()) {
            sender.sendMessage("§cКатегория '" + category + "' не найдена!");
            return;
        }

        if (type.equals("player")) {
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(id);
            if (target == null) target = Bukkit.getOfflinePlayer(id);
            plugin.getLeaderboardManager().updatePlayerScore(target.getUniqueId(), target.getName() == null ? id : target.getName(), category, amount);
            sender.sendMessage("§a[LoveLeaderboards] Игроку §e" + id + " §aдобавлено §e" + amount + " §aв категорию §e" + category + "§a.");
        } else if (type.equals("clan")) {
            plugin.getLeaderboardManager().updateClanScore(id, id, category, amount);
            sender.sendMessage("§a[LoveLeaderboards] Клану §e" + id + " §aдобавлено §e" + amount + " §aв категорию §e" + category + "§a.");
        } else {
            sender.sendMessage("§cТип должен быть 'player' или 'clan'!");
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§cИспользование: /lba set <player|clan> <ник/id> <категория> <значение>");
            return;
        }
        String type = args[1].toLowerCase();
        String id = args[2];
        String category = args[3].toLowerCase();
        double score;
        try {
            score = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНекорректное значение счета!");
            return;
        }

        if (plugin.getCategoryManager().getCategory(category).isEmpty()) {
            sender.sendMessage("§cКатегория '" + category + "' не найдена!");
            return;
        }

        if (type.equals("player")) {
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(id);
            if (target == null) target = Bukkit.getOfflinePlayer(id);
            plugin.getLeaderboardManager().setPlayerScore(target.getUniqueId(), target.getName() == null ? id : target.getName(), category, score);
            sender.sendMessage("§a[LoveLeaderboards] Игроку §e" + id + " §aустановлен счет §e" + score + " §aв категории §e" + category + "§a.");
        } else if (type.equals("clan")) {
            plugin.getLeaderboardManager().setClanScore(id, id, category, score);
            sender.sendMessage("§a[LoveLeaderboards] Клану §e" + id + " §aустановлен счет §e" + score + " §aв категории §e" + category + "§a.");
        } else {
            sender.sendMessage("§cТип должен быть 'player' или 'clan'!");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6===========================================");
        sender.sendMessage("§6★ §lLoveLeaderboards Admin Commands §6★");
        sender.sendMessage("§e/lba createstand <alltime|temporary|all> <категория> [место] §7- Создать NPC/стенд");
        sender.sendMessage("§e/lba stand list §7- Посмотреть список созданных стендов");
        sender.sendMessage("§e/lba stand remove <id> §7- Удалить стенд");
        sender.sendMessage("§e/lba stand tp <id> §7- Телепортироваться к стенду");
        sender.sendMessage("§e/lba stand info <id> §7- Подробная информация о стенде");
        sender.sendMessage("§e/lba add <player|clan> <ник/id> <категория> <кол-во> §7- Добавить очки");
        sender.sendMessage("§e/lba set <player|clan> <ник/id> <категория> <счет> §7- Установить очки");
        sender.sendMessage("§e/lba reset-monthly §7- Сбросить месячные топы");
        sender.sendMessage("§e/lba give-rewards §7- Выдать ежемесячные награды");
        sender.sendMessage("§e/lba cache-clear §7- Очистить кэш в памяти");
        sender.sendMessage("§e/lba reload §7- Перезагрузить конфиг и стенды");
        sender.sendMessage("§6===========================================");
    }

    private void sendStandHelp(CommandSender sender) {
        sender.sendMessage("§6===========================================");
        sender.sendMessage("§6★ Управление стендами Зала Славы (/lba stand) ★");
        sender.sendMessage("§e/lba createstand <alltime|temporary|all> <категория> [позиция] §7- Создать стенд (на вашей позиции)");
        sender.sendMessage("§e/lba stand list §7- Список всех стендов");
        sender.sendMessage("§e/lba stand info <id> §7- Информация о стенде");
        sender.sendMessage("§e/lba stand tp <id> §7- Телепорт к стенду");
        sender.sendMessage("§e/lba stand remove <id> §7- Удалить стенд");
        sender.sendMessage("§e/lba stand reload §7- Обновить визуалы стендов");
        sender.sendMessage("§6===========================================");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("loveleaderboards.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(Arrays.asList("stand", "add", "set", "createstand", "removestand", "reloadstands", "reset-monthly", "give-rewards", "cache-clear", "reload", "help"), args[0]);
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("stand")) {
            if (args.length == 2) {
                return filter(Arrays.asList("create", "remove", "list", "tp", "info", "reload"), args[1]);
            }
            String action = args[1].toLowerCase();
            if (action.equals("create")) {
                if (args.length == 3) return filter(List.of("alltime", "temporary", "all"), args[2]);
                if (args.length == 4) return filter(plugin.getCategoryManager().getAllCategories().stream().map(Category::name).toList(), args[3]);
                if (args.length == 5) return filter(Arrays.asList("1", "2", "3", "4", "5"), args[4]);
            }
            if (action.equals("remove") || action.equals("tp") || action.equals("info")) {
                if (args.length == 3) {
                    return filter(plugin.getHallOfFameManager().getAllStands().stream().map(LeaderboardStand::getId).toList(), args[2]);
                }
            }
        }

        if (sub.equals("add") || sub.equals("set")) {
            if (args.length == 2) {
                return filter(Arrays.asList("player", "clan"), args[1]);
            }
            if (args.length == 3) {
                if (args[1].equalsIgnoreCase("player")) {
                    return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
                }
                return filter(List.of("<clan_id>"), args[2]);
            }
            if (args.length == 4) {
                return filter(plugin.getCategoryManager().getAllCategories().stream().map(Category::name).toList(), args[3]);
            }
            if (args.length == 5) {
                return filter(Arrays.asList("1", "5", "10", "50", "100", "500", "1000"), args[4]);
            }
        }

        if (sub.equals("createstand")) {
            if (args.length == 2) {
                return filter(List.of("alltime", "temporary", "all"), args[1]);
            }
            if (args.length == 3) {
                return filter(plugin.getCategoryManager().getAllCategories().stream().map(Category::name).toList(), args[2]);
            }
            if (args.length == 4) {
                return filter(Arrays.asList("1", "2", "3"), args[3]);
            }
        }

        if (sub.equals("removestand")) {
            if (args.length == 2) {
                return filter(plugin.getHallOfFameManager().getAllStands().stream().map(LeaderboardStand::getId).toList(), args[1]);
            }
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
