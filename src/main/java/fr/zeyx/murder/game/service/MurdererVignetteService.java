package fr.zeyx.murder.game.service;

import fr.zeyx.murder.MurderPlugin;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MurdererVignetteService {

    private static final double VIGNETTE_BORDER_SIZE = 4096.0D;
    private static final int VIGNETTE_WARNING_BLOCKS = 32767;
    private static final int VIGNETTE_WARNING_TIME_SECONDS = 15;
    private static final double VIGNETTE_CENTER_OFFSET = 8.0D;
    private final Set<UUID> activePlayers = new HashSet<>();
    private boolean disabled;

    public void apply(UUID playerId) {
        if (disabled || playerId == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            Location center = player.getLocation();
            sendVignetteBorder(player, center);
            activePlayers.add(playerId);
        } catch (Throwable throwable) {
            disableService(throwable);
        }
    }

    public void tick(UUID playerId) {
        if (disabled || playerId == null || !activePlayers.contains(playerId)) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            sendVignetteBorder(player, player.getLocation());
        } catch (Throwable throwable) {
            disableService(throwable);
        }
    }

    public void clear(UUID playerId) {
        if (playerId == null) {
            return;
        }
        activePlayers.remove(playerId);
        if (disabled) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            sendWorldBorderReset(player);
        } catch (Throwable throwable) {
            disableService(throwable);
        }
    }

    public void clearAll() {
        Set<UUID> managedPlayers = new HashSet<>(activePlayers);
        activePlayers.clear();
        if (disabled) {
            return;
        }
        for (UUID playerId : managedPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            try {
                sendWorldBorderReset(player);
            } catch (Throwable throwable) {
                disableService(throwable);
                break;
            }
        }
    }

    private void sendVignetteBorder(Player player, Location center) {
        ServerPlayer handle = toHandle(player);
        WorldBorder border = new WorldBorder();
        border.setCenter(center.getX() + VIGNETTE_CENTER_OFFSET, center.getZ() + VIGNETTE_CENTER_OFFSET);
        border.setSize(VIGNETTE_BORDER_SIZE);
        border.setWarningTime(VIGNETTE_WARNING_TIME_SECONDS);
        border.setWarningBlocks(VIGNETTE_WARNING_BLOCKS);
        border.setDamagePerBlock(0.0D);
        border.setSafeZone(5.0D);
        handle.connection.send(new ClientboundInitializeBorderPacket(border));
    }

    private void sendWorldBorderReset(Player player) {
        ServerPlayer handle = toHandle(player);
        WorldBorder worldBorder = ((CraftWorld) player.getWorld()).getHandle().getWorldBorder();
        handle.connection.send(new ClientboundInitializeBorderPacket(worldBorder));
    }

    private ServerPlayer toHandle(Player player) {
        return ((CraftPlayer) player).getHandle();
    }

    private void disableService(Throwable throwable) {
        disabled = true;
        activePlayers.clear();
        MurderPlugin.getInstance().getLogger().warning("Disabling MurdererVignetteService due to NMS error: " + throwable.getMessage());
    }
}
