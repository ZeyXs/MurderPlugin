package fr.zeyx.murder.manager;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.state.ActiveArenaState;
import fr.zeyx.murder.game.Role;
import fr.zeyx.murder.util.ChatUtil;
import fr.zeyx.murder.util.MojangUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SecretIdentityManager implements Listener {

    private static final Pattern VALID_USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final int MURDERER_FOOD_LEVEL = 8;
    private static final int NON_MURDERER_FOOD_LEVEL = 6;
    private static final ChatColor[] IDENTITY_COLORS = new ChatColor[] {
            ChatColor.DARK_BLUE,
            ChatColor.DARK_GREEN,
            ChatColor.DARK_AQUA,
            ChatColor.DARK_RED,
            ChatColor.DARK_PURPLE,
            ChatColor.GOLD,
            ChatColor.DARK_GRAY,
            ChatColor.BLUE,
            ChatColor.GREEN,
            ChatColor.AQUA,
            ChatColor.RED,
            ChatColor.LIGHT_PURPLE,
            ChatColor.YELLOW
    };
    private static final ChatColor DEFAULT_IDENTITY_COLOR = ChatColor.YELLOW;

    private final ConfigurationManager configurationManager;
    private final ArenaManager arenaManager;
    private final Map<UUID, Component> originalListNames = new HashMap<>();
    private final Map<UUID, PlayerProfile> originalProfiles = new HashMap<>();
    private final Map<UUID, Integer> requestVersions = new ConcurrentHashMap<>();
    private final Map<UUID, String> currentIdentities = new ConcurrentHashMap<>();
    private final Map<UUID, ChatColor> currentIdentityColors = new ConcurrentHashMap<>();

    public SecretIdentityManager(ConfigurationManager configurationManager, ArenaManager arenaManager) {
        this.configurationManager = configurationManager;
        this.arenaManager = arenaManager;
        warnInvalidUsernames();
    }

    public String applyRandomIdentity(Player player) {
        List<String> names = configurationManager.getSecretIdentityNames();
        if (names.isEmpty()) {
            return null;
        }
        ensureIdentityColor(player.getUniqueId());
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
        assignIdentityColors(players);
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
        currentIdentityColors.remove(player.getUniqueId());
        requestVersions.remove(player.getUniqueId());
        if (original == null && originalProfile == null) {
            return false;
        }
        if (player.isOnline()) {
            int foodLevel = player.getFoodLevel();
            float saturation = player.getSaturation();
            float exhaustion = player.getExhaustion();
            if (originalProfile != null) {
                player.setPlayerProfile(originalProfile);
                scheduleHungerRestore(player, foodLevel, saturation, exhaustion);
            }
            if (original != null) {
                player.playerListName(original);
            }
        }
        return true;
    }

    private boolean applyIdentity(Player player, String username) {
        if (!isValidUsername(username)) {
            MurderPlugin.getInstance().getLogger()
                    .warning("Secret identity '" + username + "' is not a valid Minecraft username.");
            return false;
        }

        UUID playerId = player.getUniqueId();
        int requestVersion = bumpRequestVersion(playerId);
        ensureIdentityColor(playerId);
        originalListNames.putIfAbsent(player.getUniqueId(), player.playerListName() == null ? Component.text(player.getName()) : player.playerListName());
        originalProfiles.putIfAbsent(player.getUniqueId(), player.getPlayerProfile().clone());
        currentIdentities.put(playerId, username);
        player.playerListName(ChatUtil.component(colorizeName(playerId, username)));

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
                    currentIdentities.remove(player.getUniqueId());
                    return;
                }
                if (textures == null || !textures.hasValue()) {
                    MurderPlugin.getInstance().getLogger()
                            .warning("Secret identity '" + username + "' is not a valid Minecraft account.");
                    currentIdentities.remove(player.getUniqueId());
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

    public String getCurrentIdentityName(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return currentIdentities.get(playerId);
    }

    public ChatColor getCurrentIdentityColor(UUID playerId) {
        if (playerId == null) {
            return DEFAULT_IDENTITY_COLOR;
        }
        return resolveColor(playerId);
    }

    public String getCurrentIdentityDisplayName(UUID playerId) {
        String identity = getCurrentIdentityName(playerId);
        if (identity == null || identity.isBlank()) {
            return null;
        }
        return colorizeName(playerId, identity);
    }

    public String getColoredName(Player player) {
        if (player == null) {
            return null;
        }
        String identity = getCurrentIdentityName(player.getUniqueId());
        String baseName = (identity == null || identity.isBlank()) ? player.getName() : identity;
        return colorizeName(player.getUniqueId(), baseName);
    }

    public Color getCurrentIdentityLeatherColor(UUID playerId) {
        return resolveLeatherColor(resolveColor(playerId));
    }

    public String colorizeIdentityMentions(Iterable<UUID> playerIds, String message, ChatColor trailingColor) {
        if (playerIds == null || message == null || message.isBlank()) {
            return message;
        }
        List<UUID> sorted = new ArrayList<>();
        for (UUID playerId : playerIds) {
            if (playerId != null) {
                sorted.add(playerId);
            }
        }
        sorted.sort((first, second) -> {
            String firstIdentity = currentIdentities.get(first);
            String secondIdentity = currentIdentities.get(second);
            int firstLength = firstIdentity == null ? 0 : firstIdentity.length();
            int secondLength = secondIdentity == null ? 0 : secondIdentity.length();
            return Integer.compare(secondLength, firstLength);
        });

        String result = message;
        String suffix = (trailingColor == null ? ChatColor.RESET : trailingColor).toString();
        for (UUID playerId : sorted) {
            String identity = currentIdentities.get(playerId);
            if (identity == null || identity.isBlank()) {
                continue;
            }
            String prefix = resolveColor(playerId).toString();
            Pattern mentionPattern = Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(identity) + "(?![A-Za-z0-9_])");
            Matcher matcher = mentionPattern.matcher(result);
            StringBuffer buffer = new StringBuffer();
            boolean changed = false;
            while (matcher.find()) {
                changed = true;
                String replacement = prefix + matcher.group() + suffix;
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            }
            if (changed) {
                matcher.appendTail(buffer);
                result = buffer.toString();
            }
        }
        return result;
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
            int foodLevel = player.getFoodLevel();
            float saturation = player.getSaturation();
            float exhaustion = player.getExhaustion();
            player.setPlayerProfile(targetProfile);
            scheduleHungerRestore(player, foodLevel, saturation, exhaustion);
            player.playerListName(ChatUtil.component(colorizeName(player.getUniqueId(), username)));
            currentIdentities.put(player.getUniqueId(), username);
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
            int foodLevel = player.getFoodLevel();
            float saturation = player.getSaturation();
            float exhaustion = player.getExhaustion();
            player.setPlayerProfile(targetProfile);
            scheduleHungerRestore(player, foodLevel, saturation, exhaustion);
            player.playerListName(ChatUtil.component(colorizeName(player.getUniqueId(), username)));
            currentIdentities.put(player.getUniqueId(), username);
        });
    }

    private void assignIdentityColors(List<Player> players) {
        if (players == null || players.isEmpty()) {
            return;
        }
        List<ChatColor> palette = new ArrayList<>(List.of(IDENTITY_COLORS));
        Collections.shuffle(palette);
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (player == null) {
                continue;
            }
            UUID playerId = player.getUniqueId();
            currentIdentityColors.put(playerId, palette.get(i % palette.size()));
            originalListNames.putIfAbsent(playerId, player.playerListName() == null ? Component.text(player.getName()) : player.playerListName());
            player.playerListName(ChatUtil.component(colorizeName(playerId, player.getName())));
        }
    }

    private void ensureIdentityColor(UUID playerId) {
        if (playerId == null || currentIdentityColors.containsKey(playerId)) {
            return;
        }
        Set<ChatColor> used = new HashSet<>(currentIdentityColors.values());
        for (ChatColor candidate : IDENTITY_COLORS) {
            if (!used.contains(candidate)) {
                currentIdentityColors.put(playerId, candidate);
                return;
            }
        }
        currentIdentityColors.put(playerId, IDENTITY_COLORS[currentIdentityColors.size() % IDENTITY_COLORS.length]);
    }

    private ChatColor resolveColor(UUID playerId) {
        ChatColor color = currentIdentityColors.get(playerId);
        return color == null ? DEFAULT_IDENTITY_COLOR : color;
    }

    private String colorizeName(UUID playerId, String name) {
        String baseName = name == null ? "" : name;
        return resolveColor(playerId) + baseName;
    }

    private Color resolveLeatherColor(ChatColor color) {
        if (color == null) {
            return Color.fromRGB(0xFFFF55);
        }
        return switch (color) {
            case DARK_BLUE -> Color.fromRGB(0x0000AA);
            case DARK_GREEN -> Color.fromRGB(0x00AA00);
            case DARK_AQUA -> Color.fromRGB(0x00AAAA);
            case DARK_RED -> Color.fromRGB(0xAA0000);
            case DARK_PURPLE -> Color.fromRGB(0xAA00AA);
            case GOLD -> Color.fromRGB(0xFFAA00);
            case GRAY -> Color.fromRGB(0xAAAAAA);
            case DARK_GRAY -> Color.fromRGB(0x555555);
            case BLUE -> Color.fromRGB(0x5555FF);
            case GREEN -> Color.fromRGB(0x55FF55);
            case AQUA -> Color.fromRGB(0x55FFFF);
            case RED -> Color.fromRGB(0xFF5555);
            case LIGHT_PURPLE -> Color.fromRGB(0xFF55FF);
            case YELLOW -> Color.fromRGB(0xFFFF55);
            default -> Color.fromRGB(0xFFFF55);
        };
    }

    private void scheduleHungerRestore(Player player, int foodLevel, float saturation, float exhaustion) {
        Bukkit.getScheduler().runTaskLater(MurderPlugin.getInstance(), task -> {
            if (!player.isOnline()) {
                return;
            }
            int expectedFoodLevel = resolveExpectedFoodLevel(player, foodLevel);
            player.setFoodLevel(expectedFoodLevel);
            if (expectedFoodLevel == MURDERER_FOOD_LEVEL || expectedFoodLevel == NON_MURDERER_FOOD_LEVEL) {
                player.setSaturation(0f);
                player.setExhaustion(0f);
                return;
            }
            player.setSaturation(saturation);
            player.setExhaustion(exhaustion);
        }, 1L);
    }

    private int resolveExpectedFoodLevel(Player player, int fallbackFoodLevel) {
        if (player == null || arenaManager == null) {
            return fallbackFoodLevel;
        }
        return arenaManager.getCurrentArena(player)
                .filter(arena -> arena.getArenaState() instanceof ActiveArenaState)
                .map(arena -> (ActiveArenaState) arena.getArenaState())
                .map(ActiveArenaState::getSession)
                .map(session -> session == null ? null : session.getRole(player.getUniqueId()))
                .map(role -> role == Role.MURDERER ? MURDERER_FOOD_LEVEL : NON_MURDERER_FOOD_LEVEL)
                .orElse(fallbackFoodLevel);
    }

}
