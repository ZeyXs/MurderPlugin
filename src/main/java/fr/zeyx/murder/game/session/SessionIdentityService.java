package fr.zeyx.murder.game.session;

import fr.zeyx.murder.manager.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public class SessionIdentityService {

    private final GameManager gameManager;

    public SessionIdentityService(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public String resolveIdentityDisplayName(UUID playerId) {
        String identityName = gameManager.getSecretIdentityManager().getCurrentIdentityDisplayName(playerId);
        if (identityName != null && !identityName.isBlank()) {
            return identityName;
        }
        Player onlinePlayer = Bukkit.getPlayer(playerId);
        if (onlinePlayer != null) {
            String colored = gameManager.getSecretIdentityManager().getColoredName(onlinePlayer);
            if (colored != null && !colored.isBlank()) {
                return colored;
            }
            return onlinePlayer.getName();
        }
        String offlineName = Bukkit.getOfflinePlayer(playerId).getName();
        return offlineName == null ? playerId.toString() : "&f" + offlineName;
    }

    public String resolveRealPlayerName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        String offlineName = Bukkit.getOfflinePlayer(playerId).getName();
        return offlineName == null ? playerId.toString() : offlineName;
    }

    public String resolveChatName(Player player) {
        String displayName = gameManager.getSecretIdentityManager().getColoredName(player);
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return player.getName();
    }

    public List<String> collectIdentityCompletions(Iterable<UUID> playerIds) {
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        for (UUID playerId : playerIds) {
            String identity = gameManager.getSecretIdentityManager().getCurrentIdentityName(playerId);
            if (identity != null && !identity.isBlank()) {
                identities.add(identity);
            }
        }
        return new ArrayList<>(identities);
    }
}
