package fr.zeyx.murder.game.service;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.state.ActiveArenaState;
import fr.zeyx.murder.manager.ArenaManager;
import fr.zeyx.murder.manager.SecretIdentityManager;
import fr.zeyx.murder.util.ChatUtil;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TabListService implements Listener {

    private final ArenaManager arenaManager;
    private final SecretIdentityManager secretIdentityManager;
    private final Set<UUID> managedViewers = new HashSet<>();
    private BukkitTask syncTask;
    private boolean disabled;

    public TabListService(ArenaManager arenaManager, SecretIdentityManager secretIdentityManager) {
        this.arenaManager = arenaManager;
        this.secretIdentityManager = secretIdentityManager;
    }

    public void start() {
        if (syncTask != null) {
            return;
        }
        syncTask = Bukkit.getScheduler().runTaskTimer(MurderPlugin.getInstance(), this::refreshNow, 1L, 10L);
    }

    public void shutdown() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
        restoreManagedViewers();
        managedViewers.clear();
        disabled = true;
    }

    public void refreshNow() {
        if (disabled) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(MurderPlugin.getInstance(), this::refreshNow);
            return;
        }
        try {
            refreshInternal();
        } catch (Throwable throwable) {
            disabled = true;
            if (syncTask != null) {
                syncTask.cancel();
                syncTask = null;
            }
            MurderPlugin.getInstance().getLogger().warning("Disabling ArenaTabListService due to NMS error: " + throwable.getMessage());
        }
    }

    private void refreshInternal() {
        Set<UUID> currentlyManagedViewers = new HashSet<>();
        for (Arena arena : arenaManager.getArenas()) {
            if (arena == null || !(arena.getArenaState() instanceof ActiveArenaState)) {
                continue;
            }
            refreshArena(arena, currentlyManagedViewers);
        }

        Set<UUID> noLongerManaged = new HashSet<>(managedViewers);
        noLongerManaged.removeAll(currentlyManagedViewers);
        for (UUID viewerId : noLongerManaged) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                restoreViewer(viewer);
            }
        }

        managedViewers.clear();
        managedViewers.addAll(currentlyManagedViewers);
    }

    private void refreshArena(Arena arena, Set<UUID> managed) {
        List<Player> activePlayers = new ArrayList<>();
        Set<UUID> activeIds = new HashSet<>();
        for (UUID playerId : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            activePlayers.add(player);
            activeIds.add(playerId);
        }
        if (activePlayers.isEmpty()) {
            return;
        }

        List<ServerPlayer> activeHandles = activePlayers.stream().map(this::toHandle).toList();
        List<UUID> outsiders = Bukkit.getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .filter(playerId -> !activeIds.contains(playerId))
                .toList();

        for (Player viewer : activePlayers) {
            ServerPlayer viewerHandle = toHandle(viewer);

            if (!outsiders.isEmpty()) {
                viewerHandle.connection.send(new ClientboundPlayerInfoRemovePacket(outsiders));
            }

            viewerHandle.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(activeHandles));

            ClientboundPlayerInfoUpdatePacket displayPacket = buildDisplayNameUpdatePacket(viewer, activePlayers);
            if (displayPacket != null) {
                viewerHandle.connection.send(displayPacket);
            }
            managed.add(viewer.getUniqueId());
        }
    }

    private ClientboundPlayerInfoUpdatePacket buildDisplayNameUpdatePacket(Player viewer, List<Player> activePlayers) {
        List<ClientboundPlayerInfoUpdatePacket.Entry> entries = new ArrayList<>(activePlayers.size());

        for (Player target : activePlayers) {
            ServerPlayer targetHandle = toHandle(target);
            ClientboundPlayerInfoUpdatePacket basePacket = new ClientboundPlayerInfoUpdatePacket(
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                    targetHandle
            );
            if (basePacket.entries().isEmpty()) {
                continue;
            }
            ClientboundPlayerInfoUpdatePacket.Entry baseEntry = basePacket.entries().get(0);
            Component viewerDisplayName = resolveDisplayNameForViewer(viewer, target);
            entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                    baseEntry.profileId(),
                    baseEntry.profile(),
                    baseEntry.listed(),
                    baseEntry.latency(),
                    baseEntry.gameMode(),
                    PaperAdventure.asVanilla(viewerDisplayName),
                    baseEntry.showHat(),
                    baseEntry.listOrder(),
                    baseEntry.chatSession()
            ));
        }

        if (entries.isEmpty()) {
            return null;
        }
        return new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                entries
        );
    }

    private Component resolveDisplayNameForViewer(Player viewer, Player target) {
        String display = secretIdentityManager.getCurrentIdentityDisplayName(target.getUniqueId());
        if (display == null || display.isBlank()) {
            display = "&f" + target.getName();
        }
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            display = display + " &f(YOU)";
        }
        return ChatUtil.component(display);
    }

    private void restoreManagedViewers() {
        for (UUID viewerId : new HashSet<>(managedViewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                restoreViewer(viewer);
            }
        }
    }

    private void restoreViewer(Player viewer) {
        List<ServerPlayer> online = Bukkit.getOnlinePlayers().stream()
                .filter(Player::isOnline)
                .filter(target -> target.equals(viewer) || viewer.canSee(target))
                .map(this::toHandle)
                .toList();
        if (online.isEmpty()) {
            return;
        }
        ServerPlayer viewerHandle = toHandle(viewer);
        viewerHandle.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(online, viewerHandle));
    }

    private ServerPlayer toHandle(Player player) {
        return ((CraftPlayer) player).getHandle();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(MurderPlugin.getInstance(), this::refreshNow, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        refreshNow();
    }
}
