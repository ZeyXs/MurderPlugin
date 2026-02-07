package fr.zeyx.murder.game.session;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.manager.SecretIdentityManager;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChatTabCompleteEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class SessionTabCompletionService {

    private final SecretIdentityManager secretIdentityManager;

    public SessionTabCompletionService(SecretIdentityManager secretIdentityManager) {
        this.secretIdentityManager = secretIdentityManager;
    }

    public void handlePlayerChatTabComplete(PlayerChatTabCompleteEvent event, Arena arena) {
        if (event == null || arena == null) {
            return;
        }
        List<String> suggestions = buildIdentityCompletions(arena, event.getLastToken());
        event.getTabCompletions().clear();
        event.getTabCompletions().addAll(suggestions);
    }

    public void handleAsyncTabComplete(AsyncTabCompleteEvent event, Arena arena) {
        if (event == null || arena == null) {
            return;
        }
        event.setCompletions(buildIdentityCompletions(arena, extractLastToken(event.getBuffer())));
        event.setHandled(true);
    }

    private List<String> buildIdentityCompletions(Arena arena, String token) {
        String normalizedPrefix = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        for (UUID playerId : new ArrayList<>(arena.getActivePlayers())) {
            String identity = secretIdentityManager.getCurrentIdentityName(playerId);
            if (identity == null || identity.isBlank()) {
                continue;
            }
            if (normalizedPrefix.isBlank() || identity.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                suggestions.add(identity);
            }
        }
        return new ArrayList<>(suggestions);
    }

    private String extractLastToken(String buffer) {
        if (buffer == null || buffer.isBlank()) {
            return "";
        }
        int index = buffer.lastIndexOf(' ');
        if (index < 0 || index + 1 >= buffer.length()) {
            return buffer;
        }
        return buffer.substring(index + 1);
    }
}
