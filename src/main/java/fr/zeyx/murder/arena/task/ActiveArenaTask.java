package fr.zeyx.murder.arena.task;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
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
    private final GameSession session;

    public ActiveArenaTask(GameManager gameManager, Arena arena, GameSession session) {
        this.gameManager = gameManager;
        this.arena = arena;
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

        if (session.getAlivePlayers().size() <= 1) {
            if (session.getAlivePlayers().isEmpty()) {
                arena.setArenaSate(new WaitingArenaState(gameManager, arena));
            } else {
                cancel();

                session.endGame();

                MurderPlugin.getInstance().getServer().getScheduler().runTaskLater(MurderPlugin.getInstance(), endGameTask -> {
                    arena.setArenaSate(new WaitingArenaState(gameManager, arena));
                    for (UUID playerId : session.getAlivePlayers()) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null) arena.removePlayer(player, gameManager);
                    }
                }, 20 * 5);
            }
        }
    }

}
