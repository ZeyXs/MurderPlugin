package fr.zeyx.murder.game;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.game.session.SessionEndGameMessenger;
import fr.zeyx.murder.game.session.SessionIdentityService;
import fr.zeyx.murder.game.session.SessionLoadoutService;
import fr.zeyx.murder.game.session.SessionNametagService;
import fr.zeyx.murder.game.session.SessionQuickChatService;
import fr.zeyx.murder.game.session.SessionSpectatorService;
import fr.zeyx.murder.manager.GameManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class GameSession {

    private final GameManager gameManager;
    private final Arena arena;
    private final List<UUID> alivePlayers = new ArrayList<>();
    private final List<UUID> roundParticipants = new ArrayList<>();
    private final Map<UUID, Role> roles = new HashMap<>();
    private final Map<UUID, String> realPlayerNames = new HashMap<>();
    private final Map<UUID, String> identityDisplayNames = new HashMap<>();

    private final SessionQuickChatService quickChatService;
    private final SessionIdentityService identityService;
    private final SessionLoadoutService loadoutService;
    private final SessionSpectatorService spectatorService;
    private final SessionEndGameMessenger endGameMessenger;

    private UUID murdererId;
    private UUID detectiveId;
    private UUID murdererKillerId;

    public GameSession(GameManager gameManager, Arena arena) {
        this.gameManager = gameManager;
        this.arena = arena;
        this.quickChatService = new SessionQuickChatService(arena, gameManager.getSecretIdentityManager());
        this.identityService = new SessionIdentityService(gameManager);
        this.loadoutService = new SessionLoadoutService(gameManager);
        this.spectatorService = new SessionSpectatorService(
                gameManager,
                arena,
                alivePlayers,
                identityService::resolveIdentityDisplayName,
                identityService::resolveChatName
        );
        this.endGameMessenger = new SessionEndGameMessenger(
                arena,
                roundParticipants,
                alivePlayers,
                roles,
                this::resolveIdentityDisplayName,
                this::resolveRealPlayerName
        );
    }

    public void start() {
        alivePlayers.clear();
        alivePlayers.addAll(arena.getActivePlayers());
        roundParticipants.clear();
        roundParticipants.addAll(alivePlayers);
        realPlayerNames.clear();
        identityDisplayNames.clear();
        for (UUID playerId : roundParticipants) {
            String realName = identityService.resolveRealPlayerName(playerId);
            if (realName != null && !realName.isBlank()) {
                realPlayerNames.put(playerId, realName);
            }
        }
        spectatorService.clearState();

        assignRoles();
        applySecretIdentities();

        for (UUID playerId : alivePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            Location spawn = pickSpawnLocation();
            if (spawn != null) {
                player.teleport(spawn);
            }
            loadoutService.preparePlayerForRound(player, roles.get(playerId));
            cacheIdentityDisplayName(playerId);
        }

        updateAliveCountDisplays();
        updateChatCompletionsForActivePlayers();
        spectatorService.refreshPlayerVisibility();
    }

    public void endGame() {
        endGameMessenger.sendRoleRevealMessages();
        endGameMessenger.sendWinnerMessage(murdererId, murdererKillerId);
        spectatorService.restoreVisibilityForArenaPlayers();
        spectatorService.clearState();

        for (UUID playerId : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            clearChatCompletions(player);
            spectatorService.prepareForEndGame(player);
            player.setGameMode(GameMode.SPECTATOR);
            showNametag(player);
            gameManager.getSecretIdentityManager().resetIdentity(player);
        }
    }

    public List<UUID> getAlivePlayers() {
        return alivePlayers;
    }

    public Role getRole(UUID playerId) {
        return roles.get(playerId);
    }

    public void enforceHungerLock(Player player) {
        if (player == null) {
            return;
        }
        loadoutService.enforceHungerLock(player, roles.get(player.getUniqueId()));
    }

    public void removeAlive(UUID playerId) {
        removeAlive(playerId, null);
    }

    public void removeAlive(UUID playerId, UUID killerId) {
        alivePlayers.remove(playerId);
        cacheIdentityDisplayName(playerId);
        if (playerId != null && playerId.equals(murdererId) && killerId != null && !killerId.equals(playerId)) {
            murdererKillerId = killerId;
        }
        spectatorService.onAliveListChanged(playerId);
        updateChatCompletionsForActivePlayers();
        updateAliveCountDisplays();
        spectatorService.updateSpectatorBoards();
        spectatorService.refreshPlayerVisibility();
    }

    public UUID getMurdererId() {
        return murdererId;
    }

    public UUID getDetectiveId() {
        return detectiveId;
    }

    public boolean handleInteract(Player player, Component itemName, String legacyName) {
        if (player == null || itemName == null || legacyName == null) {
            return false;
        }
        if (!isAlive(player.getUniqueId())) {
            return spectatorService.handleInteract(player, itemName, legacyName);
        }
        if (quickChatService.handleInteract(player, legacyName)) {
            return true;
        }
        if (itemName.equals(arena.LEAVE_ITEM)) {
            arena.removePlayer(player, gameManager);
            return true;
        }
        return false;
    }

    public boolean eliminatePlayer(Player victim) {
        return eliminatePlayer(victim, null);
    }

    public boolean eliminatePlayer(Player victim, Player killer) {
        if (victim == null || !arena.isPlaying(victim)) {
            return false;
        }
        UUID victimId = victim.getUniqueId();
        if (!isAlive(victimId)) {
            return false;
        }

        cacheIdentityDisplayName(victimId);
        gameManager.getCorpseManager().spawnCorpse(victim, victim.getLocation(), victim.getInventory().getChestplate());
        clearTransientState(victim);
        spectatorService.prepareSpectator(victim, killer);

        UUID killerId = killer == null ? null : killer.getUniqueId();
        removeAlive(victimId, killerId);
        return true;
    }

    public void clearTransientState(Player player) {
        if (player == null) {
            return;
        }
        quickChatService.clearTransientState(player);
        spectatorService.clearTransientState(player);
        clearChatCompletions(player);
    }

    public void beforeArenaRemoval(Player player) {
        if (player == null) {
            return;
        }
        String realName = realPlayerNames.get(player.getUniqueId());
        if (realName == null || realName.isBlank()) {
            realPlayerNames.put(player.getUniqueId(), player.getName());
        }
        cacheIdentityDisplayName(player.getUniqueId());
    }

    public void handlePlayerDisconnect(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        beforeArenaRemoval(player);
        if (!isAlive(playerId)) {
            return;
        }
        gameManager.getCorpseManager().spawnCorpse(player, player.getLocation(), player.getInventory().getChestplate());
        removeAlive(playerId, null);
    }

    public static void hideNametag(Player player) {
        SessionNametagService.hide(player);
    }

    public static void showNametag(Player player) {
        SessionNametagService.show(player);
    }

    public boolean isGameOver() {
        return hasMurdererWon() || haveBystandersWon();
    }

    public boolean hasMurdererWon() {
        if (murdererId == null || !alivePlayers.contains(murdererId)) {
            return false;
        }
        for (UUID playerId : alivePlayers) {
            if (!playerId.equals(murdererId)) {
                return false;
            }
        }
        return true;
    }

    public boolean haveBystandersWon() {
        return murdererId != null && !alivePlayers.contains(murdererId);
    }

    private void assignRoles() {
        roles.clear();
        murdererId = null;
        detectiveId = null;
        murdererKillerId = null;

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

    private Location pickSpawnLocation() {
        List<Location> spawnSpots = arena.getSpawnSpots();
        if (spawnSpots != null && !spawnSpots.isEmpty()) {
            return spawnSpots.get(ThreadLocalRandom.current().nextInt(spawnSpots.size()));
        }
        return arena.getSpawnLocation();
    }

    private boolean isAlive(UUID playerId) {
        return alivePlayers.contains(playerId);
    }

    private void cacheIdentityDisplayName(UUID playerId) {
        if (playerId == null) {
            return;
        }
        String currentIdentityName = gameManager.getSecretIdentityManager().getCurrentIdentityName(playerId);
        if (currentIdentityName == null || currentIdentityName.isBlank()) {
            return;
        }
        String identityDisplayName = identityService.resolveIdentityDisplayName(playerId);
        if (identityDisplayName != null && !identityDisplayName.isBlank()) {
            identityDisplayNames.put(playerId, identityDisplayName);
        }
    }

    private String resolveIdentityDisplayName(UUID playerId) {
        String currentIdentityName = gameManager.getSecretIdentityManager().getCurrentIdentityName(playerId);
        if (currentIdentityName != null && !currentIdentityName.isBlank()) {
            String currentIdentityDisplayName = identityService.resolveIdentityDisplayName(playerId);
            if (currentIdentityDisplayName != null && !currentIdentityDisplayName.isBlank()) {
                identityDisplayNames.put(playerId, currentIdentityDisplayName);
                return currentIdentityDisplayName;
            }
        }
        String cachedIdentityDisplayName = identityDisplayNames.get(playerId);
        if (cachedIdentityDisplayName != null && !cachedIdentityDisplayName.isBlank()) {
            return cachedIdentityDisplayName;
        }
        String realName = resolveRealPlayerName(playerId);
        if (realName == null || realName.isBlank()) {
            return "&fUnknown";
        }
        return "&f" + realName;
    }

    private String resolveRealPlayerName(UUID playerId) {
        String cachedRealName = realPlayerNames.get(playerId);
        if (cachedRealName != null && !cachedRealName.isBlank()) {
            return cachedRealName;
        }
        String currentRealName = identityService.resolveRealPlayerName(playerId);
        if (currentRealName != null && !currentRealName.isBlank()) {
            realPlayerNames.put(playerId, currentRealName);
            return currentRealName;
        }
        return playerId == null ? "Unknown" : playerId.toString();
    }

    private void updateAliveCountDisplays() {
        int aliveNonMurdererCount = countAliveNonMurderers();
        Set<UUID> alive = new HashSet<>(alivePlayers);
        for (UUID playerId : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            if (!alive.contains(playerId)) {
                player.setLevel(0);
                player.setExp(0.0f);
                continue;
            }
            if (playerId.equals(murdererId)) {
                player.setLevel(aliveNonMurdererCount);
                player.setExp(1.0f);
                continue;
            }
            player.setLevel(0);
        }
    }

    private int countAliveNonMurderers() {
        int count = 0;
        for (UUID playerId : alivePlayers) {
            if (!playerId.equals(murdererId)) {
                count++;
            }
        }
        return count;
    }

    private void updateChatCompletionsForActivePlayers() {
        List<String> identityCompletions = identityService.collectIdentityCompletions(arena.getActivePlayers());
        for (UUID playerId : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            player.setCustomChatCompletions(identityCompletions);
        }
    }

    private void clearChatCompletions(Player player) {
        player.setCustomChatCompletions(List.of());
    }
}
