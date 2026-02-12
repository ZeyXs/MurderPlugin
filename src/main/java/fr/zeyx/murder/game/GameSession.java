package fr.zeyx.murder.game;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.game.feature.EndGameMessenger;
import fr.zeyx.murder.game.feature.GunFeature;
import fr.zeyx.murder.game.feature.LoadoutFeature;
import fr.zeyx.murder.game.feature.QuickChatFeature;
import fr.zeyx.murder.game.feature.SpectatorFeature;
import fr.zeyx.murder.game.service.IdentityService;
import fr.zeyx.murder.game.service.NametagService;
import fr.zeyx.murder.manager.CorpseManager;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import fr.zeyx.murder.util.ItemBuilder;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.time.Duration;

public class GameSession {

    private static final String MURDERER_SWITCH_IDENTITY_DISABLED_NAME = "&7&lSwitch Identity&r &7• Right Click";
    private static final String MURDERER_SWITCH_IDENTITY_ENABLED_NAME = "&d&lSwitch Identity&r &7• Right Click";
    private static final String MURDERER_SWITCH_IDENTITY_DISABLED_LEGACY = ChatColor.translateAlternateColorCodes('&', MURDERER_SWITCH_IDENTITY_DISABLED_NAME);
    private static final String MURDERER_SWITCH_IDENTITY_ENABLED_LEGACY = ChatColor.translateAlternateColorCodes('&', MURDERER_SWITCH_IDENTITY_ENABLED_NAME);
    private static final int MURDERER_SWITCH_IDENTITY_SLOT = 4;
    private static final double MURDERER_SWITCH_IDENTITY_RADIUS = 1.75D;

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
    private final EndGameMessenger endGameMessenger;
    private final GunFeature gunFeature;

    private UUID murdererId;
    private UUID detectiveId;
    private UUID murdererKillerId;

    public GameSession(GameManager gameManager, Arena arena, GunFeature gunFeature) {
        this.gameManager = gameManager;
        this.arena = arena;
        this.gunFeature = gunFeature;
        this.quickChatFeature = new QuickChatFeature(arena, gameManager.getSecretIdentityManager());
        this.identityService = new IdentityService(gameManager);
        this.loadoutFeature = new LoadoutFeature(gameManager);
        this.spectatorFeature = new SpectatorFeature(
                gameManager,
                arena,
                alivePlayers,
                identityService::resolveIdentityDisplayName,
                identityService::resolveChatName
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
    }

    public void start() {
        alivePlayers.clear();
        alivePlayers.addAll(arena.getActivePlayers());
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

        for (UUID playerId : alivePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            Location spawn = pickSpawnLocation();
            if (spawn != null) {
                player.teleport(spawn);
            }
            loadoutFeature.preparePlayerForRound(player, roles.get(playerId));
            cacheIdentityDisplayName(playerId);
        }

        updateAliveCountDisplays();
        updateChatCompletionsForActivePlayers();
        spectatorFeature.refreshPlayerVisibility();
        registerCurrentMurdererIdentity();
        updateMurdererSwitchIdentityItem();
    }

    public void endGame() {
        endGameMessenger.sendRoleRevealMessages();
        endGameMessenger.sendWinnerMessage(murdererId, murdererKillerId);
        spectatorFeature.restoreVisibilityForArenaPlayers();
        spectatorFeature.clearState();

        boolean murdererWon = hasMurdererWon();
        UUID winnerId = resolveWinnerId(murdererWon);
        sendPersonalEndGameMessages(murdererWon, winnerId);
        startWinnerFireworks(winnerId);
        Set<UUID> aliveAtEnd = new HashSet<>(alivePlayers);

        for (UUID playerId : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            clearChatCompletions(player);
            if (aliveAtEnd.contains(playerId)) {
                spectatorFeature.prepareForEndGame(player);
                clearInventoryKeepChestplate(player);
                player.setGameMode(GameMode.ADVENTURE);
                player.setAllowFlight(true);
            }
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
        loadoutFeature.enforceHungerLock(player, roles.get(player.getUniqueId()));
    }

    public void tick() {
        updateMurdererSwitchIdentityItem();
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
        updateChatCompletionsForActivePlayers();
        updateAliveCountDisplays();
        spectatorFeature.updateSpectatorBoards();
        spectatorFeature.refreshPlayerVisibility();
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
            return spectatorFeature.handleInteract(player, itemName, legacyName);
        }
        if (isAliveMurderer(player.getUniqueId()) && isMurdererSwitchIdentityItem(legacyName)) {
            handleMurdererIdentitySwitch(player);
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

        UUID killerId = killer == null ? null : killer.getUniqueId();
        removeAlive(victimId, killerId);
        return true;
    }

    public void clearTransientState(Player player) {
        if (player == null) {
            return;
        }
        quickChatFeature.clearTransientState(player);
        spectatorFeature.clearTransientState(player);
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

    private boolean isAliveMurderer(UUID playerId) {
        return playerId != null && playerId.equals(murdererId) && isAlive(playerId);
    }

    private boolean isMurdererSwitchIdentityItem(String legacyName) {
        return MURDERER_SWITCH_IDENTITY_DISABLED_LEGACY.equals(legacyName)
                || MURDERER_SWITCH_IDENTITY_ENABLED_LEGACY.equals(legacyName);
    }

    private void handleMurdererIdentitySwitch(Player murderer) {
        if (murderer == null || !isAliveMurderer(murderer.getUniqueId())) {
            return;
        }
        CorpseManager.CorpseIdentity corpseIdentity = gameManager.getCorpseManager()
                .findNearestCorpseIdentity(murderer.getLocation(), MURDERER_SWITCH_IDENTITY_RADIUS);
        if (corpseIdentity == null
                || !roundParticipants.contains(corpseIdentity.getSourcePlayerId())
                || corpseIdentity.getIdentityName() == null
                || corpseIdentity.getIdentityName().isBlank()) {
            setMurdererSwitchIdentityItem(murderer, false);
            return;
        }

        UUID murdererPlayerId = murderer.getUniqueId();
        String oldMurdererIdentity = gameManager.getSecretIdentityManager().getCurrentIdentityName(murdererPlayerId);
        if (oldMurdererIdentity == null || oldMurdererIdentity.isBlank()) {
            oldMurdererIdentity = murderer.getName();
        }
        ChatColor oldMurdererColor = gameManager.getSecretIdentityManager().getCurrentIdentityColor(murdererPlayerId);
        PlayerProfile oldMurdererProfile = resolveIdentityProfile(murdererPlayerId, murderer);

        gameManager.getSecretIdentityManager().cacheIdentityProfile(
                corpseIdentity.getIdentityName(),
                corpseIdentity.getIdentityProfile()
        );
        boolean switched = gameManager.getSecretIdentityManager().applySpecificIdentityFromCache(
                murderer,
                corpseIdentity.getIdentityName(),
                corpseIdentity.getIdentityColor()
        );
        if (!switched) {
            return;
        }

        gameManager.getCorpseManager().setCorpseIdentity(
                corpseIdentity.getCorpseId(),
                oldMurdererIdentity,
                oldMurdererColor,
                oldMurdererProfile
        );
        cacheIdentityDisplayName(murdererPlayerId);
        registerCurrentMurdererIdentity();
        updateChatCompletionsForActivePlayers();

        String newIdentityDisplay = resolveIdentityDisplayName(murdererPlayerId);
        if (newIdentityDisplay == null || newIdentityDisplay.isBlank()) {
            newIdentityDisplay = "&f" + murderer.getName();
        }
        refreshIdentityChestplate(murderer);
        gameManager.getScoreboardManager().showGameBoard(murderer, "&4Murderer", newIdentityDisplay);
        murderer.sendMessage(ChatUtil.component("&aYou switched identities! You are now: " + newIdentityDisplay));
        setMurdererSwitchIdentityItem(murderer, true);
    }

    private void updateMurdererSwitchIdentityItem() {
        if (!isAliveMurderer(murdererId)) {
            return;
        }
        Player murderer = Bukkit.getPlayer(murdererId);
        if (murderer == null || !murderer.isOnline()) {
            return;
        }
        CorpseManager.CorpseIdentity corpseIdentity = gameManager.getCorpseManager()
                .findNearestCorpseIdentity(murderer.getLocation(), MURDERER_SWITCH_IDENTITY_RADIUS);
        boolean canActivate = corpseIdentity != null && roundParticipants.contains(corpseIdentity.getSourcePlayerId());
        setMurdererSwitchIdentityItem(murderer, canActivate);
    }

    private void setMurdererSwitchIdentityItem(Player murderer, boolean active) {
        if (murderer == null) {
            return;
        }
        if (isQuickChatMenuOpen(murderer)) {
            return;
        }
        ItemStack current = murderer.getInventory().getItem(MURDERER_SWITCH_IDENTITY_SLOT);
        Material expectedMaterial = active ? Material.PINK_DYE : Material.GRAY_DYE;
        String expectedLegacyName = active ? MURDERER_SWITCH_IDENTITY_ENABLED_LEGACY : MURDERER_SWITCH_IDENTITY_DISABLED_LEGACY;
        if (current != null && current.getType() == expectedMaterial && current.hasItemMeta()) {
            Component currentName = current.getItemMeta().displayName();
            if (currentName != null) {
                String legacy = org.bukkit.ChatColor.stripColor(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(currentName));
                String expected = org.bukkit.ChatColor.stripColor(expectedLegacyName);
                if (legacy != null && legacy.equals(expected)) {
                    return;
                }
            }
        }
        murderer.getInventory().setItem(
                MURDERER_SWITCH_IDENTITY_SLOT,
                new ItemBuilder(expectedMaterial)
                        .setName(ChatUtil.itemComponent(active ? MURDERER_SWITCH_IDENTITY_ENABLED_NAME : MURDERER_SWITCH_IDENTITY_DISABLED_NAME))
                        .toItemStack()
        );
    }

    private boolean isQuickChatMenuOpen(Player player) {
        if (player == null) {
            return false;
        }
        ItemStack slotEight = player.getInventory().getItem(8);
        if (slotEight == null || !slotEight.hasItemMeta()) {
            return false;
        }
        Component itemName = slotEight.getItemMeta().displayName();
        if (itemName == null) {
            return false;
        }
        String legacyName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(itemName);
        return QuickChatMenu.CLOSE_NAME.equals(legacyName);
    }

    private void refreshIdentityChestplate(Player player) {
        if (player == null) {
            return;
        }
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate == null || chestplate.getType() != Material.LEATHER_CHESTPLATE) {
            return;
        }
        if (chestplate.getItemMeta() instanceof LeatherArmorMeta chestplateMeta) {
            chestplateMeta.setColor(gameManager.getSecretIdentityManager().getCurrentIdentityLeatherColor(player.getUniqueId()));
            chestplate.setItemMeta(chestplateMeta);
            player.getInventory().setChestplate(chestplate);
        }
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

    private void clearInventoryKeepChestplate(Player player) {
        if (player == null) {
            return;
        }
        ItemStack chestplate = player.getInventory().getChestplate();
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
    }

    private UUID resolveWinnerId(boolean murdererWon) {
        if (murdererWon) {
            return murdererId;
        }
        if (murdererKillerId != null) {
            return murdererKillerId;
        }
        for (UUID playerId : alivePlayers) {
            if (!playerId.equals(murdererId)) {
                return playerId;
            }
        }
        return null;
    }

    private void sendPersonalEndGameMessages(boolean murdererWon, UUID winnerId) {
        for (UUID playerId : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            if (winnerId != null && winnerId.equals(playerId)) {
                showEndGameTitle(player, "&aYou won!");
                continue;
            }

            if (murdererWon) {
                if (!playerId.equals(murdererId)) {
                    showEndGameTitle(player, "&cYou lose!");
                }
                continue;
            }

            Role role = roles.get(playerId);
            boolean aliveInnocent = alivePlayers.contains(playerId)
                    && role != null
                    && role != Role.MURDERER;
            if (aliveInnocent) {
                showEndGameTitle(player, "&aYou survived!");
            }
        }
    }

    private void showEndGameTitle(Player player, String title) {
        if (player == null || title == null || title.isBlank()) {
            return;
        }
        player.showTitle(Title.title(
                ChatUtil.component(title),
                Component.empty(),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(500))
        ));
    }

    private void startWinnerFireworks(UUID winnerId) {
        if (winnerId == null) {
            return;
        }
        new BukkitRunnable() {
            private int elapsedTicks = 0;

            @Override
            public void run() {
                Player winner = Bukkit.getPlayer(winnerId);
                if (winner == null || !winner.isOnline() || !arena.isPlaying(winner)) {
                    cancel();
                    return;
                }
                spawnWinnerFirework(winner);
                elapsedTicks += 10;
                if (elapsedTicks >= 20 * 5) {
                    cancel();
                }
            }
        }.runTaskTimer(MurderPlugin.getInstance(), 0L, 20L);
    }

    private void spawnWinnerFirework(Player winner) {
        if (winner == null || winner.getWorld() == null) {
            return;
        }
        Firework firework = winner.getWorld().spawn(winner.getLocation().clone().add(0.0D, 1.0D, 0.0D), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        Color primary = Color.fromRGB(
                ThreadLocalRandom.current().nextInt(64, 256),
                ThreadLocalRandom.current().nextInt(64, 256),
                ThreadLocalRandom.current().nextInt(64, 256)
        );
        Color fade = Color.fromRGB(
                ThreadLocalRandom.current().nextInt(64, 256),
                ThreadLocalRandom.current().nextInt(64, 256),
                ThreadLocalRandom.current().nextInt(64, 256)
        );
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL)
                .withColor(primary)
                .withFade(fade)
                .trail(true)
                .flicker(true)
                .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
        Bukkit.getScheduler().runTaskLater(MurderPlugin.getInstance(), firework::detonate, 20L);
    }
}
