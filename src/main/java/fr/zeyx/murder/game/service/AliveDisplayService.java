package fr.zeyx.murder.game.service;

import fr.zeyx.murder.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AliveDisplayService {

    private final Arena arena;
    private final IdentityService identityService;

    public AliveDisplayService(Arena arena, IdentityService identityService) {
        this.arena = arena;
        this.identityService = identityService;
    }

    public void updateAliveCountDisplays(List<UUID> alivePlayers, UUID murdererId) {
        int aliveNonMurdererCount = countAliveNonMurderers(alivePlayers, murdererId);
        Set<UUID> alive = new HashSet<>(alivePlayers);
        for (UUID playerId : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            if (!alive.contains(playerId)) {
                player.setLevel(0);
                player.setExp(0.0f);
                continue;
            }
            if (playerId.equals(murdererId)) {
                player.setLevel(aliveNonMurdererCount);
                player.setExp(1.0f);
                continue;
            }
            player.setLevel(0);
        }
    }

    public void updateChatCompletionsForActivePlayers() {
        List<String> identityCompletions = identityService.collectIdentityCompletions(arena.getActivePlayers());
        for (UUID playerId : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            player.setCustomChatCompletions(identityCompletions);
        }
    }

    public void clearChatCompletions(Player player) {
        if (player == null) {
            return;
        }
        player.setCustomChatCompletions(List.of());
    }

    private int countAliveNonMurderers(List<UUID> alivePlayers, UUID murdererId) {
        int count = 0;
        for (UUID playerId : alivePlayers) {
            if (!playerId.equals(murdererId)) {
                count++;
            }
        }
        return count;
    }
}
