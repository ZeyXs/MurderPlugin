package fr.zeyx.murder.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.Location;

public class ChatUtil {

    public static final String CHAT_PREFIX = "&c&lMURDER &7\u2022&r ";
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    public static String color(String string) {
        return ChatColor.translateAlternateColorCodes('&', string);
    }

    public static Component component(String legacyText) {
        String input = legacyText == null ? "" : legacyText;
        return LEGACY_SERIALIZER.deserialize(color(input));
    }

    public static Component itemComponent(String legacyText) {
        return itemComponent(legacyText, false);
    }

    public static Component itemComponent(String legacyText, boolean italic) {
        return component(legacyText).decoration(TextDecoration.ITALIC, italic);
    }

    public static Component prefixedComponent(String message) {
        String body = message == null ? "" : message;
        return component(CHAT_PREFIX + body);
    }

    public static String prefixed(String message) {
        if (message == null || message.isEmpty()) {
            return color(CHAT_PREFIX);
        }
        return color(CHAT_PREFIX + message);
    }

    public static String stripColor(String string) {
        return ChatColor.stripColor(string);
    }

    public static String displayLocation(Location location) {
        return "(" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")";
    }

}
