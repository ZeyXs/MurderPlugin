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
import java.util.Set;
import java.util.UUID;

public class ActiveArenaTask extends BukkitRunnable {

    private static final int ROUND_DURATION_SECONDS = 420;
    private static final int TICKS_PER_SECOND = 20;
    private static final Set<Integer> TIME_REMAINING_ANNOUNCEMENTS = Set.of(
            360, 240, 120, 60, 30, 15, 10, 5, 4, 3, 2, 1, 0
    );

    private final GameManager gameManager;
    private final Arena arena;
    private final ActiveArenaState activeArenaState;
    private final GameSession session;
    private int remainingSeconds = ROUND_DURATION_SECONDS;
    private int tickCounter;

    public ActiveArenaTask(GameManager gameManager, Arena arena, ActiveArenaState activeArenaState, GameSession session) {
        this.gameManager = gameManager;
        this.arena = arena;
        this.activeArenaState = activeArenaState;
        this.session = session;
        this.session.updateSpectatorBoards(remainingSeconds);
    }

    @Override
    public void run() {
        session.tick();
        updateRoundTimer();

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
                activeArenaState.clearAllGunItems();
                activeArenaState.clearAllEmeraldItems();
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

    private void updateRoundTimer() {
        if (session.isGameOver() || remainingSeconds <= 0) {
            return;
        }
        tickCounter++;
        if (tickCounter < TICKS_PER_SECOND) {
            return;
        }
        tickCounter = 0;
        remainingSeconds = Math.max(0, remainingSeconds - 1);
        session.updateSpectatorBoards(remainingSeconds);
        if (TIME_REMAINING_ANNOUNCEMENTS.contains(remainingSeconds)) {
            arena.sendArenaMessage("&7Time remaining: &a" + remainingSeconds + " &7seconds!");
        }
        if (remainingSeconds == 0) {
            session.markMurdererUnsuccessful();
        }
    }

}
