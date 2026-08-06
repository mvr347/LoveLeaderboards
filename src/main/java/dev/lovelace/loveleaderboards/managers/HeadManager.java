package dev.lovelace.loveleaderboards.managers;

import dev.lovelace.loveleaderboards.LoveLeaderboards;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class HeadManager {
    private final LoveLeaderboards plugin;
    private final File headsFile;
    private FileConfiguration headsConfig;

    public HeadManager(LoveLeaderboards plugin) {
        this.plugin = plugin;
        this.headsFile = new File(plugin.getDataFolder(), "heads.yml");
    }

    public void loadHeads() {
        if (!headsFile.exists()) {
            plugin.saveResource("heads.yml", false);
        }

        headsConfig = YamlConfiguration.loadConfiguration(headsFile);

        // Load default values from JAR if missing in user's file
        InputStream defaultStream = plugin.getResource("heads.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            headsConfig.setDefaults(defaultConfig);
        }

        plugin.getLogger().info("Loaded heads.yml configuration.");
    }

    public void reloadHeads() {
        loadHeads();
    }

    public String getHead(String key) {
        if (key == null || key.isBlank()) return "";
        String k = key.toLowerCase().trim();
        String kAlt = k.contains("-") ? k.replace("-", "_") : k.replace("_", "-");

        if (headsConfig != null) {
            // 1. Check categories.<key> and categories.<kAlt>
            String catHead = headsConfig.getString("categories." + k);
            if (catHead != null && !catHead.isEmpty()) return catHead;
            String catHeadAlt = headsConfig.getString("categories." + kAlt);
            if (catHeadAlt != null && !catHeadAlt.isEmpty()) return catHeadAlt;

            // 2. Check buttons.<key> and buttons.<kAlt>
            String btnHead = headsConfig.getString("buttons." + k);
            if (btnHead != null && !btnHead.isEmpty()) return btnHead;
            String btnHeadAlt = headsConfig.getString("buttons." + kAlt);
            if (btnHeadAlt != null && !btnHeadAlt.isEmpty()) return btnHeadAlt;

            // 3. Check special.<key> and special.<kAlt>
            String specHead = headsConfig.getString("special." + k);
            if (specHead != null && !specHead.isEmpty()) return specHead;
            String specHeadAlt = headsConfig.getString("special." + kAlt);
            if (specHeadAlt != null && !specHeadAlt.isEmpty()) return specHeadAlt;

            // 4. Direct path check
            String direct = headsConfig.getString(key);
            if (direct != null && !direct.isEmpty()) return direct;
        }

        return "";
    }

    public FileConfiguration getConfig() {
        if (headsConfig == null) {
            loadHeads();
        }
        return headsConfig;
    }
}
