package fr.zeyx.murder.arena.state;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.task.ArenaStartingTask;
import fr.zeyx.murder.manager.GameManager;

public class StartingArenaState extends WaitingArenaState {

    private final GameManager gameManager;
    private final Arena arena;
    private ArenaStartingTask arenaStartingTask;

    public StartingArenaState(GameManager gameManager, Arena arena) {
        super(gameManager, arena);
        this.gameManager = gameManager;
        this.arena = arena;
    }

    @Override
    public void onEnable() {
        super.onEnable();

        arenaStartingTask = new ArenaStartingTask(arena, () -> {
            arena.setArenaSate(new ActiveArenaState(gameManager, arena));
        }, 10);
        arenaStartingTask.runTaskTimer(MurderPlugin.getInstance(), 0, 20);
    }

    public ArenaStartingTask getArenaStartingTask() {
        return arenaStartingTask;
    }

}
