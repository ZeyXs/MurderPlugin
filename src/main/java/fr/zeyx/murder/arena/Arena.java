package fr.zeyx.murder.arena;

import fr.zeyx.murder.arena.state.ActiveArenaState;
import fr.zeyx.murder.arena.state.InitArenaState;
import fr.zeyx.murder.arena.state.StartingArenaState;
import fr.zeyx.murder.arena.state.WaitingArenaState;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import fr.zeyx.murder.util.ItemBuilder;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.UUID;

public class Arena {

    private String name;
    private String displayName;
    private Location spawnLocation;
    private List<UUID> activePlayers;
    private ArenaState arenaState;

    public final String LEAVE_ITEM = ChatUtil.color("&cLeave &7(Right-click)");

    public Arena(String name, String displayName, Location spawnLocation, List<UUID> activePlayers, ArenaState arenaState) {
        this.name = name;
        this.displayName = displayName;
        this.spawnLocation = spawnLocation;
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
        player.setTotalExperience(0);
        player.getInventory().clear();
        player.getInventory().setHeldItemSlot(0);
        player.getInventory().setItem(8, new ItemBuilder(Material.BARRIER).setName(LEAVE_ITEM).toItemStack());
        Location lobbyLocation = gameManager.getConfigurationManager().getLobbyLocation();
        if (lobbyLocation != null) {
            player.teleport(lobbyLocation);
        }
        updateLobbyBoards(gameManager);

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 50, 1);
        sendArenaMessage(ChatColor.of("#ff731c") + player.getDisplayName() + " &7joined the game! " + ChatColor.of("#ffba3b") + "(" + activePlayers.size() + "/12)");

        if (activePlayers.size() >= 2 && !(arenaState instanceof StartingArenaState)) {
            setArenaSate(new StartingArenaState(gameManager, this));
        }
    }

    // TODO: Leave while in game
    public void removePlayer(Player player, GameManager gameManager) {
        if (!(activePlayers.remove(player.getUniqueId()))) return;
        gameManager.getConfigurationManager().loadRollback(player);
        Location lobbyLocation = gameManager.getConfigurationManager().getLobbyLocation();
        if (lobbyLocation != null) {
            player.teleport(lobbyLocation);
        }
        gameManager.getScoreboardManager().clear(player);
        updateLobbyBoards(gameManager);

        if (!(arenaState instanceof ActiveArenaState activeArenaState)) {
            player.sendMessage(ChatUtil.prefixed("&7You left the game."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 50, 1);
            sendArenaMessage(ChatColor.of("#ff2424") + player.getDisplayName() + " &7left the game! " + ChatColor.of("#ff4040") + "(" + activePlayers.size() + "/12)");
        } else {
            player.removePotionEffect(PotionEffectType.SPEED);
            activeArenaState.alivePlayers.remove(player.getUniqueId());
        }

        if (activePlayers.size() <= 3 && arenaState instanceof StartingArenaState startingArenaState) {
            startingArenaState.getArenaStartingTask().cancel();
            setArenaSate(new WaitingArenaState(gameManager, this));
            sendArenaMessage("&cStart cancelled! Need at least " + ChatColor.of("#ff7e21") + "4 players &7to start a game!");
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
}
