package dev.lovelace.loveleaderboards.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("deprecation")
public class ItemBuilder {
    private static final int MAX_CACHE_SIZE = 500;
    private static final Logger LOGGER = Logger.getLogger(ItemBuilder.class.getName());

    private static final Map<UUID, com.destroystokyo.paper.profile.PlayerProfile> PROFILE_CACHE = 
        Collections.synchronizedMap(new LinkedHashMap<UUID, com.destroystokyo.paper.profile.PlayerProfile>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, com.destroystokyo.paper.profile.PlayerProfile> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        });

    private static final Map<String, com.destroystokyo.paper.profile.PlayerProfile> BASE64_CACHE = 
        Collections.synchronizedMap(new LinkedHashMap<String, com.destroystokyo.paper.profile.PlayerProfile>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, com.destroystokyo.paper.profile.PlayerProfile> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        });

    private static final Map<String, org.bukkit.profile.PlayerProfile> BUKKIT_PROFILE_CACHE = 
        Collections.synchronizedMap(new LinkedHashMap<String, org.bukkit.profile.PlayerProfile>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, org.bukkit.profile.PlayerProfile> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        });

    public static void clearCaches() {
        PROFILE_CACHE.clear();
        BASE64_CACHE.clear();
        BUKKIT_PROFILE_CACHE.clear();
    }

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material != null ? material : Material.STONE);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        if (meta != null && name != null) {
            meta.displayName(dev.lovelace.loveleaderboards.utils.TextUtil.parse(name));
        }
        return this;
    }

    public ItemBuilder lore(String... lore) {
        if (meta != null && lore != null) {
            List<net.kyori.adventure.text.Component> formatted = Arrays.stream(lore)
                .map(dev.lovelace.loveleaderboards.utils.TextUtil::parse)
                .toList();
            meta.lore(formatted);
        }
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        if (meta != null && lore != null) {
            List<net.kyori.adventure.text.Component> formatted = lore.stream()
                .map(dev.lovelace.loveleaderboards.utils.TextUtil::parse)
                .toList();
            meta.lore(formatted);
        }
        return this;
    }

    public ItemBuilder skullOwner(UUID uuid) {
        if (meta instanceof SkullMeta skullMeta && uuid != null) {
            skullMeta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(uuid));
        }
        return this;
    }

    public ItemBuilder skullOwner(String name) {
        if (meta instanceof SkullMeta skullMeta && name != null && !name.isEmpty()) {
            skullMeta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(name));
        }
        return this;
    }

    public ItemBuilder playerProfile(com.destroystokyo.paper.profile.PlayerProfile profile) {
        if (meta instanceof SkullMeta skullMeta && profile != null) {
            skullMeta.setPlayerProfile(profile);
        }
        return this;
    }

    public ItemBuilder playerProfile(UUID uuid, String name) {
        if (meta instanceof SkullMeta skullMeta && uuid != null) {
            try {
                com.destroystokyo.paper.profile.PlayerProfile profile = PROFILE_CACHE.computeIfAbsent(uuid, k -> {
                    com.destroystokyo.paper.profile.PlayerProfile p = org.bukkit.Bukkit.createProfile(uuid, name != null ? name : "Player");
                    if (!p.hasTextures() && !org.bukkit.Bukkit.isPrimaryThread()) {
                        try {
                            p.complete(false);
                        } catch (Throwable t) {
                            LOGGER.log(Level.FINE, "Failed to complete player profile for UUID " + uuid, t);
                        }
                    }
                    return p;
                });
                skullMeta.setPlayerProfile(profile);
            } catch (Throwable t) {
                LOGGER.log(Level.FINE, "Failed to set player profile for UUID " + uuid, t);
                skullMeta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(uuid));
            }
        }
        return this;
    }

    public ItemBuilder base64Head(String input) {
        if (!(meta instanceof SkullMeta skullMeta) || input == null || input.isBlank()) {
            return this;
        }

        String text = input.trim();
        if (text.startsWith("basehead-")) {
            text = text.substring("basehead-".length()).trim();
        }

        String base64String = null;
        java.net.URL skinUrl = null;

        if (text.startsWith("http://") || text.startsWith("https://")) {
            try {
                skinUrl = new java.net.URI(text).toURL();
                String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + text + "\"}}}";
                base64String = java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        } else if (text.matches("^[a-fA-F0-9]{32,64}$")) {
            String urlStr = "http://textures.minecraft.net/texture/" + text;
            try {
                skinUrl = new java.net.URI(urlStr).toURL();
                String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + urlStr + "\"}}}";
                base64String = java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        } else {
            // Assume Base64 string
            base64String = text;
            try {
                String decoded = new String(java.util.Base64.getDecoder().decode(text), java.nio.charset.StandardCharsets.UTF_8);
                int urlIndex = decoded.indexOf("\"url\":\"");
                if (urlIndex != -1) {
                    int start = urlIndex + 7;
                    int end = decoded.indexOf("\"", start);
                    if (end != -1) {
                        skinUrl = new java.net.URI(decoded.substring(start, end)).toURL();
                    }
                }
            } catch (Exception ignored) {}
        }

        if (base64String == null) {
            return this;
        }

        final String finalBase64 = base64String;
        final java.net.URL finalUrl = skinUrl;
        UUID uuid = UUID.nameUUIDFromBytes(finalBase64.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // 1. Primary: Paper PlayerProfile API (Direct Base64 Property)
        try {
            com.destroystokyo.paper.profile.PlayerProfile profile = BASE64_CACHE.computeIfAbsent(finalBase64, k -> {
                com.destroystokyo.paper.profile.PlayerProfile p = org.bukkit.Bukkit.createProfile(uuid, "TextureHead");
                p.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", finalBase64));
                return p;
            });
            skullMeta.setPlayerProfile(profile);
            return this;
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Paper PlayerProfile skin application failed", t);
        }

        // 2. Fallback: Mojang Authlib GameProfile Reflection (Direct Base64 Property)
        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object gameProfile = gameProfileClass.getConstructor(UUID.class, String.class).newInstance(uuid, "TextureHead");
            Object property = propertyClass.getConstructor(String.class, String.class).newInstance("textures", finalBase64);
            
            Object propertiesMap = gameProfileClass.getMethod("getProperties").invoke(gameProfile);
            propertiesMap.getClass().getMethod("put", Object.class, Object.class).invoke(propertiesMap, "textures", property);

            java.lang.reflect.Field profileField = skullMeta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(skullMeta, gameProfile);
            return this;
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Mojang Authlib reflection skin application failed", t);
        }

        // 3. Fallback: Bukkit Standard PlayerProfile API
        try {
            org.bukkit.profile.PlayerProfile profile = BUKKIT_PROFILE_CACHE.computeIfAbsent(finalBase64, k -> {
                org.bukkit.profile.PlayerProfile p = org.bukkit.Bukkit.createPlayerProfile(uuid, "TextureHead");
                if (finalUrl != null) {
                    p.getTextures().setSkin(finalUrl);
                }
                return p;
            });
            skullMeta.setOwnerProfile(profile);
            return this;
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Bukkit PlayerProfile skin application failed", t);
        }

        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }
}
