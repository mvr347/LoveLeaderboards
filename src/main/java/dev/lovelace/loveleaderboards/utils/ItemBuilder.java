package dev.lovelace.loveleaderboards.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ItemBuilder {
    private static final Map<UUID, com.destroystokyo.paper.profile.PlayerProfile> PROFILE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, com.destroystokyo.paper.profile.PlayerProfile> BASE64_CACHE = new ConcurrentHashMap<>();

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        if (meta != null) {
            meta.displayName(dev.lovelace.loveleaderboards.utils.TextUtil.parse(name));
        }
        return this;
    }

    public ItemBuilder lore(String... lore) {
        if (meta != null) {
            List<net.kyori.adventure.text.Component> formatted = Arrays.stream(lore)
                .map(dev.lovelace.loveleaderboards.utils.TextUtil::parse)
                .toList();
            meta.lore(formatted);
        }
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        if (meta != null) {
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
        if (meta instanceof SkullMeta skullMeta && name != null) {
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
                        try { p.complete(false); } catch (Throwable ignored) {}
                    }
                    return p;
                });
                skullMeta.setPlayerProfile(profile);
            } catch (Throwable t) {
                skullMeta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(uuid));
            }
        }
        return this;
    }

    public ItemBuilder base64Head(String base64) {
        if (meta instanceof SkullMeta skullMeta && base64 != null && !base64.isEmpty()) {
            if (base64.startsWith("basehead-")) {
                base64 = base64.substring("basehead-".length());
            }
            final String finalBase64 = base64;
            try {
                com.destroystokyo.paper.profile.PlayerProfile profile = BASE64_CACHE.computeIfAbsent(finalBase64, k -> {
                    UUID uuid = UUID.nameUUIDFromBytes(finalBase64.getBytes());
                    com.destroystokyo.paper.profile.PlayerProfile p = org.bukkit.Bukkit.createProfile(uuid, "TextureHead");
                    p.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", finalBase64));
                    return p;
                });
                skullMeta.setPlayerProfile(profile);
            } catch (Throwable t1) {
                try {
                    String decoded = new String(java.util.Base64.getDecoder().decode(finalBase64));
                    int urlIndex = decoded.indexOf("\"url\":\"");
                    if (urlIndex != -1) {
                        int start = urlIndex + 7;
                        int end = decoded.indexOf("\"", start);
                        if (end != -1) {
                            String url = decoded.substring(start, end);
                            UUID uuid = UUID.nameUUIDFromBytes(finalBase64.getBytes());
                            org.bukkit.profile.PlayerProfile profile = org.bukkit.Bukkit.createPlayerProfile(uuid, "TextureHead");
                            profile.getTextures().setSkin(new java.net.URI(url).toURL());
                            skullMeta.setOwnerProfile(profile);
                        }
                    }
                } catch (Throwable ignored) {}
            }
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
