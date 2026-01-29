package fr.zeyx.murder.arena;

import fr.zeyx.murder.arena.state.InitArenaState;
import org.bukkit.Location;

import java.util.ArrayList;

public class TemporaryArena {

    private String name;
    private String displayName;
    private Location spawnLocation;
    private Location spectatorLocation;

    public TemporaryArena() {

    }

    public TemporaryArena(Arena arena) {
        this.name = arena.getName();
        this.displayName = arena.getDisplayName();
        this.spawnLocation = arena.getSpawnLocation();
        this.spectatorLocation = arena.getSpectatorLocation();

    }

    public TemporaryArena(String name, String displayName, Location spawnLocation, Location spectatorLocation) {
        this.name = name;
        this.displayName = name;
        this.spawnLocation = spawnLocation;
        this.spectatorLocation = spectatorLocation;
    }

    public Arena toArena() {
        return new Arena(name, displayName, spawnLocation, spectatorLocation, new ArrayList<>(), new ArrayList<>(), new InitArenaState());
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

    public Location getSpectatorLocation() {
        return spectatorLocation;
    }

    public void setSpectatorLocation(Location spectatorLocation) {
        this.spectatorLocation = spectatorLocation;
    }

}
