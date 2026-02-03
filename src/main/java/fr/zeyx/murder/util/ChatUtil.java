package fr.zeyx.murder.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.Location;

public class ChatUtil {

    public static final String CHAT_PREFIX = "&c&lMURDER &7\u2022&r ";
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    public static Component color(String legacyText) {
        return component(legacyText);
    }

    public static Component component(String legacyText) {
        String input = translate(legacyText);
        return LEGACY_SERIALIZER.deserialize(input);
    }

    public static Component itemComponent(String legacyText) {
        return itemComponent(component(legacyText), false);
    }

    public static Component itemComponent(String legacyText, boolean italic) {
        return itemComponent(component(legacyText), italic);
    }

    public static Component itemComponent(Component component, boolean italic) {
        Component base = component == null ? Component.empty() : component;
        return base.decoration(TextDecoration.ITALIC, italic);
    }

    public static Component prefixed(String message) {
        String body = message == null ? "" : message;
        return component(CHAT_PREFIX + body);
    }

    public static String stripColor(String string) {
        return ChatColor.stripColor(string);
    }

    public static String displayLocation(Location location) {
        return "(" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")";
    }

    public static String legacy(String legacyText) {
        return translate(legacyText);
    }

    public static String legacy(Component component) {
        if (component == null) {
            return "";
        }
        return LEGACY_SERIALIZER.serialize(component);
    }

    private static String translate(String legacyText) {
        String input = legacyText == null ? "" : legacyText;
        return ChatColor.translateAlternateColorCodes('&', input);
    }

}
