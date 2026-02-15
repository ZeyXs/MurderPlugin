package fr.zeyx.murder.arena.task;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.TemporaryArena;
import fr.zeyx.murder.manager.SetupWizardManager;
import fr.zeyx.murder.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class SetupWizardTask extends BukkitRunnable {

    private static final double PARTICLE_Y_OFFSET = 0.2D;
    private static final Particle.DustOptions SPAWN_DUST = new Particle.DustOptions(Color.fromRGB(190, 85, 255), 1.4F);
    private static final Particle.DustOptions EMERALD_DUST = new Particle.DustOptions(Color.fromRGB(70, 220, 90), 1.4F);

    private final SetupWizardManager wizardManager;
    private final MurderPlugin plugin;
    private final Map<UUID, Map<SpotKey, ArmorStand>> hologramsByPlayer = new HashMap<>();

    private enum SpotType {
        SPAWN,
        EMERALD
    }

    private static final class SpotKey {
        private final SpotType type;
        private final int index;

        private SpotKey(SpotType type, int index) {
            this.type = type;
            this.index = index;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof SpotKey other)) {
                return false;
            }
            return index == other.index && type == other.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, index);
        }
    }

    public SetupWizardTask(SetupWizardManager wizardManager) {
        this.wizardManager = wizardManager;
        this.plugin = MurderPlugin.getInstance();
    }

    public void clearVisualsForPlayer(UUID playerId) {
        clearPlayerHolograms(playerId);
    }

    public void clearAllVisuals() {
        clearAllHolograms();
    }

    @Override
    public void run() {
        Set<UUID> setupPlayers = Set.copyOf(wizardManager.getPlayersInWizard().keySet());
        if (setupPlayers.isEmpty()) {
            clearAllHolograms();
            cancel();
            return;
        }

        removeHologramsForPlayersNotInSetup(setupPlayers);
        for (UUID playerId : setupPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            TemporaryArena arena = wizardManager.getPlayersInWizard().get(playerId);
            if (player == null || !player.isOnline() || arena == null) {
                clearPlayerHolograms(playerId);
                continue;
            }

            player.sendActionBar(TextUtil.component("&e&lMurder Arena Setup Mode"));
            wizardManager.refreshContextualHotbarItems(player);
            spawnIndexedParticles(player, arena.getSpawnSpots(), SPAWN_DUST);
            spawnIndexedParticles(player, arena.getEmeraldSpots(), EMERALD_DUST);
            syncHolograms(player, arena);
        }
    }

    private void spawnIndexedParticles(Player player, List<Location> locations, Particle.DustOptions dust) {
        if (player == null || locations == null || locations.isEmpty()) {
            return;
        }
        World playerWorld = player.getWorld();
        if (playerWorld == null) {
            return;
        }

        for (Location location : locations) {
            if (location == null || location.getWorld() == null || !location.getWorld().equals(playerWorld)) {
                continue;
            }
            Location anchor = toVisualAnchor(location);
            player.spawnParticle(Particle.DUST, anchor, 8, 0.22D, 0.05D, 0.22D, 0.0D, dust);
        }
    }

    private void syncHolograms(Player player, TemporaryArena arena) {
        UUID playerId = player.getUniqueId();
        Map<SpotKey, ArmorStand> playerHolograms = hologramsByPlayer.computeIfAbsent(playerId, key -> new HashMap<>());
        Set<SpotKey> activeKeys = new HashSet<>();

        syncSpotHolograms(player, playerHolograms, activeKeys, arena.getSpawnSpots(), SpotType.SPAWN, "&d");
        syncSpotHolograms(player, playerHolograms, activeKeys, arena.getEmeraldSpots(), SpotType.EMERALD, "&a");

        for (SpotKey key : new ArrayList<>(playerHolograms.keySet())) {
            if (activeKeys.contains(key)) {
                continue;
            }
            removeHologram(playerHolograms.remove(key));
        }
        if (playerHolograms.isEmpty()) {
            hologramsByPlayer.remove(playerId);
        }
    }

    private void syncSpotHolograms(
            Player owner,
            Map<SpotKey, ArmorStand> playerHolograms,
            Set<SpotKey> activeKeys,
            List<Location> locations,
            SpotType type,
            String legacyColorPrefix
    ) {
        if (locations == null) {
            return;
        }
        for (int index = 0; index < locations.size(); index++) {
            Location spotLocation = locations.get(index);
            if (spotLocation == null || spotLocation.getWorld() == null) {
                continue;
            }

            SpotKey key = new SpotKey(type, index);
            activeKeys.add(key);
            String hologramName = org.bukkit.ChatColor.translateAlternateColorCodes('&', legacyColorPrefix + "#" + (index + 1));
            Location hologramLocation = toVisualAnchor(spotLocation);

            ArmorStand hologram = playerHolograms.get(key);
            if (hologram == null || !hologram.isValid()) {
                hologram = spawnHologram(hologramLocation, hologramName);
                if (hologram == null) {
                    continue;
                }
                playerHolograms.put(key, hologram);
            } else {
                if (!hologram.getWorld().equals(hologramLocation.getWorld())
                        || hologram.getLocation().distanceSquared(hologramLocation) > 0.0001D) {
                    hologram.teleport(hologramLocation);
                }
                hologram.setCustomName(hologramName);
            }
            enforceHologramVisibility(owner, hologram);
        }
    }

    private ArmorStand spawnHologram(Location location, String name) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        World world = location.getWorld();
        return world.spawn(location, ArmorStand.class, hologram -> {
            hologram.setVisible(false);
            hologram.setGravity(false);
            hologram.setMarker(true);
            hologram.setSilent(true);
            hologram.setInvulnerable(true);
            hologram.setPersistent(false);
            hologram.setCollidable(false);
            hologram.setCanPickupItems(false);
            hologram.setCustomNameVisible(true);
            hologram.setCustomName(name);
        });
    }

    private void enforceHologramVisibility(Player owner, ArmorStand hologram) {
        if (owner == null || hologram == null || !hologram.isValid()) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }
            if (viewer.getUniqueId().equals(owner.getUniqueId())) {
                viewer.showEntity(plugin, hologram);
            } else {
                viewer.hideEntity(plugin, hologram);
            }
        }
    }

    private void removeHologramsForPlayersNotInSetup(Set<UUID> activePlayers) {
        for (UUID trackedPlayer : new ArrayList<>(hologramsByPlayer.keySet())) {
            if (activePlayers.contains(trackedPlayer)) {
                continue;
            }
            clearPlayerHolograms(trackedPlayer);
        }
    }

    private void clearPlayerHolograms(UUID playerId) {
        Map<SpotKey, ArmorStand> playerHolograms = hologramsByPlayer.remove(playerId);
        if (playerHolograms == null) {
            return;
        }
        for (ArmorStand hologram : playerHolograms.values()) {
            removeHologram(hologram);
        }
    }

    private void clearAllHolograms() {
        for (UUID playerId : new ArrayList<>(hologramsByPlayer.keySet())) {
            clearPlayerHolograms(playerId);
        }
    }

    private void removeHologram(ArmorStand hologram) {
        if (hologram != null && hologram.isValid()) {
            hologram.remove();
        }
    }

    private Location toVisualAnchor(Location baseLocation) {
        if (baseLocation == null) {
            return null;
        }
        return baseLocation.clone().add(0.0D, PARTICLE_Y_OFFSET, 0.0D);
    }
}
