package fr.zeyx.murder.arena.task;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.state.ActiveArenaState;
import fr.zeyx.murder.arena.state.WaitingArenaState;
import fr.zeyx.murder.manager.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class ActiveArenaTask extends BukkitRunnable {

    private final GameManager gameManager;
    private final Arena arena;
    private final ActiveArenaState activeArenaState;

    public ActiveArenaTask(GameManager gameManager, Arena arena, ActiveArenaState activeArenaState) {
        this.gameManager = gameManager;
        this.arena = arena;
        this.activeArenaState = activeArenaState;
    }

    @Override
    public void run() {
        if (activeArenaState.alivePlayers.size() <= 1) {
            if (activeArenaState.alivePlayers.isEmpty()) {
                arena.setArenaSate(new WaitingArenaState(gameManager, arena));
            } else {
                cancel();

                // TODO: Win system
                activeArenaState.endGame();

                MurderPlugin.getInstance().getServer().getScheduler().runTaskLater(MurderPlugin.getInstance(), endGameTask -> {
                    arena.setArenaSate(new WaitingArenaState(gameManager, arena));
                    for (UUID playerId : activeArenaState.alivePlayers) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null) arena.removePlayer(player, gameManager);
                    }
                }, 20 * 5);
            }
        }
    }

}
