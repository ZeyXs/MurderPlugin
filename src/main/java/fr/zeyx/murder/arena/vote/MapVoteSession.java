package fr.zeyx.murder.arena.vote;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class MapVoteSession {

    private final List<Arena> candidates;
    private final Map<UUID, Arena> votes = new HashMap<>();
    private boolean locked;
    private Arena selectedArena;

    public MapVoteSession(List<Arena> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            this.candidates = Collections.emptyList();
        } else {
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        }
    }

    public List<Arena> getCandidates() {
        return candidates;
    }

    public boolean isLocked() {
        return locked;
    }

    public Arena getSelectedArena() {
        return selectedArena;
    }

    public Arena getVote(UUID playerId) {
        return votes.get(playerId);
    }

    public void removeVote(UUID playerId) {
        votes.remove(playerId);
    }

    public Arena findCandidate(String name) {
        if (name == null) {
            return null;
        }
        for (Arena arena : candidates) {
            if (arena.getName().equalsIgnoreCase(name)) {
                return arena;
            }
        }
        return null;
    }

    public boolean setVote(UUID playerId, Arena arena) {
        if (locked || arena == null || !candidates.contains(arena)) {
            return false;
        }
        votes.put(playerId, arena);
        return true;
    }

    public int getVoteCount(Arena arena) {
        if (arena == null) {
            return 0;
        }
        int count = 0;
        for (Arena voted : votes.values()) {
            if (arena.equals(voted)) {
                count++;
            }
        }
        return count;
    }

    public Arena lockAndSelect() {
        if (selectedArena == null) {
            selectedArena = selectWinner();
        }
        locked = true;
        return selectedArena;
    }

    public Arena getSelectedOrSelect() {
        if (selectedArena == null) {
            selectedArena = selectWinner();
        }
        return selectedArena;
    }

    public void sendVotePrompt(Player player) {
        if (player == null) {
            return;
        }
        if (candidates.isEmpty()) {
            player.sendMessage(TextUtil.prefixed("&cNo maps are available to vote."));
            return;
        }
        if (locked && selectedArena != null) {
            player.sendMessage(TextUtil.prefixed("&7You are now playing on &a" + selectedArena.getDisplayName()));
            return;
        }

        player.sendMessage(TextUtil.prefixed("&7If you notice a bug or have a suggestion, post it on the site at &bserver.net"));
        player.sendMessage(TextUtil.prefixed("&6Vote for the next map! &9(click to vote)"));
        for (Arena arena : candidates) {
            Component map = TextUtil.prefixed("&a" + arena.getDisplayName())
                    .clickEvent(ClickEvent.runCommand("/murder vote " + arena.getName()))
                    .hoverEvent(HoverEvent.showText(TextUtil.component("&fVote for &a" + arena.getDisplayName())));
            player.sendMessage(map);
        }

    }

    private Arena selectWinner() {
        if (candidates.isEmpty()) {
            return null;
        }
        int bestVotes = -1;
        List<Arena> best = new ArrayList<>();
        for (Arena arena : candidates) {
            int count = getVoteCount(arena);
            if (count > bestVotes) {
                bestVotes = count;
                best.clear();
                best.add(arena);
            } else if (count == bestVotes) {
                best.add(arena);
            }
        }
        if (bestVotes <= 0) {
            best = new ArrayList<>(candidates);
        }
        return best.get(ThreadLocalRandom.current().nextInt(best.size()));
    }

}
