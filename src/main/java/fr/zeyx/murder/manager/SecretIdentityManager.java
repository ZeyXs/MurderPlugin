package fr.zeyx.murder.manager;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.util.ChatUtil;
import fr.zeyx.murder.util.MojangUtil;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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

    public String applyRandomIdentity(Player player) {
        List<String> names = configurationManager.getSecretIdentityNames();
        if (names.isEmpty()) {
            return null;
        }
        String username = pickRandomIdentity(player.getUniqueId(), names);
        if (username == null) {
            return null;
        }
        return applyIdentity(player, username) ? username : null;
    }

    public boolean applyUniqueIdentities(List<Player> players) {
        if (players == null || players.isEmpty()) {
            return true;
        }
        List<String> configured = configurationManager.getSecretIdentityNames();
        List<String> validNames = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String name : configured) {
            if (isValidUsername(name)) {
                String trimmed = name.trim();
                String key = trimmed.toLowerCase();
                if (seen.add(key)) {
                    validNames.add(trimmed);
                }
            } else if (name != null && !name.trim().isEmpty()) {
                MurderPlugin.getInstance().getLogger()
                        .warning("Secret identity '" + name + "' is not a valid Minecraft username.");
            }
        }
        if (validNames.size() < players.size()) {
            MurderPlugin.getInstance().getLogger()
                    .warning("Not enough secret identities configured. Need " + players.size()
                            + " but found " + validNames.size() + ".");
            return false;
        }
        Collections.shuffle(validNames);
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (player != null) {
                applyIdentity(player, validNames.get(i));
            }
        }
        return true;
    }

    public boolean resetIdentity(Player player) {
        bumpRequestVersion(player.getUniqueId());
        Component original = originalListNames.remove(player.getUniqueId());
        PlayerProfile originalProfile = originalProfiles.remove(player.getUniqueId());
        currentIdentities.remove(player.getUniqueId());
        requestVersions.remove(player.getUniqueId());
        if (original == null && originalProfile == null) {
            return false;
        }
        if (player.isOnline()) {
            if (originalProfile != null) {
                player.setPlayerProfile(originalProfile);
            }
            if (original != null) {
                player.playerListName(original);
            }
            refreshPlayerAppearance(player);
        }
        return true;
    }

    private boolean applyIdentity(Player player, String username) {
        if (!isValidUsername(username)) {
            MurderPlugin.getInstance().getLogger()
                    .warning("Secret identity '" + username + "' is not a valid Minecraft username.");
            return false;
        }

        int requestVersion = bumpRequestVersion(player.getUniqueId());
        originalListNames.putIfAbsent(player.getUniqueId(), player.playerListName() == null ? Component.text(player.getName()) : player.playerListName());
        originalProfiles.putIfAbsent(player.getUniqueId(), player.getPlayerProfile().clone());

        PlayerProfile lookup = Bukkit.createProfile(username);
        lookup.update().whenComplete((resolved, throwable) -> {
            if (!Objects.equals(requestVersions.get(player.getUniqueId()), requestVersion)) {
                return;
            }

            if (throwable == null) {
                PlayerProfile resolvedProfile = resolved == null ? lookup : resolved;
                if (resolvedProfile.isComplete() && resolvedProfile.hasTextures()) {
                    applyResolvedIdentity(player, username, requestVersion, resolvedProfile.getTextures());
                    return;
                }
            }

            MojangUtil.resolveTextures(username).whenComplete((textures, error) -> {
                if (!Objects.equals(requestVersions.get(player.getUniqueId()), requestVersion)) {
                    return;
                }
                if (error != null) {
                    MurderPlugin.getInstance().getLogger()
                            .warning("Failed to resolve secret identity '" + username + "' via Mojang API: " + error.getMessage());
                    return;
                }
                if (textures == null || !textures.hasValue()) {
                    MurderPlugin.getInstance().getLogger()
                            .warning("Secret identity '" + username + "' is not a valid Minecraft account.");
                    return;
                }
                applyResolvedIdentity(player, username, requestVersion, textures);
            });
        });
        return true;
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
        resetIdentity(event.getPlayer());
    }

    private void applyResolvedIdentity(Player player, String username, int requestVersion, PlayerTextures sourceTextures) {
        Bukkit.getScheduler().runTask(MurderPlugin.getInstance(), () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!Objects.equals(requestVersions.get(player.getUniqueId()), requestVersion)) {
                return;
            }
            PlayerProfile targetProfile = player.getPlayerProfile().clone();
            PlayerTextures targetTextures = targetProfile.getTextures();
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
            refreshPlayerAppearance(player);
        });
    }

    private void applyResolvedIdentity(Player player, String username, int requestVersion, MojangUtil.MojangTextures textures) {
        Bukkit.getScheduler().runTask(MurderPlugin.getInstance(), () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!Objects.equals(requestVersions.get(player.getUniqueId()), requestVersion)) {
                return;
            }
            PlayerProfile targetProfile = player.getPlayerProfile().clone();
            targetProfile.setProperty(new ProfileProperty("textures", textures.getValue(), textures.getSignature()));

            player.setPlayerProfile(targetProfile);
            player.playerListName(ChatUtil.component(username));
            currentIdentities.put(player.getUniqueId(), username.toLowerCase());
            refreshPlayerAppearance(player);
        });
    }

    private void refreshPlayerAppearance(Player player) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            viewer.hidePlayer(MurderPlugin.getInstance(), player);
            viewer.showPlayer(MurderPlugin.getInstance(), player);
        }
    }

}
