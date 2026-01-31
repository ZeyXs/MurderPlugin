package fr.zeyx.murder.arena.state;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.ArenaState;
import fr.zeyx.murder.arena.task.ActiveArenaTask;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ActiveArenaState extends ArenaState {

    private final GameManager gameManager;
    private final Arena arena;
    private ActiveArenaTask activeArenaTask;

    public final List<UUID> alivePlayers = new ArrayList<>();

    public ActiveArenaState(GameManager gameManager, Arena arena) {
        this.gameManager = gameManager;
        this.arena = arena;
    }

    @Override
    public void onEnable() {
        super.onEnable();

        alivePlayers.addAll(arena.getActivePlayers());

        activeArenaTask = new ActiveArenaTask(gameManager, arena, this);
        activeArenaTask.runTaskTimer(MurderPlugin.getInstance(), 0, 8);

        for (UUID playerId : alivePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            player.teleport(arena.getSpawnLocation());
            player.getInventory().clear();
            gameManager.getScoreboardManager().showGameBoard(player);
        }
    }

    public void endGame() {
        for (UUID playerId : alivePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            player.sendMessage(ChatUtil.prefixed("&7End of the game!"));
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                event.setCancelled(true);
                return;
            }
            event.setDamage(0);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (arena.isPlaying(player)) {
            alivePlayers.remove(player.getUniqueId());
            arena.removePlayer(player, gameManager);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (arena.isPlaying(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (arena.isPlaying(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        Player player = (Player) event.getEntity();
        if (arena.isPlaying(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (arena.isPlaying(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(EntityDropItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (arena.isPlaying(player)) event.setCancelled(true);
        }
    }

}
