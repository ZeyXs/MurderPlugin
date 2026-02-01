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
        if (section == null) return null;
        if (section.getString("world") == null) return null;
        World world = Bukkit.getWorld(section.getString("world"));
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"), (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    public static List<Location> locationsFrom(ConfigurationSection section) {
        List<Location> locations = new ArrayList<>();
        if (section == null) {
            return locations;
        }
        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException ignored) {
                return a.compareToIgnoreCase(b);
            }
        });
        for (String key : keys) {
            Location location = locationFrom(section.getConfigurationSection(key));
            if (location != null) {
                locations.add(location);
            }
        }
        return locations;
    }

    public static void writeLocations(List<Location> locations, ConfigurationSection section, String path) {
        if (section == null || path == null) {
            return;
        }
        section.set(path, null);
        if (locations == null || locations.isEmpty()) {
            return;
        }
        ConfigurationSection locationsSection = section.createSection(path);
        for (int i = 0; i < locations.size(); i++) {
            Location location = locations.get(i);
            if (location == null) {
                continue;
            }
            writeLocation(location, locationsSection.createSection(String.valueOf(i)));
        }
    }

    @SuppressWarnings("ALL")
    public static ItemStack[] getContents(ConfigurationSection section, String path) {
        List<ItemStack> inventoryContentsList = (List<ItemStack>) section.getList(path, new ArrayList<ItemStack>());
        return inventoryContentsList.toArray(new ItemStack[inventoryContentsList.size()]);
    }

}
