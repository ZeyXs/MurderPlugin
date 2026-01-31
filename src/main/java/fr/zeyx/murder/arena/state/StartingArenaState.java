package fr.zeyx.murder.arena.state;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.task.ArenaStartingTask;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.manager.MapVoteSession;

public class StartingArenaState extends WaitingArenaState {

    private final GameManager gameManager;
    private final Arena arena;
    private ArenaStartingTask arenaStartingTask;
    private final boolean forceStart;

    public StartingArenaState(GameManager gameManager, Arena arena) {
        this(gameManager, arena, false);
    }

    public StartingArenaState(GameManager gameManager, Arena arena, boolean forceStart) {
        super(gameManager, arena);
        this.gameManager = gameManager;
        this.arena = arena;
        this.forceStart = forceStart;
    }

    @Override
    public void onEnable() {
        super.onEnable();

        MapVoteSession voteSession = gameManager.getArenaManager().getOrCreateVoteSession();
        arenaStartingTask = new ArenaStartingTask(gameManager, arena, voteSession, 20, forceStart);
        arenaStartingTask.runTaskTimer(MurderPlugin.getInstance(), 0, 20);
    }

    public ArenaStartingTask getArenaStartingTask() {
        return arenaStartingTask;
    }

    public boolean isForceStart() {
        return forceStart;
    }

}
