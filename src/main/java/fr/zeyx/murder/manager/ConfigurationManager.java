package fr.zeyx.murder.manager;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.manager.config.ArenaConfig;
import fr.zeyx.murder.manager.config.BooksConfig;
import fr.zeyx.murder.manager.config.RollbackConfig;
import fr.zeyx.murder.manager.config.SecretIdentitiesConfig;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;

public class ConfigurationManager {

    private final ArenaConfig arenaConfig;
    private final RollbackConfig rollbackConfig;
    private final BooksConfig booksConfig;
    private final SecretIdentitiesConfig secretIdentitiesConfig;

    public ConfigurationManager() {
        this.arenaConfig = new ArenaConfig();
        this.rollbackConfig = new RollbackConfig();
        this.booksConfig = new BooksConfig();
        this.secretIdentitiesConfig = new SecretIdentitiesConfig();
    }

    public List<Arena> loadArenas() {
        return arenaConfig.loadArenas();
    }

    public void saveArena(Arena arena) {
        arenaConfig.saveArena(arena);
    }

    public void removeArena(Arena arena) {
        arenaConfig.removeArena(arena);
    }

    public Location getLobbyLocation() {
        return arenaConfig.getLobbyLocation();
    }

    public void setLobbyLocation(Location lobbyLocation) {
        arenaConfig.setLobbyLocation(lobbyLocation);
    }

    public void saveRollback(Player player) {
        rollbackConfig.saveRollback(player);
    }

    public void loadRollback(Player player) {
        rollbackConfig.loadRollback(player);
    }

    public ConfigurationSection getBookSection(String key) {
        return booksConfig.getBookSection(key);
    }

    public List<String> getSecretIdentityNames() {
        return secretIdentitiesConfig.getSecretIdentityNames();
    }

}
