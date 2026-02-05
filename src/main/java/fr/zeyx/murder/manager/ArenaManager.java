package fr.zeyx.murder.manager;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.vote.MapVoteSession;
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
        arenaList.add(arena);
    }

    public void removeArena(Arena arena) {
        this.arenaList.removeIf(existing -> existing.equals(arena));
    }

    public Optional<Arena> findArena(String configName) {
        return arenaList.stream().filter(arena -> arena.getName().equalsIgnoreCase(configName)).findAny();
    }

    public Optional<Arena> findArena(String[] commandArgs) {
        StringBuilder name = new StringBuilder();

        int index = 0;
        for (String arg : commandArgs) {
            if (arg.equalsIgnoreCase("edit")) continue;
            name.append(arg);
            if (index == commandArgs.length - 3) {
                name.append("_");
            }
            index++;
        }
        return findArena(name.toString());
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
