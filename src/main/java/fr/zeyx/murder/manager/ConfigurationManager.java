package fr.zeyx.murder.manager;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.state.InitArenaState;
import fr.zeyx.murder.util.ConfigUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConfigurationManager {

    private final YamlConfiguration arenasConfiguration;
    private final YamlConfiguration rollbackConfiguration;
    private final File arenasFile;
    private final File rollbackFile;

    public ConfigurationManager() {
        this.arenasFile = new File(MurderPlugin.getInstance().getDataFolder(), "arenas.yml");
        this.rollbackFile = new File(MurderPlugin.getInstance().getDataFolder(), "inventories.yml");
        this.arenasConfiguration = new YamlConfiguration();
        this.rollbackConfiguration = new YamlConfiguration();

        try {
            this.arenasConfiguration.load(this.arenasFile);
            this.rollbackConfiguration.load(this.rollbackFile);
        } catch (IOException | InvalidConfigurationException exception) {
            saveArenaConfig();
            saveRollbackConfig();
        }
    }

    @SuppressWarnings("ALL")
    public List<Arena> loadArenas() {
        List<Arena> arenaList = new ArrayList<>();
        for (String arenaName : arenasConfiguration.getKeys(false)) {
            ConfigurationSection section = arenasConfiguration.getConfigurationSection(arenaName);
            Arena arena = new Arena(arenaName,
                    section.getString("displayName"),
                    ConfigUtil.locationFrom(section.getConfigurationSection("spawnLocation")),
                    ConfigUtil.locationFrom(section.getConfigurationSection("spectatorLocation")),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new InitArenaState()
            );
            arenaList.add(arena);
        }
        return arenaList;
    }

    @SuppressWarnings("ALL")
    public void loadRollback(Player player) {
        try {
            this.rollbackConfiguration.load(this.rollbackFile);
        } catch (IOException | InvalidConfigurationException exception) {
            exception.printStackTrace();
        }

        ConfigurationSection section = rollbackConfiguration.getConfigurationSection(player.getUniqueId().toString());
        ItemStack[] inventoryContents = ConfigUtil.getContents(section, "inventoryContents");
        ItemStack[] armorContents = ConfigUtil.getContents(section, "armorContents");
        String gameMode = section.getString("gameMode");
        Location location = ConfigUtil.locationFrom(section.getConfigurationSection("location"));

        player.getInventory().setContents(inventoryContents);
        player.getInventory().setArmorContents(armorContents);
        player.setGameMode(GameMode.valueOf(gameMode));
        player.teleport(location);
    }

    public void saveArena(Arena arena) {
        if (arenasConfiguration.isConfigurationSection(arena.getName())) {
            arenasConfiguration.set(arena.getName(), null);
        }
        ConfigurationSection arenaSection = arenasConfiguration.createSection(arena.getName());
        arenaSection.set("displayName", arena.getDisplayName());
        ConfigUtil.writeLocation(arena.getSpawnLocation(), arenaSection.createSection("spawnLocation"));
        ConfigUtil.writeLocation(arena.getSpectatorLocation(), arenaSection.createSection("spectatorLocation"));
        saveArenaConfig();
    }

    public void removeArena(Arena arena) {
        if (arenasConfiguration.isConfigurationSection(arena.getName())) {
            arenasConfiguration.set(arena.getName(), null);
        }
        saveArenaConfig();
    }

    public void saveRollback(Player player) {
        if (rollbackConfiguration.isConfigurationSection(player.getUniqueId().toString())) {
            rollbackConfiguration.set(player.getUniqueId().toString(), null);
        }
        ConfigurationSection inventorySection = rollbackConfiguration.createSection(player.getUniqueId().toString());
        inventorySection.set("inventoryContents", player.getInventory().getContents());
        inventorySection.set("armorContents", player.getInventory().getArmorContents());
        inventorySection.set("gameMode", player.getGameMode().toString());
        ConfigUtil.writeLocation(player.getLocation(), inventorySection.createSection("location"));
        saveRollbackConfig();
    }

    public void saveArenaConfig() {
        try {
            arenasConfiguration.save(arenasFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveRollbackConfig() {
        try {
            rollbackConfiguration.save(rollbackFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
