package fr.zeyx.murder.manager;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.state.ActiveArenaState;
import fr.zeyx.murder.game.GameSession;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
    private final Map<UUID, PlayerProfile> currentIdentityProfiles = new ConcurrentHashMap<>();
    private final Map<String, ProfileProperty> identityTextureCache = new ConcurrentHashMap<>();
    private final Set<String> identitiesBeingCached = ConcurrentHashMap.newKeySet();

    public SecretIdentityManager(ConfigurationManager configurationManager, ArenaManager arenaManager) {
        this.configurationManager = configurationManager;
        this.arenaManager = arenaManager;
        warnInvalidUsernames();
        warmIdentityCache(configurationManager.getSecretIdentityNames(), false, 0L);
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
        warmIdentityCache(validNames, true, 6000L);
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
        currentIdentityProfiles.remove(player.getUniqueId());
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

    public boolean applySpecificIdentityFromCache(Player player, String identityName, ChatColor identityColor) {
        if (player == null || identityName == null || identityName.isBlank()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (identityColor != null) {
            currentIdentityColors.put(playerId, identityColor);
        } else {
            ensureIdentityColor(playerId);
        }
        currentIdentityProfiles.remove(playerId);
        warmIdentityCache(List.of(identityName), true, 3000L);
        int requestVersion = bumpRequestVersion(playerId);
        originalListNames.putIfAbsent(playerId, player.playerListName() == null ? Component.text(player.getName()) : player.playerListName());
        originalProfiles.putIfAbsent(playerId, player.getPlayerProfile().clone());
        currentIdentities.put(playerId, identityName);
        player.playerListName(ChatUtil.component(formatTabListName(playerId, identityName)));
        return applyIdentityFromCache(player, identityName, requestVersion);
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
        currentIdentityProfiles.remove(playerId);
        player.playerListName(ChatUtil.component(formatTabListName(playerId, username)));

        if (applyIdentityFromCache(player, username, requestVersion)) {
            return true;
        }

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
                    currentIdentityProfiles.remove(player.getUniqueId());
                    return;
                }
                if (textures == null || !textures.hasValue()) {
                    MurderPlugin.getInstance().getLogger()
                            .warning("Secret identity '" + username + "' is not a valid Minecraft account.");
                    currentIdentities.remove(player.getUniqueId());
                    currentIdentityProfiles.remove(player.getUniqueId());
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

    public PlayerProfile getCurrentIdentityProfile(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        PlayerProfile profile = currentIdentityProfiles.get(playerId);
        return profile == null ? null : profile.clone();
    }

    public void cacheIdentityProfile(String identityName, PlayerProfile profile) {
        String key = normalizeIdentityKey(identityName);
        if (key == null || profile == null) {
            return;
        }
        ProfileProperty textureProperty = extractTextureProperty(profile);
        if (textureProperty != null) {
            identityTextureCache.put(key, cloneTextureProperty(textureProperty));
        }
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
        PlayerProfile targetProfile = buildProfileFromTextures(player.getUniqueId(), username, sourceTextures);
        if (targetProfile == null) {
            return;
        }
        applyPreparedProfile(player, username, requestVersion, targetProfile);
    }

    private void applyResolvedIdentity(Player player, String username, int requestVersion, MojangUtil.MojangTextures textures) {
        if (textures == null || !textures.hasValue()) {
            return;
        }
        ProfileProperty textureProperty = new ProfileProperty("textures", textures.getValue(), textures.getSignature());
        String key = normalizeIdentityKey(username);
        if (key != null) {
            identityTextureCache.put(key, cloneTextureProperty(textureProperty));
        }
        PlayerProfile targetProfile = buildProfileFromTextureProperty(
                player.getUniqueId(),
                username,
                textureProperty
        );
        applyPreparedProfile(player, username, requestVersion, targetProfile);
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
            player.playerListName(ChatUtil.component(formatTabListName(playerId, player.getName())));
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

    private String formatTabListName(UUID playerId, String baseName) {
        return colorizeName(playerId, baseName);
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
        MurderPlugin plugin = MurderPlugin.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            applyHungerRestoreNow(player, foodLevel, saturation, exhaustion);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task -> applyHungerRestoreNow(player, foodLevel, saturation, exhaustion), 1L);
    }

    private void applyHungerRestoreNow(Player player, int foodLevel, float saturation, float exhaustion) {
        if (player == null || !player.isOnline()) {
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

    private boolean applyIdentityFromCache(Player player, String identityName, int requestVersion) {
        String key = normalizeIdentityKey(identityName);
        if (player == null || key == null) {
            return false;
        }
        ProfileProperty cachedTexture = identityTextureCache.get(key);
        if (cachedTexture == null) {
            return false;
        }

        PlayerProfile targetProfile = buildProfileFromTextureProperty(player.getUniqueId(), identityName, cachedTexture);
        applyPreparedProfile(player, identityName, requestVersion, targetProfile);
        return true;
    }

    private void warmIdentityCache(List<String> identities, boolean waitForCompletion, long timeoutMillis) {
        if (identities == null || identities.isEmpty()) {
            return;
        }
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (String identity : identities) {
            String key = normalizeIdentityKey(identity);
            if (key == null || identityTextureCache.containsKey(key)) {
                continue;
            }
            futures.add(cacheIdentityTextureAsync(identity));
        }
        if (!waitForCompletion || futures.isEmpty()) {
            return;
        }
        CompletableFuture<Void> combined = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            if (timeoutMillis > 0L) {
                combined.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } else {
                combined.get();
            }
        } catch (Exception ignored) {
        }
    }

    private CompletableFuture<Boolean> cacheIdentityTextureAsync(String identityName) {
        String key = normalizeIdentityKey(identityName);
        if (key == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (identityTextureCache.containsKey(key)) {
            return CompletableFuture.completedFuture(true);
        }
        if (!identitiesBeingCached.add(key)) {
            return CompletableFuture.completedFuture(false);
        }

        String username = identityName.trim();
        PlayerProfile lookup = Bukkit.createProfile(username);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        lookup.update().whenComplete((resolved, throwable) -> {
            if (throwable == null) {
                PlayerProfile resolvedProfile = resolved == null ? lookup : resolved;
                ProfileProperty textureProperty = extractTextureProperty(resolvedProfile);
                if (textureProperty != null) {
                    identityTextureCache.put(key, cloneTextureProperty(textureProperty));
                    identitiesBeingCached.remove(key);
                    result.complete(true);
                    return;
                }
            }

            MojangUtil.resolveTextures(username).whenComplete((textures, error) -> {
                try {
                    if (error != null || textures == null || !textures.hasValue()) {
                        result.complete(false);
                        return;
                    }
                    identityTextureCache.put(key, new ProfileProperty("textures", textures.getValue(), textures.getSignature()));
                    result.complete(true);
                } finally {
                    identitiesBeingCached.remove(key);
                }
            });
        });
        return result;
    }

    private String normalizeIdentityKey(String identityName) {
        if (identityName == null) {
            return null;
        }
        String normalized = identityName.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private ProfileProperty extractTextureProperty(PlayerProfile profile) {
        if (profile == null) {
            return null;
        }
        for (ProfileProperty property : profile.getProperties()) {
            if (property == null || !"textures".equals(property.getName())) {
                continue;
            }
            if (property.getValue() == null || property.getValue().isBlank()) {
                continue;
            }
            return property;
        }
        return null;
    }

    private ProfileProperty cloneTextureProperty(ProfileProperty property) {
        if (property == null) {
            return null;
        }
        return new ProfileProperty(property.getName(), property.getValue(), property.getSignature());
    }

    private PlayerProfile buildProfileFromTextureProperty(UUID playerId, String identityName, ProfileProperty textureProperty) {
        if (playerId == null || identityName == null || identityName.isBlank() || textureProperty == null) {
            return null;
        }
        PlayerProfile targetProfile = Bukkit.createProfile(playerId, identityName);
        targetProfile.clearProperties();
        targetProfile.setProperty(cloneTextureProperty(textureProperty));
        return targetProfile;
    }

    private PlayerProfile buildProfileFromTextures(UUID playerId, String identityName, PlayerTextures sourceTextures) {
        if (playerId == null || identityName == null || identityName.isBlank() || sourceTextures == null || sourceTextures.getSkin() == null) {
            return null;
        }
        PlayerProfile targetProfile = Bukkit.createProfile(playerId, identityName);
        PlayerTextures targetTextures = targetProfile.getTextures();
        targetTextures.clear();
        targetTextures.setSkin(sourceTextures.getSkin(), sourceTextures.getSkinModel());
        if (sourceTextures.getCape() != null) {
            targetTextures.setCape(sourceTextures.getCape());
        }
        targetProfile.setTextures(targetTextures);
        return targetProfile;
    }

    private void applyPreparedProfile(Player player, String identityName, int requestVersion, PlayerProfile targetProfile) {
        if (player == null || targetProfile == null || identityName == null || identityName.isBlank()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        currentIdentityProfiles.put(playerId, targetProfile.clone());

        MurderPlugin plugin = MurderPlugin.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!Objects.equals(requestVersions.get(playerId), requestVersion)) {
                return;
            }
            int foodLevel = player.getFoodLevel();
            float saturation = player.getSaturation();
            float exhaustion = player.getExhaustion();
            player.setPlayerProfile(targetProfile);
            refreshVisibleViewers(player);
            reapplyProfileShortly(player, targetProfile, requestVersion);
            GameSession.hideNametag(player);
            scheduleHungerRestore(player, foodLevel, saturation, exhaustion);
            player.playerListName(ChatUtil.component(formatTabListName(playerId, identityName)));
            currentIdentities.put(playerId, identityName);
            currentIdentityProfiles.put(playerId, targetProfile.clone());
        });
    }

    private void refreshVisibleViewers(Player target) {
        if (target == null || !target.isOnline()) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == null || viewer.equals(target) || !viewer.canSee(target)) {
                continue;
            }
            viewer.hidePlayer(MurderPlugin.getInstance(), target);
            viewer.showPlayer(MurderPlugin.getInstance(), target);
        }
    }

    private void reapplyProfileShortly(Player player, PlayerProfile targetProfile, int requestVersion) {
        if (player == null || targetProfile == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        MurderPlugin plugin = MurderPlugin.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            if (!Objects.equals(requestVersions.get(playerId), requestVersion)) {
                return;
            }
            player.setPlayerProfile(targetProfile);
            refreshVisibleViewers(player);
        }, 2L);
    }

}
