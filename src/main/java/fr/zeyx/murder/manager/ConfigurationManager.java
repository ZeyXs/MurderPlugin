package fr.zeyx.murder.manager;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.state.InitArenaState;
import fr.zeyx.murder.util.ConfigUtil;
import fr.zeyx.murder.util.SnapshotCodec;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ConfigurationManager {

    private final YamlConfiguration arenasConfiguration;
    private final YamlConfiguration rollbackConfiguration;
    private final YamlConfiguration booksConfiguration;
    private final File arenasFile;
    private final File rollbackFile;
    private final File booksFile;
    private Location lobbyLocation;
    private final Map<UUID, PlayerSnapshot> snapshotCache = new HashMap<>();

    public ConfigurationManager() {
        this.arenasFile = new File(MurderPlugin.getInstance().getDataFolder(), "arenas.yml");
        this.rollbackFile = new File(MurderPlugin.getInstance().getDataFolder(), "inventories.yml");
        this.booksFile = new File(MurderPlugin.getInstance().getDataFolder(), "books.yml");
        this.arenasConfiguration = new YamlConfiguration();
        this.rollbackConfiguration = new YamlConfiguration();
        this.booksConfiguration = new YamlConfiguration();

        try {
            this.arenasConfiguration.load(this.arenasFile);
            this.rollbackConfiguration.load(this.rollbackFile);
        } catch (IOException | InvalidConfigurationException exception) {
            saveArenaConfig();
            saveRollbackConfig();
        }

        if (!booksFile.exists()) {
            MurderPlugin.getInstance().saveResource("books.yml", false);
        }
        try {
            this.booksConfiguration.load(this.booksFile);
        } catch (IOException | InvalidConfigurationException exception) {
            saveBooksConfig();
        }

        this.lobbyLocation = ConfigUtil.locationFrom(arenasConfiguration.getConfigurationSection("lobby"));
    }

    @SuppressWarnings("ALL")
    public List<Arena> loadArenas() {
        List<Arena> arenaList = new ArrayList<>();
        for (String arenaName : arenasConfiguration.getKeys(false)) {
            if (arenaName.equalsIgnoreCase("lobby")) {
                continue;
            }
            ConfigurationSection section = arenasConfiguration.getConfigurationSection(arenaName);
            Arena arena = new Arena(arenaName,
                    section.getString("displayName"),
                    ConfigUtil.locationFrom(section.getConfigurationSection("spawnLocation")),
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

        UUID playerId = player.getUniqueId();
        PlayerSnapshot cached = snapshotCache.remove(playerId);
        if (cached != null) {
            applySnapshot(
                    player,
                    cached.getContents(),
                    cached.getArmor(),
                    cached.getGameMode(),
                    cached.getLocation(),
                    cached.getHealth(),
                    cached.getFoodLevel(),
                    cached.getSaturation(),
                    cached.getExhaustion(),
                    cached.getExp(),
                    cached.getLevel(),
                    cached.getTotalExperience(),
                    cached.getPotionEffects()
            );
            rollbackConfiguration.set(playerId.toString(), null);
            saveRollbackConfig();
            return;
        }

        String encoded = rollbackConfiguration.getString(playerId.toString());
        if (encoded == null || encoded.isEmpty()) {
            return;
        }

        SnapshotCodec.DecodedSnapshot decoded = SnapshotCodec.decode(encoded);
        if (decoded == null) {
            return;
        }

        applySnapshot(
                player,
                decoded.getContents(),
                decoded.getArmor(),
                decoded.getGameMode(),
                decoded.getLocation(),
                decoded.getHealth(),
                decoded.getFoodLevel(),
                decoded.getSaturation(),
                decoded.getExhaustion(),
                decoded.getExp(),
                decoded.getLevel(),
                decoded.getTotalExperience(),
                decoded.getPotionEffects()
        );
        rollbackConfiguration.set(playerId.toString(), null);
        saveRollbackConfig();
    }

    public void saveArena(Arena arena) {
        if (arenasConfiguration.isConfigurationSection(arena.getName())) {
            arenasConfiguration.set(arena.getName(), null);
        }
        ConfigurationSection arenaSection = arenasConfiguration.createSection(arena.getName());
        arenaSection.set("displayName", arena.getDisplayName());
        ConfigUtil.writeLocation(arena.getSpawnLocation(), arenaSection.createSection("spawnLocation"));
        saveArenaConfig();
    }

    public void removeArena(Arena arena) {
        if (arenasConfiguration.isConfigurationSection(arena.getName())) {
            arenasConfiguration.set(arena.getName(), null);
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

    public void saveRollback(Player player) {
        UUID playerId = player.getUniqueId();
        snapshotCache.put(playerId, new PlayerSnapshot(
                player.getInventory().getContents(),
                player.getInventory().getArmorContents(),
                player.getGameMode(),
                player.getLocation(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getExp(),
                player.getLevel(),
                player.getTotalExperience(),
                player.getActivePotionEffects().toArray(new PotionEffect[0])
        ));

        String encoded = SnapshotCodec.encode(
                player.getInventory().getContents(),
                player.getInventory().getArmorContents(),
                player.getGameMode(),
                player.getLocation(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getExp(),
                player.getLevel(),
                player.getTotalExperience(),
                player.getActivePotionEffects().toArray(new PotionEffect[0])
        );
        rollbackConfiguration.set(playerId.toString(), encoded);
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

    public void saveBooksConfig() {
        try {
            booksConfiguration.save(booksFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ConfigurationSection getBookSection(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return booksConfiguration.getConfigurationSection(key);
    }

    private void applySnapshot(
            Player player,
            ItemStack[] contents,
            ItemStack[] armor,
            GameMode gameMode,
            Location location,
            double health,
            int foodLevel,
            float saturation,
            float exhaustion,
            float exp,
            int level,
            int totalExperience,
            PotionEffect[] potionEffects
    ) {
        player.getInventory().setContents(contents);
        player.getInventory().setArmorContents(armor);
        player.setGameMode(gameMode);
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            player.setHealth(Math.max(0.0, Math.min(health, maxHealth)));
        } else {
            player.setHealth(Math.max(0.0, health));
        }
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setExhaustion(exhaustion);
        player.setExp(exp);
        player.setLevel(level);
        player.setTotalExperience(totalExperience);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        if (potionEffects != null) {
            for (PotionEffect effect : potionEffects) {
                if (effect != null) {
                    player.addPotionEffect(effect);
                }
            }
        }
        if (location != null) {
            player.teleport(location);
        }
    }

}
