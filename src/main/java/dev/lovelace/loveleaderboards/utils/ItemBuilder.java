package dev.lovelace.loveleaderboards.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ItemBuilder {
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
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(uuid));
        }
        return this;
    }

    public ItemBuilder skullOwner(String name) {
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(name));
        }
        return this;
    }

    public ItemBuilder playerProfile(com.destroystokyo.paper.profile.PlayerProfile profile) {
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setPlayerProfile(profile);
        }
        return this;
    }

    public ItemBuilder playerProfile(UUID uuid, String name) {
        if (meta instanceof SkullMeta skullMeta) {
            try {
                com.destroystokyo.paper.profile.PlayerProfile profile = org.bukkit.Bukkit.createProfile(uuid, name);
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
            try {
                com.destroystokyo.paper.profile.PlayerProfile profile = org.bukkit.Bukkit.createProfile(UUID.nameUUIDFromBytes(base64.getBytes()));
                profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", base64));
                skullMeta.setPlayerProfile(profile);
            } catch (Throwable t1) {
                try {
                    Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
                    Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
                    Object gameProfile = gameProfileClass.getConstructor(UUID.class, String.class).newInstance(UUID.nameUUIDFromBytes(base64.getBytes()), null);
                    Object property = propertyClass.getConstructor(String.class, String.class).newInstance("textures", base64);
                    Object propertiesMap = gameProfileClass.getMethod("getProperties").invoke(gameProfile);
                    propertiesMap.getClass().getMethod("put", Object.class, Object.class).invoke(propertiesMap, "textures", property);

                    java.lang.reflect.Field profileField = skullMeta.getClass().getDeclaredField("profile");
                    profileField.setAccessible(true);
                    profileField.set(skullMeta, gameProfile);
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

