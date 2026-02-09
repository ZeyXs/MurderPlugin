package fr.zeyx.murder.arena;

import fr.zeyx.murder.arena.state.ActiveArenaState;
import fr.zeyx.murder.arena.state.InitArenaState;
import fr.zeyx.murder.arena.state.StartingArenaState;
import fr.zeyx.murder.arena.state.WaitingArenaState;
import fr.zeyx.murder.game.GameSession;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.BookUtil;
import fr.zeyx.murder.util.ChatUtil;
import fr.zeyx.murder.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Arena {

    private String name;
    private String displayName;
    private Location spawnLocation;
    private List<Location> spawnSpots;
    private List<Location> emeraldSpots;
    private List<UUID> activePlayers;
    private ArenaState arenaState;

    public final Component HOW_TO_PLAY_ITEM = ChatUtil.itemComponent("&6&lHow to Play&r&7 \u2022 Right Click");
    public final Component SELECT_EQUIPMENT_ITEM = ChatUtil.itemComponent("&2&lSelect Equipment&r&7 \u2022 Right Click");
    public final Component STORE_ITEM = ChatUtil.itemComponent("&b&lStore&r&7 \u2022 Right Click");
    public final Component VIEW_STATS_ITEM = ChatUtil.itemComponent("&c&lView Stats&r&7 \u2022 Right Click");
    public final Component LEAVE_ITEM = ChatUtil.itemComponent("&e&lLeave&r&7 \u2022 Right Click");

    public Arena(
            String name,
            String displayName,
            Location spawnLocation,
            List<Location> spawnSpots,
            List<Location> emeraldSpots,
            List<UUID> activePlayers,
            ArenaState arenaState
    ) {
        this.name = name;
        this.displayName = displayName;
        this.spawnLocation = spawnLocation;
        this.spawnSpots = spawnSpots == null ? new ArrayList<>() : spawnSpots;
        this.emeraldSpots = emeraldSpots == null ? new ArrayList<>() : emeraldSpots;
        this.activePlayers = activePlayers;
        this.arenaState = arenaState;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public List<Location> getSpawnSpots() {
        return spawnSpots;
    }

    public List<Location> getEmeraldSpots() {
        return emeraldSpots;
    }

    public List<UUID> getActivePlayers() {
        return activePlayers;
    }

    public void setArenaSate(ArenaState arenaState) {
        this.arenaState.onDisable();
        this.arenaState = arenaState;
        arenaState.onEnable();
    }

    public ArenaState getArenaState() {
        return arenaState;
    }

    public void addPlayer(Player player, GameManager gameManager) {
        if (arenaState instanceof InitArenaState) setArenaSate(new WaitingArenaState(gameManager, this));
        activePlayers.add(player.getUniqueId());

        gameManager.getConfigurationManager().saveRollback(player);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setExperienceLevelAndProgress(0);
        player.getInventory().clear();
        player.getInventory().setHeldItemSlot(0);
        player.getInventory().setItem(0, BookUtil.buildBook(gameManager.getConfigurationManager(), "lobby-how-to", HOW_TO_PLAY_ITEM));
        player.getInventory().setItem(3, new ItemBuilder(Material.ENDER_CHEST).setName(SELECT_EQUIPMENT_ITEM).toItemStack());
        player.getInventory().setItem(4, new ItemBuilder(Material.EMERALD).setName(STORE_ITEM).toItemStack());
        ItemStack statsHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) statsHead.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(player);
            skullMeta.displayName(VIEW_STATS_ITEM);
            statsHead.setItemMeta(skullMeta);
        }
        player.getInventory().setItem(5, statsHead);
        player.getInventory().setItem(8, new ItemBuilder(Material.CLOCK).setName(LEAVE_ITEM).toItemStack());
        Location lobbyLocation = gameManager.getConfigurationManager().getLobbyLocation();
        if (lobbyLocation != null) {
            player.teleport(lobbyLocation);
        }
        updateLobbyBoards(gameManager);

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 50, 1);
        gameManager.getArenaManager().getOrCreateVoteSession().sendVotePrompt(player);

        if (activePlayers.size() >= 4 && !(arenaState instanceof StartingArenaState)) {
            setArenaSate(new StartingArenaState(gameManager, this));
        }
    }

    public void removePlayer(Player player, GameManager gameManager) {
        if (!(activePlayers.remove(player.getUniqueId()))) return;
        if (arenaState instanceof ActiveArenaState activeArenaState) {
            GameSession session = activeArenaState.getSession();
            if (session != null) {
                session.beforeArenaRemoval(player);
                session.clearTransientState(player);
            }
        }
        if (gameManager.getArenaManager().getVoteSession() != null) {
            gameManager.getArenaManager().getVoteSession().removeVote(player.getUniqueId());
        }
        gameManager.getConfigurationManager().loadRollback(player);
        gameManager.getSecretIdentityManager().resetIdentity(player);
        GameSession.showNametag(player);
        gameManager.getScoreboardManager().clear(player);
        updateLobbyBoards(gameManager);

        if (!(arenaState instanceof ActiveArenaState activeArenaState)) {
            player.sendMessage(ChatUtil.prefixed("&7You left the game."));
        } else {
            player.removePotionEffect(PotionEffectType.SPEED);
            GameSession session = activeArenaState.getSession();
            if (session != null) {
                session.removeAlive(player.getUniqueId());
            }
        }

        if (activePlayers.size() <= 3 && arenaState instanceof StartingArenaState startingArenaState) {
            startingArenaState.getArenaStartingTask().cancel();
            setArenaSate(new WaitingArenaState(gameManager, this));
        }

        if (activePlayers.isEmpty()) {
            gameManager.getArenaManager().resetVoteSession();
        }
    }

    public boolean isPlaying(Player player) {
        return activePlayers.contains(player.getUniqueId());
    }

    public void sendArenaMessage(String message) {
        for (UUID playerId : this.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(ChatUtil.prefixed(message));
            }
        }
    }

    public void reset(GameManager gameManager) {
        setArenaSate(new WaitingArenaState(gameManager, this));
        for (UUID playerId : activePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) removePlayer(player, gameManager);
        }
    }

    private void updateLobbyBoards(GameManager gameManager) {
        int lobbyPlayers = activePlayers.size();
        for (UUID playerId : activePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                gameManager.getScoreboardManager().showLobbyBoard(player, lobbyPlayers);
            }
        }
    }

    public void transferPlayersTo(Arena target) {
        if (target == null || target == this) {
            return;
        }
        target.getActivePlayers().clear();
        target.getActivePlayers().addAll(this.activePlayers);
        this.activePlayers.clear();
    }
}
