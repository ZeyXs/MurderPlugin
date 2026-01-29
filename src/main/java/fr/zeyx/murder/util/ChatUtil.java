package fr.zeyx.murder.util;

import org.bukkit.ChatColor;
import org.bukkit.Location;

public class ChatUtil {

    public static String color(String string) {
        return ChatColor.translateAlternateColorCodes('&', string);
    }

    public static String stripColor(String string) {
        return ChatColor.stripColor(string);
    }

    public static String displayLocation(Location location) {
        return "(" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")";
    }

}
