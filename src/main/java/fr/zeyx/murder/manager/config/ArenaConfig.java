package fr.zeyx.murder.manager.config;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.state.InitArenaState;
import fr.zeyx.murder.util.ConfigUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ArenaConfig {

    private final YamlConfiguration arenasConfiguration;
    private final File arenasFile;
    private Location lobbyLocation;

    public ArenaConfig() {
        this.arenasFile = new File(MurderPlugin.getInstance().getDataFolder(), "arenas.yml");
        this.arenasConfiguration = new YamlConfiguration();

        try {
            this.arenasConfiguration.load(this.arenasFile);
        } catch (IOException | InvalidConfigurationException exception) {
            saveArenaConfig();
        }

        this.lobbyLocation = ConfigUtil.locationFrom(arenasConfiguration.getConfigurationSection("lobby"));
    }

    public List<Arena> loadArenas() {
        List<Arena> arenaList = new ArrayList<>();
        for (String arenaName : arenasConfiguration.getKeys(false)) {
            if (arenaName.equalsIgnoreCase("lobby")) {
                continue;
            }
            ConfigurationSection section = arenasConfiguration.getConfigurationSection(arenaName);
            if (section == null) {
                continue;
            }
            Location spawnLocation = ConfigUtil.locationFrom(section.getConfigurationSection("spawnLocation"));
            List<Location> spawnSpots = ConfigUtil.locationsFrom(section.getConfigurationSection("spawnSpots"));
            List<Location> emeraldSpots = ConfigUtil.locationsFrom(section.getConfigurationSection("emeraldSpawns"));
            if (spawnSpots.isEmpty() && spawnLocation != null) {
                spawnSpots.add(spawnLocation);
            }
            Arena arena = new Arena(
                    arenaName,
                    section.getString("displayName"),
                    spawnLocation,
                    spawnSpots,
                    emeraldSpots,
                    new ArrayList<>(),
                    new InitArenaState()
            );
            arenaList.add(arena);
        }
        return arenaList;
    }

    public void saveArena(Arena arena) {
        if (arenasConfiguration.isConfigurationSection(arena.getName())) {
            arenasConfiguration.set(arena.getName(), null);
        }
        ConfigurationSection arenaSection = arenasConfiguration.createSection(arena.getName());
        arenaSection.set("displayName", arena.getDisplayName());
        if (arena.getSpawnLocation() != null) {
            ConfigUtil.writeLocation(arena.getSpawnLocation(), arenaSection.createSection("spawnLocation"));
        }
        ConfigUtil.writeLocations(arena.getSpawnSpots(), arenaSection, "spawnSpots");
        ConfigUtil.writeLocations(arena.getEmeraldSpots(), arenaSection, "emeraldSpawns");
        saveArenaConfig();
    }

    public void removeArena(Arena arena) {
        if (arena == null) {
            return;
        }
        removeArena(arena.getName());
    }

    public void removeArena(String arenaName) {
        if (arenaName == null || arenaName.isBlank()) {
            return;
        }
        if (arenasConfiguration.isConfigurationSection(arenaName)) {
            arenasConfiguration.set(arenaName, null);
        }
        saveArenaConfig();
    }

    public Location getLobbyLocation() {
        return lobbyLocation;
    }

    public void setLobbyLocation(Location lobbyLocation) {
        this.lobbyLocation = lobbyLocation;
        if (lobbyLocation == null) {
            arenasConfiguration.set("lobby", null);
        } else {
            arenasConfiguration.set("lobby", null);
            ConfigUtil.writeLocation(lobbyLocation, arenasConfiguration.createSection("lobby"));
        }
        saveArenaConfig();
    }

    private void saveArenaConfig() {
        try {
            arenasConfiguration.save(arenasFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
