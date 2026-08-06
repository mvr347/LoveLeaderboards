package dev.lovelace.loveleaderboards.managers;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.Category;
import dev.lovelace.loveleaderboards.models.LeaderboardEntry;
import dev.lovelace.loveleaderboards.models.LeaderboardStand;
import dev.lovelace.loveleaderboards.utils.TextUtil;
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
    private final Map<String, LeaderboardStand> stands = new ConcurrentHashMap<>();
    private org.bukkit.scheduler.BukkitTask refreshTask;

    public HallOfFameManager(LoveLeaderboards plugin) {
        this.plugin = plugin;
        this.standsFile = new File(plugin.getDataFolder(), "stands.yml");
    }

    public void loadStands() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        stands.clear();
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
                int npcId = section.getInt(key + ".npc-id", -1);
                String holoUuidStr = section.getString(key + ".hologram-uuid", null);
                UUID holoUuid = holoUuidStr != null ? UUID.fromString(holoUuidStr) : null;
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
                        LeaderboardStand stand = new LeaderboardStand(key, loc, category, position, type, period, npcId, holoUuid);
                        stands.put(key, stand);
                    }
                }
            }
        }
        plugin.getLogger().info("Loaded " + stands.size() + " Hall of Fame stands.");
        
        // Schedule periodic refresh of stands (holograms & skins)
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllStands, 100L, 600L);
    }

    public void saveStands() {
        if (standsConfig == null) {
            standsConfig = new YamlConfiguration();
        }
        standsConfig.set("stands", null); // Clear

        for (LeaderboardStand stand : stands.values()) {
            String key = "stands." + stand.getId();
            standsConfig.set(key + ".category", stand.getCategory());
            standsConfig.set(key + ".position", stand.getPosition());
            standsConfig.set(key + ".type", stand.getType());
            standsConfig.set(key + ".period", stand.getCurrentPeriod());
            standsConfig.set(key + ".npc-id", stand.getNpcId());
            standsConfig.set(key + ".hologram-uuid", stand.getHologramEntityUuid() != null ? stand.getHologramEntityUuid().toString() : null);

            Location loc = stand.getLocation();
            standsConfig.set(key + ".world", loc.getWorld().getName());
            standsConfig.set(key + ".x", loc.getX());
            standsConfig.set(key + ".y", loc.getY());
            standsConfig.set(key + ".z", loc.getZ());
            standsConfig.set(key + ".yaw", loc.getYaw());
            standsConfig.set(key + ".pitch", loc.getPitch());
        }

        try {
            standsConfig.save(standsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save stands.yml: " + e.getMessage());
        }
    }

    public boolean createStand(String id, Location location, String category, int position, String type) {
        if (stands.containsKey(id)) return false;

        int npcId = -1;
        // Attempt Citizens creation if Citizens plugin is enabled
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            try {
                net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
                net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, TextUtil.colorize("&e[# " + position + "]"));
                npc.spawn(location);
                npcId = npc.getId();
            } catch (Throwable t) {
                plugin.getLogger().warning("Citizens found but failed to spawn NPC: " + t.getMessage());
            }
        }

        LeaderboardStand stand = new LeaderboardStand(id, location, category, position, type, null, npcId, null);
        stands.put(id, stand);
        
        updateStand(stand);
        saveStands();
        return true;
    }

    public boolean removeStand(String id) {
        LeaderboardStand stand = stands.remove(id);
        if (stand == null) return false;

        // Remove Citizens NPC if present
        if (stand.getNpcId() != -1 && Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            try {
                net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(stand.getNpcId());
                if (npc != null) {
                    npc.destroy();
                }
            } catch (Throwable t) {
                plugin.getLogger().log(java.util.logging.Level.FINE, "Failed to destroy Citizens NPC for stand " + id, t);
            }
        }

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

        // 1. Update Citizens NPC skin and name if available
        if (stand.getNpcId() != -1 && Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            try {
                net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(stand.getNpcId());
                if (npc != null && npc.isSpawned()) {
                    boolean updateSkin = plugin.getConfig().getBoolean("hall-of-fame.npc.update-skin", true);
                    String fallbackSkin = plugin.getConfig().getString("hall-of-fame.npc.fallback-skin", "Steve");
                    String emptyNpcName = plugin.getConfig().getString("hall-of-fame.npc.empty-name", "&7[Свободно]");
                    String nameFormat = plugin.getConfig().getString("hall-of-fame.npc.name-format", "&e%player_name%");

                    if (!playerName.equals(emptyPlayerName) && targetEntry != null) {
                        if (updateSkin) {
                            net.citizensnpcs.trait.SkinTrait skinTrait = npc.getOrAddTrait(net.citizensnpcs.trait.SkinTrait.class);
                            skinTrait.setSkinName(playerName, true);
                        }
                        if (stand.getType().equals("alltime")) {
                            npc.setName(""); // Empty name for alltime
                        } else {
                            npc.setName(TextUtil.colorize(nameFormat.replace("%player_name%", playerName)));
                        }
                    } else {
                        if (updateSkin) {
                            net.citizensnpcs.trait.SkinTrait skinTrait = npc.getOrAddTrait(net.citizensnpcs.trait.SkinTrait.class);
                            skinTrait.setSkinName(fallbackSkin, true);
                        }
                        if (stand.getType().equals("alltime")) {
                            npc.setName("");
                        } else {
                            npc.setName(TextUtil.colorize(emptyNpcName));
                        }
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Error updating Citizens NPC skin: " + t.getMessage());
            }
        }

        // 2. Update Hologram Text
        spawnOrUpdateHologram(stand, playerName, score);
    }

    public void togglePeriod(LeaderboardStand stand) {
        stand.cycleNextPeriod();
        updateStand(stand);
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
                    "&7(Shift + ПКМ — сменить период)",
                    "&7(ПКМ — открыть меню)"
            );
        }

        List<String> formattedLines = new ArrayList<>();
        for (String line : linesTemplate) {
            if (line.contains("★ ЗАЛ СЛАВЫ ★")) {
                if (stand.getType().equals("temporary") || stand.getType().equals("all")) {
                    line = "&e&lЛучший " + periodDisplayName.toLowerCase();
                }
            }
            if (stand.getType().equals("alltime") && line.contains("Shift + ПКМ")) {
                continue; // Skip the shift tip for alltime
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
            }
        }

        if (textDisplay == null) {
            if (holoLoc.getWorld() == null || !holoLoc.getChunk().isLoaded()) {
                return;
            }
            for (Entity nearby : holoLoc.getWorld().getNearbyEntities(holoLoc, 1.5, 1.5, 1.5)) {
                if (nearby instanceof TextDisplay td && td.isValid()) {
                    textDisplay = td;
                    stand.setHologramEntityUuid(td.getUniqueId());
                    break;
                }
            }
            if (textDisplay == null) {
                TextDisplay newTd = (TextDisplay) holoLoc.getWorld().spawnEntity(holoLoc, EntityType.TEXT_DISPLAY);
                
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

    public LeaderboardStand getStandById(String id) {
        return stands.get(id);
    }

    public LeaderboardStand getStandByNpcId(int npcId) {
        for (LeaderboardStand stand : stands.values()) {
            if (stand.getNpcId() == npcId) {
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
