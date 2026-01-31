package fr.zeyx.murder.util;

import org.bukkit.ChatColor;
import org.bukkit.Location;

public class ChatUtil {

    public static final String CHAT_PREFIX = "&c&lMURDER &7\u2022&r ";

    public static String color(String string) {
        return ChatColor.translateAlternateColorCodes('&', string);
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
