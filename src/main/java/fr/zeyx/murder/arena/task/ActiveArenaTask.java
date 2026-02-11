package fr.zeyx.murder.arena.task;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.state.ActiveArenaState;
import fr.zeyx.murder.arena.state.WaitingArenaState;
import fr.zeyx.murder.game.GameSession;
import fr.zeyx.murder.manager.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.UUID;

public class ActiveArenaTask extends BukkitRunnable {

    private final GameManager gameManager;
    private final Arena arena;
    private final ActiveArenaState activeArenaState;
    private final GameSession session;

    public ActiveArenaTask(GameManager gameManager, Arena arena, ActiveArenaState activeArenaState, GameSession session) {
        this.gameManager = gameManager;
        this.arena = arena;
        this.activeArenaState = activeArenaState;
        this.session = session;
    }

    @Override
    public void run() {
        for (UUID playerId : new ArrayList<>(session.getAlivePlayers())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                session.enforceHungerLock(player);
            }
        }

        if (session.isGameOver()) {
            cancel();
            if (activeArenaState != null) {
                activeArenaState.clearAllKnifeItems();
            }
            session.endGame();

            MurderPlugin.getInstance().getServer().getScheduler().runTaskLater(MurderPlugin.getInstance(), endGameTask -> {
                gameManager.getCorpseManager().clearCorpses();
                arena.setArenaSate(new WaitingArenaState(gameManager, arena));
                for (UUID playerId : new ArrayList<>(arena.getActivePlayers())) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        arena.removePlayer(player, gameManager);
                    }
                }
            }, 20 * 5);
        }
    }

}
