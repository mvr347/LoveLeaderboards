package dev.lovelace.loveleaderboards.managers;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.integrations.StandNpcs;
import dev.lovelace.loveleaderboards.models.Category;
import dev.lovelace.loveleaderboards.models.LeaderboardEntry;
import dev.lovelace.loveleaderboards.models.LeaderboardStand;
import dev.lovelace.loveleaderboards.utils.NpcKeys;
import dev.lovelace.loveleaderboards.utils.TextUtil;
import net.kyori.adventure.text.Component;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HallOfFameManager {
    private final LoveLeaderboards plugin;
    private final File standsFile;
    private YamlConfiguration standsConfig;
    private final Object fileLock = new Object();
    private final StandNpcs standNpcs;
    private final Map<String, LeaderboardStand> stands = new ConcurrentHashMap<>();
    private org.bukkit.scheduler.BukkitTask refreshTask;

    public HallOfFameManager(LoveLeaderboards plugin) {
        this.plugin = plugin;
        this.standNpcs = new StandNpcs(plugin);
        this.standsFile = new File(plugin.getDataFolder(), "stands.yml");
    }

    public void loadStands() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        stands.clear();
        boolean migratedNpcKeys = false;
        if (!standsFile.exists()) {
            try {
                standsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create stands.yml: " + e.getMessage());
            }
        }
        standsConfig = YamlConfiguration.loadConfiguration(standsFile);

        ConfigurationSection section = standsConfig.getConfigurationSection("stands");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String category = section.getString(key + ".category", "kills");
                int position = section.getInt(key + ".position", 1);
                String period = section.getString(key + ".period", "alltime");
                NpcKeys.Stored npc = NpcKeys.read(section.getConfigurationSection(key));
                if (npc.migrated()) {
                    migratedNpcKeys = true;
                }
                String holoUuidStr = section.getString(key + ".hologram-uuid", null);
                UUID holoUuid = null;
                if (holoUuidStr != null) {
                    try {
                        holoUuid = UUID.fromString(holoUuidStr);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Stand '" + key + "' in stands.yml has an invalid hologram-uuid ('"
                                + holoUuidStr + "') — ignoring it; a new hologram will be spawned.");
                    }
                }
                String type = section.getString(key + ".type", "all");

                String worldName = section.getString(key + ".world");
                if (worldName != null) {
                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        double x = section.getDouble(key + ".x");
                        double y = section.getDouble(key + ".y");
                        double z = section.getDouble(key + ".z");
                        float yaw = (float) section.getDouble(key + ".yaw");
                        float pitch = (float) section.getDouble(key + ".pitch");

                        Location loc = new Location(world, x, y, z, yaw, pitch);
                        LeaderboardStand stand = new LeaderboardStand(key, loc, category, position, type, period, npc.key(), npc.provider(), holoUuid);
                        stands.put(key, stand);
                    }
                }
            }
        }
        plugin.getLogger().info("Loaded " + stands.size() + " Hall of Fame stands.");
        if (migratedNpcKeys) {
            migrateLegacyNpcIds();
        }
        
        // Schedule periodic refresh of stands (holograms & skins)
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllStands, 100L, 600L);
    }

    /**
     * One-time rewrite of the legacy {@code npc-id: <int>} to {@code npc-key}/{@code npc-provider}. The old file is
     * kept next to it as a backup so a downgrade does not lose the Citizens ids.
     */
    private void migrateLegacyNpcIds() {
        File backup = new File(plugin.getDataFolder(), "stands.yml.pre-npc-key");
        if (!backup.exists()) {
            try {
                java.nio.file.Files.copy(standsFile.toPath(), backup.toPath());
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось сделать резервную копию stands.yml: " + e.getMessage());
            }
        }
        // Rewrite only the NPC fields of the loaded file instead of re-serialising the in-memory stands: stands in
        // worlds that are not loaded yet are not in memory, and a full snapshot would drop them.
        ConfigurationSection section = standsConfig.getConfigurationSection("stands");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection stand = section.getConfigurationSection(id);
                if (stand == null || !NpcKeys.read(stand).migrated()) {
                    continue;
                }
                NpcKeys.Stored npc = NpcKeys.read(stand);
                stand.set("npc-key", npc.key());
                stand.set("npc-provider", npc.provider());
                stand.set("npc-id", null);
            }
        }
        writeStandsFile(standsConfig.saveToString());
        plugin.getLogger().info("stands.yml: числовые npc-id мигрированы в npc-key (провайдер citizens).");
    }

    /** Saves stands.yml: snapshot is serialized on the calling (main) thread, the disk write goes async. */
    public void saveStands() {
        saveStands(true);
    }

    /** Use {@code async=false} only from onDisable, where the scheduler is no longer usable. */
    public void saveStands(boolean async) {
        // Build a fresh config instead of mutating the shared one: no race with async writers.
        YamlConfiguration snapshot = new YamlConfiguration();
        for (LeaderboardStand stand : stands.values()) {
            String key = "stands." + stand.getId();
            snapshot.set(key + ".category", stand.getCategory());
            snapshot.set(key + ".position", stand.getPosition());
            snapshot.set(key + ".type", stand.getType());
            snapshot.set(key + ".period", stand.getCurrentPeriod());
            snapshot.set(key + ".npc-key", stand.getNpcKey());
            snapshot.set(key + ".npc-provider", stand.getNpcProvider());
            snapshot.set(key + ".hologram-uuid", stand.getHologramEntityUuid() != null ? stand.getHologramEntityUuid().toString() : null);

            Location loc = stand.getLocation();
            snapshot.set(key + ".world", loc.getWorld().getName());
            snapshot.set(key + ".x", loc.getX());
            snapshot.set(key + ".y", loc.getY());
            snapshot.set(key + ".z", loc.getZ());
            snapshot.set(key + ".yaw", loc.getYaw());
            snapshot.set(key + ".pitch", loc.getPitch());
        }
        final String data = snapshot.saveToString();
        if (async && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeStandsFile(data));
        } else {
            writeStandsFile(data);
        }
    }

    private void writeStandsFile(String data) {
        synchronized (fileLock) {
            try {
                java.nio.file.Files.writeString(standsFile.toPath(), data, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save stands.yml: " + e.getMessage());
            }
        }
    }

    public boolean createStand(String id, Location location, String category, int position, String type) {
        if (stands.containsKey(id)) return false;

        LeaderboardStand stand = new LeaderboardStand(id, location, category, position, type, null, null, null, null);
        // NPC through the configured NpcProvider (Citizens or LoveEssential); the stand works without one.
        String provider = standNpcs.configuredProvider();
        boolean updateSkin = plugin.getConfig().getBoolean("hall-of-fame.npc.update-skin", true);
        String fallbackSkin = plugin.getConfig().getString("hall-of-fame.npc.fallback-skin", "Steve");
        String npcKey = standNpcs.create(stand, TextUtil.parse("&e[# " + position + "]"), updateSkin ? fallbackSkin : null);
        if (npcKey != null) {
            stand.setNpc(npcKey, provider);
        }
        stands.put(id, stand);

        updateStand(stand);
        saveStands();
        return true;
    }

    public boolean removeStand(String id) {
        LeaderboardStand stand = stands.remove(id);
        if (stand == null) return false;

        standNpcs.remove(stand);

        // Remove Hologram text display if present
        if (stand.getHologramEntityUuid() != null) {
            Entity entity = Bukkit.getEntity(stand.getHologramEntityUuid());
            if (entity != null) {
                entity.remove();
            }
        }

        saveStands();
        return true;
    }

    public void updateAllStands() {
        for (LeaderboardStand stand : stands.values()) {
            updateStand(stand);
        }
    }

    public void updateStand(LeaderboardStand stand) {
        Optional<Category> catOpt = plugin.getCategoryManager().getCategory(stand.getCategory());
        String entityType = catOpt.map(Category::getEntityType).orElse("player");
        String periodDbKey = dev.lovelace.loveleaderboards.models.TimePeriod.fromString(stand.getCurrentPeriod()).getDbKey();

        if (Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                List<LeaderboardEntry> top = plugin.getDatabaseManager().getTopByCategory(
                    stand.getCategory(), entityType, periodDbKey, stand.getPosition()
                );
                LeaderboardEntry targetEntry = (!top.isEmpty() && top.size() >= stand.getPosition()) ? top.get(stand.getPosition() - 1) : null;
                Bukkit.getScheduler().runTask(plugin, () -> applyStandUpdate(stand, targetEntry));
            });
        } else {
            List<LeaderboardEntry> top = plugin.getDatabaseManager().getTopByCategory(
                stand.getCategory(), entityType, periodDbKey, stand.getPosition()
            );
            LeaderboardEntry targetEntry = (!top.isEmpty() && top.size() >= stand.getPosition()) ? top.get(stand.getPosition() - 1) : null;
            Bukkit.getScheduler().runTask(plugin, () -> applyStandUpdate(stand, targetEntry));
        }
    }

    private void applyStandUpdate(LeaderboardStand stand, LeaderboardEntry targetEntry) {
        String emptyPlayerName = plugin.getConfig().getString("hall-of-fame.hologram.empty-player-name", "Свободно");
        String playerName = targetEntry != null && !targetEntry.entityId().equals("empty") ? targetEntry.entityName() : emptyPlayerName;
        double score = targetEntry != null ? targetEntry.score() : 0;

        // 1. Update the stand NPC's skin and name through its NpcProvider
        if (stand.hasNpc()) {
            boolean updateSkin = plugin.getConfig().getBoolean("hall-of-fame.npc.update-skin", true);
            String fallbackSkin = plugin.getConfig().getString("hall-of-fame.npc.fallback-skin", "Steve");
            String emptyNpcName = plugin.getConfig().getString("hall-of-fame.npc.empty-name", "&7[Свободно]");
            String nameFormat = plugin.getConfig().getString("hall-of-fame.npc.name-format", "&e%player_name%");
            boolean alltime = stand.getType().equals("alltime");

            if (!playerName.equals(emptyPlayerName) && targetEntry != null) {
                // Empty name for alltime stands
                Component name = alltime ? Component.empty() : TextUtil.parse(nameFormat.replace("%player_name%", playerName));
                standNpcs.apply(stand, name, updateSkin ? playerName : null);
            } else {
                Component name = alltime ? Component.empty() : TextUtil.parse(emptyNpcName);
                standNpcs.apply(stand, name, updateSkin ? fallbackSkin : null);
            }
        }

        // 2. Update Hologram Text
        spawnOrUpdateHologram(stand, playerName, score);
    }

    public void togglePeriod(LeaderboardStand stand) {
        stand.cycleNextPeriod();
        updateStand(stand);
        // Players can trigger this on every shift-right-click; saveStands() does synchronous
        // YAML disk I/O, so keep it off the main thread on this hot, player-driven path instead
        // of blocking the event handler on every click.
        saveStands();
    }

    private void spawnOrUpdateHologram(LeaderboardStand stand, String playerName, double score) {
        double heightOffset = plugin.getConfig().getDouble("hall-of-fame.hologram.height-offset", 2.3);
        Location holoLoc = stand.getLocation().clone().add(0, heightOffset, 0);

        String categoryDisplayName = plugin.getCategoryManager().getCategory(stand.getCategory())
                .map(Category::displayName).orElse(stand.getCategory());

        String periodKey = stand.getCurrentPeriod().toLowerCase();
        String periodDisplayName = plugin.getConfig().getString("hall-of-fame.periods." + periodKey,
                switch (periodKey) {
                    case "monthly" -> "За месяц";
                    case "weekly" -> "За неделю";
                    case "yearly" -> "За год";
                    default -> "За всё время";
                });

        List<String> linesTemplate = plugin.getConfig().getStringList("hall-of-fame.hologram.lines");
        if (linesTemplate.isEmpty()) {
            linesTemplate = plugin.getConfig().getStringList("hall-of-fame.hologram-lines");
        }
        if (linesTemplate.isEmpty()) {
            linesTemplate = List.of(
                    "&e&l★ ЗАЛ СЛАВЫ ★",
                    "&fКатегория: &a%category_name%",
                    "&fПериод: &6%time_period%",
                    "&fЛидер #%position%: &b%player_name%",
                    "&fОчки: &e%score%",
                    "&eShift &8+ &6ПКМ &8— &7сменить период",
                    "&6ПКМ &8— &7открыть меню"
            );
        }

        List<String> formattedLines = new ArrayList<>();
        for (String line : linesTemplate) {
            if (line.contains("★ ЗАЛ СЛАВЫ ★")) {
                if (stand.getType().equals("temporary") || stand.getType().equals("all")) {
                    line = "&e&lЛучший " + periodDisplayName.toLowerCase();
                }
            }
            // The Shift click hint is the only template line that mentions Shift; skip it for alltime.
            if (stand.getType().equals("alltime") && line.contains("Shift")) {
                continue;
            }
            String formatted = line
                    .replace("%category_name%", categoryDisplayName)
                    .replace("%time_period%", periodDisplayName)
                    .replace("%position%", String.valueOf(stand.getPosition()))
                    .replace("%player_name%", playerName)
                    .replace("%clan_name%", playerName)
                    .replace("%clan%", playerName)
                    .replace("%score%", String.valueOf((long) score));

            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                formatted = PlaceholderAPI.setPlaceholders(null, formatted);
            }
            formattedLines.add(TextUtil.colorize(formatted));
        }

        String fullText = String.join("\n", formattedLines);

        // Native TextDisplay Entity management
        TextDisplay textDisplay = null;
        if (stand.getHologramEntityUuid() != null) {
            Entity entity = Bukkit.getEntity(stand.getHologramEntityUuid());
            if (entity instanceof TextDisplay td && td.isValid()) {
                textDisplay = td;
                td.setPersistent(false);
            }
        }

        if (textDisplay == null) {
            if (holoLoc.getWorld() == null
                    || !holoLoc.getWorld().isChunkLoaded(holoLoc.getBlockX() >> 4, holoLoc.getBlockZ() >> 4)
                    || !holoLoc.getChunk().isEntitiesLoaded()) {
                // Entities not loaded yet: the existing hologram may be there, spawning now would duplicate it.
                return;
            }
            for (Entity nearby : holoLoc.getWorld().getNearbyEntities(holoLoc, 1.5, 1.5, 1.5)) {
                if (nearby instanceof TextDisplay td && td.isValid()) {
                    textDisplay = td;
                    td.setPersistent(false);
                    stand.setHologramEntityUuid(td.getUniqueId());
                    break;
                }
            }
            if (textDisplay == null) {
                // Non-persistent: never saved to the chunk, so crashes/restarts cannot leave orphan duplicates.
                TextDisplay newTd = holoLoc.getWorld().spawn(holoLoc, TextDisplay.class, e -> e.setPersistent(false));
                
                String billboardStr = plugin.getConfig().getString("hall-of-fame.hologram.billboard", "CENTER");
                try {
                    newTd.setBillboard(Display.Billboard.valueOf(billboardStr.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    newTd.setBillboard(Display.Billboard.CENTER);
                }
                
                newTd.setSeeThrough(plugin.getConfig().getBoolean("hall-of-fame.hologram.see-through", false));
                newTd.setShadowed(plugin.getConfig().getBoolean("hall-of-fame.hologram.shadowed", true));
                stand.setHologramEntityUuid(newTd.getUniqueId());
                textDisplay = newTd;
            }
        }

        textDisplay.teleport(holoLoc);
        textDisplay.setText(fullText);
    }

    public StandNpcs getStandNpcs() {
        return standNpcs;
    }

    public LeaderboardStand getStandById(String id) {
        return stands.get(id);
    }

    public LeaderboardStand getStandByNpc(String providerId, String npcKey) {
        for (LeaderboardStand stand : stands.values()) {
            if (stand.hasNpc() && stand.getNpcKey().equals(npcKey) && stand.getNpcProvider().equals(providerId)) {
                return stand;
            }
        }
        return null;
    }

    public LeaderboardStand getStandByHologramUuid(UUID uuid) {
        for (LeaderboardStand stand : stands.values()) {
            if (uuid.equals(stand.getHologramEntityUuid())) {
                return stand;
            }
        }
        return null;
    }

    public Collection<LeaderboardStand> getAllStands() {
        return stands.values();
    }

    public void removeAllHolograms() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        for (LeaderboardStand stand : stands.values()) {
            if (stand.getHologramEntityUuid() != null) {
                Entity entity = Bukkit.getEntity(stand.getHologramEntityUuid());
                if (entity != null) {
                    entity.remove();
                }
            }
        }
    }
}
