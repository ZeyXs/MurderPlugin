package fr.zeyx.murder.arena;

import fr.zeyx.murder.arena.state.InitArenaState;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class TemporaryArena {

    private String name;
    private String displayName;
    private Location spawnLocation;
    private List<Location> spawnSpots = new ArrayList<>();
    private List<Location> emeraldSpots = new ArrayList<>();

    public TemporaryArena() {

    }

    public TemporaryArena(Arena arena) {
        this.name = arena.getName();
        this.displayName = arena.getDisplayName();
        this.spawnLocation = arena.getSpawnLocation();
        if (arena.getSpawnSpots() != null) {
            for (Location spawnSpot : arena.getSpawnSpots()) {
                if (spawnSpot != null) {
                    this.spawnSpots.add(spawnSpot);
                }
            }
        }
        if (arena.getEmeraldSpots() != null) {
            for (Location emeraldSpot : arena.getEmeraldSpots()) {
                if (emeraldSpot != null) {
                    this.emeraldSpots.add(emeraldSpot);
                }
            }
        }

    }

    public TemporaryArena(String name, String displayName, Location spawnLocation) {
        this.name = name;
        this.displayName = name;
        this.spawnLocation = spawnLocation;
    }

    public Arena toArena() {
        return new Arena(
                name,
                displayName,
                spawnLocation,
                filterNullLocations(spawnSpots),
                filterNullLocations(emeraldSpots),
                new ArrayList<>(),
                new InitArenaState()
        );
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.name = displayName.replace(" ", "_").toUpperCase();
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(Location spawnLocation) {
        this.spawnLocation = spawnLocation;
    }

    public List<Location> getSpawnSpots() {
        return spawnSpots;
    }

    public List<Location> getEmeraldSpots() {
        return emeraldSpots;
    }

    private List<Location> filterNullLocations(List<Location> locations) {
        List<Location> filtered = new ArrayList<>();
        if (locations == null) {
            return filtered;
        }
        for (Location location : locations) {
            if (location != null) {
                filtered.add(location);
            }
        }
        return filtered;
    }

}
