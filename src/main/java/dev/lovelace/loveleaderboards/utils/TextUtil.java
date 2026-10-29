package dev.lovelace.loveleaderboards.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    
    /** Standard help header: {@code ========== Title ==========}. */
    public static Component helpHeader(String title) {
        return Component.text("========== ", NamedTextColor.DARK_GRAY)
                .append(Component.text(title, NamedTextColor.AQUA))
                .append(Component.text(" ==========", NamedTextColor.DARK_GRAY));
    }

    /** Standard help line; the command is plain text, so {@code <arg>} placeholders are never parsed as tags. */
    public static Component helpLine(String command, String description) {
        return Component.text(command, NamedTextColor.AQUA)
                .append(Component.text(" - " + description, NamedTextColor.GRAY));
    }

    public static Component helpFooter() {
        return Component.text("=========================================", NamedTextColor.DARK_GRAY);
    }

    public static String colorize(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }
}
