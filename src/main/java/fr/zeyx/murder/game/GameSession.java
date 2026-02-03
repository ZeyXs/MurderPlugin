package fr.zeyx.murder.game;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class GameSession {

    private final GameManager gameManager;
    private final Arena arena;

    private final List<UUID> alivePlayers = new ArrayList<>();
    private final Map<UUID, Role> roles = new HashMap<>();
    private UUID murdererId;
    private UUID detectiveId;

    public GameSession(GameManager gameManager, Arena arena) {
        this.gameManager = gameManager;
        this.arena = arena;
    }

    public void start() {
        alivePlayers.clear();
        alivePlayers.addAll(arena.getActivePlayers());

        assignRoles();
        applySecretIdentities();

        for (UUID playerId : alivePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            Location spawn = pickSpawnLocation();
            if (spawn != null) {
                player.teleport(spawn);
            }
            player.getInventory().clear();
            gameManager.getScoreboardManager().showGameBoard(player);
            notifyRole(player, roles.get(playerId));
        }
    }

    public void endGame() {
        for (UUID playerId : alivePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(ChatUtil.prefixed("&7End of the game!"));
        }
    }

    public List<UUID> getAlivePlayers() {
        return alivePlayers;
    }

    public Role getRole(UUID playerId) {
        return roles.get(playerId);
    }

    public void removeAlive(UUID playerId) {
        alivePlayers.remove(playerId);
        roles.remove(playerId);
        if (playerId != null) {
            if (playerId.equals(murdererId)) {
                murdererId = null;
            }
            if (playerId.equals(detectiveId)) {
                detectiveId = null;
            }
        }
    }

    public UUID getMurdererId() {
        return murdererId;
    }

    public UUID getDetectiveId() {
        return detectiveId;
    }

    private Location pickSpawnLocation() {
        List<Location> spawnSpots = arena.getSpawnSpots();
        if (spawnSpots != null && !spawnSpots.isEmpty()) {
            return spawnSpots.get(ThreadLocalRandom.current().nextInt(spawnSpots.size()));
        }
        return arena.getSpawnLocation();
    }

    private void assignRoles() {
        roles.clear();
        murdererId = null;
        detectiveId = null;
        List<UUID> players = new ArrayList<>(alivePlayers);
        if (players.isEmpty()) {
            return;
        }
        Collections.shuffle(players);
        murdererId = players.get(0);
        roles.put(murdererId, Role.MURDERER);
        if (players.size() > 1) {
            detectiveId = players.get(1);
            roles.put(detectiveId, Role.DETECTIVE);
        }
        for (int i = 2; i < players.size(); i++) {
            roles.put(players.get(i), Role.BYSTANDER);
        }
    }

    private void applySecretIdentities() {
        List<Player> players = new ArrayList<>();
        for (UUID playerId : alivePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                players.add(player);
            }
        }
        gameManager.getSecretIdentityManager().applyUniqueIdentities(players);
    }

    private void notifyRole(Player player, Role role) {
        if (player == null || role == null) {
            return;
        }
        switch (role) {
            case MURDERER -> player.sendMessage(ChatUtil.prefixed("&cYou are the Murderer."));
            case DETECTIVE -> player.sendMessage(ChatUtil.prefixed("&bYou are the Detective."));
            case BYSTANDER -> player.sendMessage(ChatUtil.prefixed("&aYou are a Bystander."));
        }
    }
}
