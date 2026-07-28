package dev.lovelace.loveleaderboards.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class TextUtil {
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public static Component parse(String text) {
        if (text == null) return Component.empty();
        
        // If it contains MiniMessage tags, prefer MiniMessage
        if (text.contains("<") && text.contains(">")) {
            // It might also contain legacy ampersands, let's replace them first or just parse both
            // Usually, we parse legacy first, then serialize to MM? Or just replace & with section sign.
            // For simplicity, if it has < >, we use MiniMessage.
            return MINI_MESSAGE.deserialize(text);
        }
        
        return LEGACY_SERIALIZER.deserialize(text);
    }
    
    public static String colorize(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }
}
