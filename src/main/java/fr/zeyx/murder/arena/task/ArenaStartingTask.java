package fr.zeyx.murder.arena.task;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.state.ActiveArenaState;
import fr.zeyx.murder.arena.state.WaitingArenaState;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.manager.MapVoteSession;
import fr.zeyx.murder.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class ArenaStartingTask extends BukkitRunnable {

    private final GameManager gameManager;
    private final Arena lobbyArena;
    private final MapVoteSession voteSession;
    private final boolean forceStart;
    private int timeUntilStart;

    public ArenaStartingTask(GameManager gameManager, Arena lobbyArena, MapVoteSession voteSession, int timeUntilStart, boolean forceStart) {
        this.gameManager = gameManager;
        this.lobbyArena = lobbyArena;
        this.voteSession = voteSession;
        this.timeUntilStart = timeUntilStart;
        this.forceStart = forceStart;
    }

    @Override
    public void run() {
        if (!forceStart && lobbyArena.getActivePlayers().size() < 4) {
            cancel();
            return;
        }

        if (timeUntilStart <= 0) {
            cancel();
            startGame();
            return;
        }

        if (timeUntilStart == 5) {
            lockVoteAndAnnounce();
        }

        for (UUID playerId : lobbyArena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            player.sendActionBar(ChatUtil.component("&f&lJoining Map in &r&7» &b&l" + timeUntilStart + " seconds"));
        }
        timeUntilStart--;
    }

    private void lockVoteAndAnnounce() {
        if (voteSession == null) {
            return;
        }
        Arena selected = voteSession.lockAndSelect();
        if (selected != null) {
            lobbyArena.sendArenaMessage("&aMap selected: &e" + selected.getDisplayName());
        }
    }

    private void startGame() {
        Arena selected = voteSession != null ? voteSession.getSelectedOrSelect() : lobbyArena;
        if (selected == null) {
            selected = lobbyArena;
        }

        if (selected == lobbyArena) {
            lobbyArena.setArenaSate(new ActiveArenaState(gameManager, lobbyArena));
        } else {
            lobbyArena.transferPlayersTo(selected);
            lobbyArena.setArenaSate(new WaitingArenaState(gameManager, lobbyArena));
            selected.setArenaSate(new ActiveArenaState(gameManager, selected));
        }

        gameManager.getArenaManager().resetVoteSession();
    }
}
