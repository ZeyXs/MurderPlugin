package fr.zeyx.murder.game.session;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.game.Role;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public class SessionEndGameMessenger {

    private final Arena arena;
    private final List<UUID> alivePlayers;
    private final Map<UUID, Role> roles;
    private final Function<UUID, String> identityDisplayNameResolver;
    private final Function<UUID, String> realPlayerNameResolver;

    public SessionEndGameMessenger(Arena arena,
                                   List<UUID> alivePlayers,
                                   Map<UUID, Role> roles,
                                   Function<UUID, String> identityDisplayNameResolver,
                                   Function<UUID, String> realPlayerNameResolver) {
        this.arena = arena;
        this.alivePlayers = alivePlayers;
        this.roles = roles;
        this.identityDisplayNameResolver = identityDisplayNameResolver;
        this.realPlayerNameResolver = realPlayerNameResolver;
    }

    public void sendRoleRevealMessages() {
        List<UUID> orderedPlayers = new ArrayList<>(arena.getActivePlayers());
        orderedPlayers.sort(Comparator.comparingInt(this::rolePriority));

        for (UUID playerId : orderedPlayers) {
            Role role = roles.get(playerId);
            if (role == null) {
                continue;
            }
            String identityName = identityDisplayNameResolver.apply(playerId);
            String realPlayerName = realPlayerNameResolver.apply(playerId);
            boolean dead = !alivePlayers.contains(playerId);
            arena.sendArenaMessage(identityName + " &f» &7" + realPlayerName + " " + formatRoleToken(role, dead));
        }
    }

    public void sendWinnerMessage(UUID murdererId, UUID murdererKillerId) {
        if (hasMurdererWon(murdererId)) {
            arena.sendArenaMessage("&7The &cmurderer &7has killed everyone!");
            return;
        }
        arena.sendArenaMessage("&7The &cmurderer &7has been killed by "
                + resolveMurdererKillerIdentityName(murdererId, murdererKillerId) + "&7!");
    }

    private boolean hasMurdererWon(UUID murdererId) {
        if (murdererId == null || !alivePlayers.contains(murdererId)) {
            return false;
        }
        for (UUID playerId : alivePlayers) {
            if (!playerId.equals(murdererId)) {
                return false;
            }
        }
        return true;
    }

    private String resolveMurdererKillerIdentityName(UUID murdererId, UUID murdererKillerId) {
        if (murdererKillerId != null) {
            String displayName = identityDisplayNameResolver.apply(murdererKillerId);
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
        }
        for (UUID playerId : alivePlayers) {
            if (playerId.equals(murdererId)) {
                continue;
            }
            String displayName = identityDisplayNameResolver.apply(playerId);
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
        }
        return "&fUnknown";
    }

    private int rolePriority(UUID playerId) {
        Role role = roles.get(playerId);
        if (role == null) {
            return 3;
        }
        return switch (role) {
            case BYSTANDER -> 0;
            case DETECTIVE -> 1;
            case MURDERER -> 2;
        };
    }

    private String formatRoleToken(Role role, boolean dead) {
        String strike = dead ? "&m" : "";
        return switch (role) {
            case BYSTANDER -> "&a" + strike + "(bystander)";
            case DETECTIVE -> "&d" + strike + "(detective)";
            case MURDERER -> "&c" + strike + "(murderer)";
        };
    }
}
