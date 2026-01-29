package fr.zeyx.murder.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ConfigUtil {

    public static void writeLocation(Location location, ConfigurationSection section) {
        section.set("world", location.getWorld().getName());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
    }

    public static Location locationFrom(ConfigurationSection section) {
        if (section.getString("world") == null) return null;
        World world = Bukkit.getWorld(section.getString("world"));
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"), (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    @SuppressWarnings("ALL")
    public static ItemStack[] getContents(ConfigurationSection section, String path) {
        List<ItemStack> inventoryContentsList = (List<ItemStack>) section.getList(path, new ArrayList<ItemStack>());
        return inventoryContentsList.toArray(new ItemStack[inventoryContentsList.size()]);
    }

}
