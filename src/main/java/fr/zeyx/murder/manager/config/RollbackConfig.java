package fr.zeyx.murder.manager.config;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.manager.PlayerSnapshot;
import fr.zeyx.murder.util.SnapshotCodec;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RollbackConfig {

    private final YamlConfiguration rollbackConfiguration;
    private final File rollbackFile;
    private final Map<UUID, PlayerSnapshot> snapshotCache = new HashMap<>();

    public RollbackConfig() {
        this.rollbackFile = new File(MurderPlugin.getInstance().getDataFolder(), "inventories.yml");
        this.rollbackConfiguration = new YamlConfiguration();

        try {
            this.rollbackConfiguration.load(this.rollbackFile);
        } catch (IOException | InvalidConfigurationException exception) {
            saveRollbackConfig();
        }
    }

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

    private void saveRollbackConfig() {
        try {
            rollbackConfiguration.save(rollbackFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
