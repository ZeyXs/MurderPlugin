package fr.zeyx.murder.game;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.game.feature.EndGameMessenger;
import fr.zeyx.murder.game.feature.EndGameFeature;
import fr.zeyx.murder.game.feature.GunFeature;
import fr.zeyx.murder.game.feature.KnifeFeature;
import fr.zeyx.murder.game.feature.LoadoutFeature;
import fr.zeyx.murder.game.feature.SwitchIdentityFeature;
import fr.zeyx.murder.game.feature.QuickChatFeature;
import fr.zeyx.murder.game.feature.SpectatorFeature;
import fr.zeyx.murder.game.service.AliveDisplayService;
import fr.zeyx.murder.game.service.IdentityService;
import fr.zeyx.murder.game.service.NametagService;
import fr.zeyx.murder.manager.GameManager;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GameSession {

    private static final int ROUND_DURATION_SECONDS = 420;

    private final GameManager gameManager;
    private final Arena arena;
    private final List<UUID> alivePlayers = new ArrayList<>();
    private final List<UUID> roundParticipants = new ArrayList<>();
    private final Map<UUID, Role> roles = new HashMap<>();
    private final Map<UUID, String> realPlayerNames = new HashMap<>();
    private final Map<UUID, String> identityDisplayNames = new HashMap<>();
    private final List<String> murdererIdentityHistory = new ArrayList<>();

    private final QuickChatFeature quickChatFeature;
    private final IdentityService identityService;
    private final LoadoutFeature loadoutFeature;
    private final SpectatorFeature spectatorFeature;
    private final AliveDisplayService aliveDisplayService;
    private final SwitchIdentityFeature switchIdentityFeature;
    private final EndGameMessenger endGameMessenger;
    private final EndGameFeature endGameFeature;
    private final GunFeature gunFeature;
    private final KnifeFeature knifeFeature;

    private UUID murdererId;
    private UUID detectiveId;
    private UUID murdererKillerId;
    private int murdererKillCount;
    private int roundTimeLeftSeconds = ROUND_DURATION_SECONDS;
    private boolean murdererUnsuccessful;

    public GameSession(GameManager gameManager, Arena arena, GunFeature gunFeature, KnifeFeature knifeFeature) {
        this.gameManager = gameManager;
        this.arena = arena;
        this.gunFeature = gunFeature;
        this.knifeFeature = knifeFeature;
        this.quickChatFeature = new QuickChatFeature(arena, gameManager.getSecretIdentityManager(), this::getAlivePlayers);
        this.identityService = new IdentityService(gameManager);
        this.loadoutFeature = new LoadoutFeature(gameManager);
        this.aliveDisplayService = new AliveDisplayService(arena, identityService);
        this.switchIdentityFeature = new SwitchIdentityFeature(gameManager);
        this.spectatorFeature = new SpectatorFeature(
                gameManager,
                arena,
                alivePlayers,
                identityService::resolveIdentityDisplayName,
                identityService::resolveChatName,
                this::getRole,
                this::resolveWeaponCountForSpectatorView,
                this::getMurdererKillCount
        );
        this.endGameMessenger = new EndGameMessenger(
                arena,
                roundParticipants,
                alivePlayers,
                roles,
                this::resolveRoleRevealIdentityName,
                this::resolveIdentityDisplayName,
                this::resolveRealPlayerName
        );
        this.endGameFeature = new EndGameFeature(arena, gameManager);
    }

    public void start() {
        alivePlayers.clear();
        alivePlayers.addAll(arena.getActivePlayers());
        roundTimeLeftSeconds = ROUND_DURATION_SECONDS;
        murdererUnsuccessful = false;
        roundParticipants.clear();
        roundParticipants.addAll(alivePlayers);
        realPlayerNames.clear();
        identityDisplayNames.clear();
        murdererIdentityHistory.clear();
        for (UUID playerId : roundParticipants) {
            String realName = identityService.resolveRealPlayerName(playerId);
            if (realName != null && !realName.isBlank()) {
                realPlayerNames.put(playerId, realName);
            }
        }
        spectatorFeature.clearState();

        assignRoles();
        applySecretIdentities();
        List<Location> roundSpawns = assignUniqueRoundSpawns(alivePlayers.size());
        int spawnIndex = 0;

        for (UUID playerId : alivePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            Location spawn = spawnIndex < roundSpawns.size() ? roundSpawns.get(spawnIndex++) : null;
            if (spawn != null) {
                player.teleport(spawn.clone());
            }
            loadoutFeature.preparePlayerForRound(player, roles.get(playerId));
            cacheIdentityDisplayName(playerId);
        }

        aliveDisplayService.updateAliveCountDisplays(alivePlayers, murdererId);
        aliveDisplayService.updateChatCompletionsForActivePlayers();
        spectatorFeature.refreshPlayerVisibility();
        registerCurrentMurdererIdentity();
        switchIdentityFeature.updateSwitchIdentityItem(murdererId, alivePlayers, roundParticipants);
        gameManager.getArenaTabListService().refreshNow();
    }

    public void endGame() {
        endGameMessenger.sendRoleRevealMessages();
        endGameMessenger.sendWinnerMessage(murdererId, murdererKillerId, murdererUnsuccessful);
        spectatorFeature.restoreVisibilityForArenaPlayers();
        spectatorFeature.clearState();

        endGameFeature.applyEndGameState(
                alivePlayers,
                roles,
                murdererId,
                murdererKillerId,
                hasMurdererWon(),
                spectatorFeature
        );
        gameManager.getArenaTabListService().refreshNow();
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
        loadoutFeature.enforceHungerLock(player, roles.get(player.getUniqueId()));
    }

    public void tick() {
        switchIdentityFeature.updateSwitchIdentityItem(murdererId, alivePlayers, roundParticipants);
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
        spectatorFeature.onAliveListChanged(playerId);
        aliveDisplayService.updateChatCompletionsForActivePlayers();
        aliveDisplayService.updateAliveCountDisplays(alivePlayers, murdererId);
        spectatorFeature.updateSpectatorBoards(roundTimeLeftSeconds);
        spectatorFeature.refreshPlayerVisibility();
        gameManager.getArenaTabListService().refreshNow();
    }

    public boolean handleInteract(Player player, Component itemName, String legacyName) {
        if (player == null) {
            return false;
        }
        if (!isAlive(player.getUniqueId())) {
            return spectatorFeature.handleInteract(player, itemName, legacyName);
        }
        if (itemName == null || legacyName == null) {
            return false;
        }
        if (switchIdentityFeature.isSwitchIdentityItem(legacyName)) {
            switchIdentityFeature.handleIdentitySwitch(
                    player,
                    murdererId,
                    alivePlayers,
                    roundParticipants,
                    this::resolveIdentityDisplayName,
                    this::onMurdererIdentityChanged
            );
            return true;
        }
        if (quickChatFeature.handleInteract(player, legacyName)) {
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
        UUID killerId = killer == null ? null : killer.getUniqueId();
        if (killerId != null && killerId.equals(murdererId)) {
            murdererKillCount++;
        }

        cacheIdentityDisplayName(victimId);
        gameManager.getCorpseManager().spawnCorpse(
                victim,
                victim.getLocation(),
                victim.getInventory().getChestplate(),
                gameManager.getSecretIdentityManager().getCurrentIdentityName(victimId),
                gameManager.getSecretIdentityManager().getCurrentIdentityColor(victimId),
                resolveIdentityProfile(victimId, victim)
        );
        if (gunFeature != null) {
            gunFeature.onPlayerEliminated(victim, this);
        }
        clearTransientState(victim);
        spectatorFeature.prepareSpectator(victim, killer);

        removeAlive(victimId, killerId);
        return true;
    }

    public void clearTransientState(Player player) {
        if (player == null) {
            return;
        }
        quickChatFeature.clearTransientState(player);
        spectatorFeature.clearTransientState(player);
        aliveDisplayService.clearChatCompletions(player);
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
        gameManager.getCorpseManager().spawnCorpse(
                player,
                player.getLocation(),
                player.getInventory().getChestplate(),
                gameManager.getSecretIdentityManager().getCurrentIdentityName(playerId),
                gameManager.getSecretIdentityManager().getCurrentIdentityColor(playerId),
                resolveIdentityProfile(playerId, player)
        );
        removeAlive(playerId, null);
    }

    public static void hideNametag(Player player) {
        NametagService.hide(player);
    }

    public static void showNametag(Player player) {
        NametagService.show(player);
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
        return murdererUnsuccessful || (murdererId != null && !alivePlayers.contains(murdererId));
    }

    public void updateSpectatorBoards(int roundTimeLeftSeconds) {
        this.roundTimeLeftSeconds = Math.max(0, roundTimeLeftSeconds);
        spectatorFeature.updateSpectatorBoards(this.roundTimeLeftSeconds);
    }

    public void markMurdererUnsuccessful() {
        murdererUnsuccessful = true;
    }

    private void assignRoles() {
        roles.clear();
        murdererId = null;
        detectiveId = null;
        murdererKillerId = null;
        murdererKillCount = 0;

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

    private List<Location> assignUniqueRoundSpawns(int playerCount) {
        List<Location> availableSpawns = collectDistinctConfiguredSpawns();
        if (availableSpawns.isEmpty()) {
            Location fallback = arena.getSpawnLocation();
            if (fallback != null && fallback.getWorld() != null) {
                availableSpawns.add(fallback.clone());
            }
        }
        Collections.shuffle(availableSpawns);

        List<Location> assignedSpawns = new ArrayList<>(Math.max(playerCount, 0));
        Set<String> usedSpawnKeys = new HashSet<>();

        for (Location spawn : availableSpawns) {
            if (spawn == null || spawn.getWorld() == null) {
                continue;
            }
            if (assignedSpawns.size() >= playerCount) {
                break;
            }
            if (usedSpawnKeys.add(toBlockKey(spawn))) {
                assignedSpawns.add(spawn.clone());
            }
        }

        if (assignedSpawns.size() >= playerCount || assignedSpawns.isEmpty()) {
            return assignedSpawns;
        }

        // If config has fewer unique spawn spots than players, expand around the first spot.
        Location anchor = assignedSpawns.get(0).clone();
        int radius = 1;
        while (assignedSpawns.size() < playerCount) {
            for (int x = -radius; x <= radius && assignedSpawns.size() < playerCount; x++) {
                for (int z = -radius; z <= radius && assignedSpawns.size() < playerCount; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) {
                        continue;
                    }
                    Location candidate = anchor.clone().add(x * 2.0D, 0.0D, z * 2.0D);
                    if (usedSpawnKeys.add(toBlockKey(candidate))) {
                        assignedSpawns.add(candidate);
                    }
                }
            }
            radius++;
        }
        return assignedSpawns;
    }

    private List<Location> collectDistinctConfiguredSpawns() {
        List<Location> distinctSpawns = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Location> configuredSpawns = arena.getSpawnSpots();
        if (configuredSpawns == null) {
            return distinctSpawns;
        }
        for (Location spawn : configuredSpawns) {
            if (spawn == null || spawn.getWorld() == null) {
                continue;
            }
            if (seen.add(toBlockKey(spawn))) {
                distinctSpawns.add(spawn.clone());
            }
        }
        return distinctSpawns;
    }

    private String toBlockKey(Location location) {
        return location.getWorld().getUID()
                + ":" + location.getBlockX()
                + ":" + location.getBlockY()
                + ":" + location.getBlockZ();
    }

    private boolean isAlive(UUID playerId) {
        return alivePlayers.contains(playerId);
    }

    private PlayerProfile resolveIdentityProfile(UUID playerId, Player fallbackPlayer) {
        PlayerProfile identityProfile = gameManager.getSecretIdentityManager().getCurrentIdentityProfile(playerId);
        if (identityProfile != null) {
            return identityProfile;
        }
        if (fallbackPlayer == null || fallbackPlayer.getPlayerProfile() == null) {
            return null;
        }
        return fallbackPlayer.getPlayerProfile().clone();
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

    private String resolveRoleRevealIdentityName(UUID playerId) {
        if (playerId != null && playerId.equals(murdererId) && !murdererIdentityHistory.isEmpty()) {
            return String.join("&f, ", murdererIdentityHistory);
        }
        return resolveIdentityDisplayName(playerId);
    }

    private void registerCurrentMurdererIdentity() {
        if (murdererId == null) {
            return;
        }
        String display = resolveIdentityDisplayName(murdererId);
        if (display != null && !display.isBlank()) {
            murdererIdentityHistory.add(display);
        }
    }

    private void onMurdererIdentityChanged(UUID playerId) {
        cacheIdentityDisplayName(playerId);
        registerCurrentMurdererIdentity();
        aliveDisplayService.updateChatCompletionsForActivePlayers();
        gameManager.getArenaTabListService().refreshNow();
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

    private int getMurdererKillCount() {
        return murdererKillCount;
    }

    private int resolveWeaponCountForSpectatorView(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return 0;
        }
        Role role = roles.get(playerId);
        if (role == Role.MURDERER) {
            return knifeFeature == null ? 0 : knifeFeature.getKnifeCount(player);
        }
        int gunCount = gunFeature == null ? 0 : gunFeature.getGunCount(player);
        return gunCount > 0 ? 1 : 0;
    }

}
