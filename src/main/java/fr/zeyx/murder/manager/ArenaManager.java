package fr.zeyx.murder.manager;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.vote.MapVoteSession;
import fr.zeyx.murder.command.CommandArgs;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ArenaManager {

    private List<Arena> arenaList = new ArrayList<>();
    private MapVoteSession voteSession;

    public ArenaManager(List<Arena> arenas) {
        this.arenaList = arenas;
    }

    public List<Arena> getArenas() {
        return arenaList;
    }

    public MapVoteSession getVoteSession() {
        return voteSession;
    }

    public MapVoteSession getOrCreateVoteSession() {
        if (voteSession == null) {
            voteSession = createVoteSession();
        }
        return voteSession;
    }

    public void resetVoteSession() {
        voteSession = null;
    }

    public void addArena(Arena arena) {
        if (arena == null) {
            return;
        }
        arenaList.add(arena);
    }

    public void removeArena(Arena arena) {
        if (arena == null) {
            return;
        }
        removeArena(arena.getName());
    }

    public void removeArena(String arenaName) {
        if (arenaName == null || arenaName.isBlank()) {
            return;
        }
        this.arenaList.removeIf(existing ->
                existing != null && existing.getName() != null && existing.getName().equalsIgnoreCase(arenaName)
        );
    }

    public void upsertArena(String previousName, Arena arena) {
        if (arena == null || arena.getName() == null || arena.getName().isBlank()) {
            return;
        }
        if (previousName != null && !previousName.isBlank()) {
            removeArena(previousName);
        }
        removeArena(arena.getName());
        addArena(arena);
    }

    public Optional<Arena> findArena(String configName) {
        if (configName == null || configName.isBlank()) {
            return Optional.empty();
        }
        return arenaList.stream()
                .filter(arena -> arena != null && arena.getName() != null && arena.getName().equalsIgnoreCase(configName))
                .findAny();
    }

    public Optional<Arena> findArena(String[] commandArgs) {
        String arenaName = CommandArgs.joinArgs(commandArgs, 1);
        if (arenaName.isBlank()) {
            arenaName = CommandArgs.joinArgs(commandArgs, 0);
        }
        return findArena(arenaName);
    }

    public Optional<Arena> getCurrentArena(Player player) {
        return arenaList.stream().filter(arena -> arena.isPlaying(player)).findAny();
    }

    private MapVoteSession createVoteSession() {
        if (arenaList.isEmpty()) {
            return new MapVoteSession(Collections.emptyList());
        }
        List<Arena> candidates = new ArrayList<>(arenaList);
        Collections.shuffle(candidates);
        if (candidates.size() > 3) {
            candidates = candidates.subList(0, 3);
        }
        return new MapVoteSession(candidates);
    }
}
