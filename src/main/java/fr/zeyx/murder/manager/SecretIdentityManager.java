package fr.zeyx.murder.manager;

import com.destroystokyo.paper.profile.PlayerProfile;
import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.util.ChatUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.profile.PlayerTextures;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class SecretIdentityManager implements Listener {

    private static final Pattern VALID_USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private final ConfigurationManager configurationManager;
    private final Map<UUID, Component> originalListNames = new HashMap<>();
    private final Map<UUID, PlayerProfile> originalProfiles = new HashMap<>();
    private final Map<UUID, Integer> requestVersions = new HashMap<>();
    private final Map<UUID, String> currentIdentities = new HashMap<>();

    public SecretIdentityManager(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
        warnInvalidUsernames();
    }

    public void applyRandomIdentity(Player player) {
        List<String> names = configurationManager.getSecretIdentityNames();
        if (names.isEmpty()) {
            player.sendMessage(ChatUtil.prefixedComponent("&cNo secret identities configured."));
            return;
        }
        String username = pickRandomIdentity(player.getUniqueId(), names);
        if (username == null) {
            player.sendMessage(ChatUtil.prefixedComponent("&cNo alternative identity available."));
            return;
        }
        applyIdentity(player, username);
    }

    public void resetIdentity(Player player) {
        bumpRequestVersion(player.getUniqueId());
        Component original = originalListNames.remove(player.getUniqueId());
        PlayerProfile originalProfile = originalProfiles.remove(player.getUniqueId());
        currentIdentities.remove(player.getUniqueId());
        if (original == null && originalProfile == null) {
            player.sendMessage(ChatUtil.prefixedComponent("&cYou don't have a secret identity to reset."));
            return;
        }
        if (originalProfile != null) {
            player.setPlayerProfile(originalProfile);
        }
        if (original != null) {
            player.playerListName(original);
        }
        player.sendMessage(ChatUtil.prefixedComponent("&aYour identity has been reset."));
    }

    private void applyIdentity(Player player, String username) {
        if (!isValidUsername(username)) {
            MurderPlugin.getInstance().getLogger()
                    .warning("Secret identity '" + username + "' is not a valid Minecraft username.");
            player.sendMessage(ChatUtil.prefixedComponent("&cThat identity is not a valid Minecraft username."));
            return;
        }

        int requestVersion = bumpRequestVersion(player.getUniqueId());
        originalListNames.putIfAbsent(player.getUniqueId(), player.playerListName() == null ? Component.text(player.getName()) : player.playerListName());
        originalProfiles.putIfAbsent(player.getUniqueId(), player.getPlayerProfile().clone());

        PlayerProfile lookup = Bukkit.createProfile(username);
        lookup.update().whenComplete((resolved, throwable) -> {
            if (throwable != null) {
                MurderPlugin.getInstance().getLogger().warning(
                        "Failed to resolve secret identity '" + username + "': " + throwable.getMessage());
                Bukkit.getScheduler().runTask(MurderPlugin.getInstance(), () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    player.sendMessage(ChatUtil.prefixedComponent("&cFailed to apply that identity."));
                });
                return;
            }

            if (!Objects.equals(requestVersions.get(player.getUniqueId()), requestVersion)) {
                return;
            }

            PlayerProfile resolvedProfile = resolved == null ? lookup : resolved;
            if (!resolvedProfile.isComplete() || !resolvedProfile.hasTextures()) {
                MurderPlugin.getInstance().getLogger()
                        .warning("Secret identity '" + username + "' is not a valid Minecraft account.");
                Bukkit.getScheduler().runTask(MurderPlugin.getInstance(), () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    player.sendMessage(ChatUtil.prefixedComponent("&cThat identity could not be resolved."));
                });
                return;
            }

            Bukkit.getScheduler().runTask(MurderPlugin.getInstance(), () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (!Objects.equals(requestVersions.get(player.getUniqueId()), requestVersion)) {
                    return;
                }
                PlayerProfile targetProfile = Bukkit.createProfile(player.getUniqueId(), player.getName());
                PlayerTextures targetTextures = targetProfile.getTextures();
                PlayerTextures sourceTextures = resolvedProfile.getTextures();
                if (sourceTextures.getSkin() != null) {
                    targetTextures.setSkin(sourceTextures.getSkin(), sourceTextures.getSkinModel());
                }
                if (sourceTextures.getCape() != null) {
                    targetTextures.setCape(sourceTextures.getCape());
                }
                targetProfile.setTextures(targetTextures);

                player.setPlayerProfile(targetProfile);
                player.playerListName(ChatUtil.component(username));
                currentIdentities.put(player.getUniqueId(), username.toLowerCase());
                player.sendMessage(ChatUtil.prefixedComponent("&7Your identity is now &a" + username));
            });
        });
    }

    private void warnInvalidUsernames() {
        for (String name : configurationManager.getSecretIdentityNames()) {
            if (!isValidUsername(name)) {
                MurderPlugin.getInstance().getLogger()
                        .warning("Secret identity '" + name + "' is not a valid Minecraft username.");
            }
        }
    }

    private boolean isValidUsername(String username) {
        return username != null && VALID_USERNAME.matcher(username).matches();
    }

    private int bumpRequestVersion(UUID playerId) {
        Integer current = requestVersions.get(playerId);
        int next = current == null ? 1 : current + 1;
        requestVersions.put(playerId, next);
        return next;
    }

    private String pickRandomIdentity(UUID playerId, List<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        String current = currentIdentities.get(playerId);
        int maxAttempts = Math.max(10, names.size() * 2);
        for (int i = 0; i < maxAttempts; i++) {
            String candidate = names.get(ThreadLocalRandom.current().nextInt(names.size()));
            if (candidate == null) {
                continue;
            }
            String trimmed = candidate.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (current == null || !trimmed.equalsIgnoreCase(current)) {
                return trimmed;
            }
        }
        for (String candidate : names) {
            if (candidate == null) {
                continue;
            }
            String trimmed = candidate.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (current == null || !trimmed.equalsIgnoreCase(current)) {
                return trimmed;
            }
        }
        return null;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        originalListNames.remove(event.getPlayer().getUniqueId());
        originalProfiles.remove(event.getPlayer().getUniqueId());
        requestVersions.remove(event.getPlayer().getUniqueId());
        currentIdentities.remove(event.getPlayer().getUniqueId());
    }
}
