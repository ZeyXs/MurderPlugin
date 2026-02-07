package fr.zeyx.murder.game;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import fr.zeyx.murder.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import net.kyori.adventure.title.Title;
import org.bukkit.ChatColor;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.time.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashSet;

public class GameSession {

    private static final String HIDDEN_NAMETAG_TEAM = "murder_hide";
    private static final int MURDERER_FOOD_LEVEL = 8;
    private static final int NON_MURDERER_FOOD_LEVEL = 6;

    private static final String MURDERER_KNIFE_NAME = "&7&oKnife";
    private static final String MURDERER_BUY_KNIFE_NAME = "&7&lBuy Knife&r &7• Right Click";
    private static final String MURDERER_SWITCH_IDENTITY_NAME = "&7&lSwitch Identity&r &7• Right Click";
    private static final String DETECTIVE_GUN_NAME = "&7&oGun";

    private final GameManager gameManager;
    private final Arena arena;

    private final List<UUID> alivePlayers = new ArrayList<>();
    private final Map<UUID, Role> roles = new HashMap<>();
    private final Map<UUID, ItemStack[]> chatHotbars = new HashMap<>();
    private final Set<UUID> chatMenuCooldown = new HashSet<>();
    private UUID murdererId;
    private UUID detectiveId;
    private UUID murdererKillerId;

    public GameSession(GameManager gameManager, Arena arena) {
        this.gameManager = gameManager;
        this.arena = arena;
    }

    public void start() {
        alivePlayers.clear();
        alivePlayers.addAll(arena.getActivePlayers());
        int aliveCount = alivePlayers.size();

        assignRoles();
        applySecretIdentities();

        for (UUID playerId : alivePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            Location spawn = pickSpawnLocation();
            if (spawn != null) {
                player.teleport(spawn);
            }
            player.getInventory().clear();
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            Role role = roles.get(playerId);
            if (role != null) {
                enforceHungerLock(player);
            }
            if (role == Role.MURDERER) {
                ItemStack knife = new ItemBuilder(Material.WOODEN_SWORD).setName(ChatUtil.itemComponent(MURDERER_KNIFE_NAME, true)).toItemStack();
                applyInstantAttackSpeed(knife);
                player.getInventory().setItem(0, knife);
                player.getInventory().setItem(3, new ItemBuilder(Material.GRAY_DYE).setName(ChatUtil.itemComponent(MURDERER_BUY_KNIFE_NAME)).toItemStack());
                player.getInventory().setItem(4, new ItemBuilder(Material.GRAY_DYE).setName(ChatUtil.itemComponent(MURDERER_SWITCH_IDENTITY_NAME)).toItemStack());
            } else if (role == Role.DETECTIVE) {
                ItemStack gun = new ItemBuilder(Material.WOODEN_HOE).setName(ChatUtil.itemComponent(DETECTIVE_GUN_NAME, true)).toItemStack();
                applyInstantAttackSpeed(gun);
                player.getInventory().setItem(0, gun);
            }
            player.getInventory().setItem(8, QuickChatMenu.buildChatBook());
            Color identityColor = gameManager.getSecretIdentityManager().getCurrentIdentityLeatherColor(player.getUniqueId());
            if (identityColor != null) {
                ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
                if (chestplate.getItemMeta() instanceof LeatherArmorMeta chestplateMeta) {
                    chestplateMeta.setColor(identityColor);
                    chestplateMeta.setUnbreakable(true);
                    chestplateMeta.addItemFlags(ItemFlag.HIDE_DYE, ItemFlag.HIDE_UNBREAKABLE);
                    chestplate.setItemMeta(chestplateMeta);
                }
                player.getInventory().setChestplate(chestplate);
            }
            player.getInventory().setHeldItemSlot(8);
            player.setLevel(aliveCount);
            player.setExp(1.0f);
            String roleLine = switch (roles.get(playerId)) {
                case MURDERER -> "&4Murderer";
                case DETECTIVE -> "&1Detective";
                case BYSTANDER -> "&bBystander";
            };
            String identityName = gameManager.getSecretIdentityManager().getCurrentIdentityDisplayName(player.getUniqueId());
            if (identityName == null || identityName.isBlank()) {
                identityName = gameManager.getSecretIdentityManager().getColoredName(player);
            }
            gameManager.getScoreboardManager().showGameBoard(player, roleLine, identityName);
            hideNametag(player);
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 5, 0, false, false, false));
            showRoleTitle(player, roles.get(playerId));
        }

        updateChatCompletionsForActivePlayers();
    }

    public void endGame() {
        sendRoleRevealMessages();
        sendWinnerMessage();

        for (UUID playerId : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            clearChatCompletions(player);
            player.setGameMode(GameMode.SPECTATOR);
            showNametag(player);
            gameManager.getSecretIdentityManager().resetIdentity(player);
        }
    }

    public List<UUID> getAlivePlayers() {
        return alivePlayers;
    }

    public Role getRole(UUID playerId) {
        return roles.get(playerId);
    }

    public int getLockedFoodLevel(UUID playerId) {
        Role role = roles.get(playerId);
        if (role == null) {
            return -1;
        }
        return role == Role.MURDERER ? MURDERER_FOOD_LEVEL : NON_MURDERER_FOOD_LEVEL;
    }

    public void enforceHungerLock(Player player) {
        if (player == null) {
            return;
        }
        int foodLevel = getLockedFoodLevel(player.getUniqueId());
        if (foodLevel < 0) {
            return;
        }
        player.setFoodLevel(foodLevel);
        player.setSaturation(0f);
        player.setExhaustion(0f);
        player.sendHealthUpdate(player.getHealth(), foodLevel, 0f);
    }

    public void removeAlive(UUID playerId) {
        removeAlive(playerId, null);
    }

    public void removeAlive(UUID playerId, UUID killerId) {
        alivePlayers.remove(playerId);
        if (playerId != null && playerId.equals(murdererId) && killerId != null && !killerId.equals(playerId)) {
            murdererKillerId = killerId;
        }
        updateChatCompletionsForActivePlayers();
    }

    public UUID getMurdererId() {
        return murdererId;
    }

    public UUID getDetectiveId() {
        return detectiveId;
    }

    public boolean handleInteract(Player player, Component itemName, String legacyName) {
        if (player == null || itemName == null || legacyName == null) {
            return false;
        }
        if (!isAlive(player.getUniqueId())) {
            return true;
        }
        if (chatMenuCooldown.contains(player.getUniqueId())) {
            return true;
        }
        if (QuickChatMenu.CHAT_BOOK_NAME.equals(legacyName)) {
            openChatMenu(player);
            return true;
        }
        if (chatHotbars.containsKey(player.getUniqueId())) {
            if (QuickChatMenu.CLOSE_NAME.equals(legacyName)) {
                closeChatMenu(player);
                return true;
            }
            String message = QuickChatMenu.resolveMessage(legacyName);
            if (message != null) {
                closeChatMenu(player);
                String resolvedMessage = resolveQuickChatMessage(player, legacyName, message);
                sendQuickChatMessage(player, resolvedMessage);
                return true;
            }
        }
        if (itemName.equals(arena.LEAVE_ITEM)) {
            arena.removePlayer(player, gameManager);
            return true;
        }
        return false;
    }

    public boolean handleDamageByEntity(EntityDamageByEntityEvent event) {
        if (event == null) {
            return false;
        }
        Player damager = resolveDamager(event);
        Player victim = event.getEntity() instanceof Player damagedPlayer ? damagedPlayer : null;
        boolean victimInArena = victim != null && arena.isPlaying(victim);

        if (damager == null) {
            return victimInArena;
        }

        boolean damagerInArena = arena.isPlaying(damager);
        if (damagerInArena) {
            return true;
        }
        return victimInArena;
    }

    public boolean handleDamage(EntityDamageEvent event) {
        if (event == null) {
            return false;
        }
        if (!(event.getEntity() instanceof Player victim) || !arena.isPlaying(victim)) {
            return false;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            victim.setFallDistance(0f);
            return true;
        }
        if (event instanceof EntityDamageByEntityEvent) {
            return true;
        }
        if (event.isCancelled()) {
            return true;
        }
        if (!isAlive(victim.getUniqueId())) {
            return true;
        }
        if (event.getFinalDamage() < victim.getHealth()) {
            return false;
        }
        eliminatePlayer(victim, resolveDamager(event));
        return true;
    }

    public boolean eliminatePlayer(Player victim) {
        return eliminatePlayer(victim, null);
    }

    public boolean eliminatePlayer(Player victim, Player killer) {
        if (victim == null || !arena.isPlaying(victim)) {
            return false;
        }
        UUID victimId = victim.getUniqueId();
        if (!isAlive(victimId)) {
            return false;
        }

        gameManager.getCorpseManager().spawnCorpse(victim, victim.getLocation(), victim.getInventory().getChestplate());

        clearTransientState(victim);
        victim.removePotionEffect(PotionEffectType.SPEED);
        victim.getInventory().clear();
        victim.getInventory().setArmorContents(new ItemStack[4]);
        victim.setFireTicks(0);
        victim.setFallDistance(0f);
        victim.setGameMode(GameMode.SPECTATOR);

        UUID killerId = killer == null ? null : killer.getUniqueId();
        removeAlive(victimId, killerId);
        updateAliveCountDisplays();
        return true;
    }

    public void clearTransientState(Player player) {
        if (player == null) {
            return;
        }
        chatHotbars.remove(player.getUniqueId());
        chatMenuCooldown.remove(player.getUniqueId());
        clearChatCompletions(player);
    }

    private Location pickSpawnLocation() {
        List<Location> spawnSpots = arena.getSpawnSpots();
        if (spawnSpots != null && !spawnSpots.isEmpty()) {
            return spawnSpots.get(ThreadLocalRandom.current().nextInt(spawnSpots.size()));
        }
        return arena.getSpawnLocation();
    }

    private void assignRoles() {
        roles.clear();
        murdererId = null;
        detectiveId = null;
        murdererKillerId = null;
        List<UUID> players = new ArrayList<>(alivePlayers);
        if (players.isEmpty()) {
            return;
        }
        Collections.shuffle(players);
        murdererId = players.get(0);
        roles.put(murdererId, Role.MURDERER);
        if (players.size() > 1) {
            detectiveId = players.get(1);
            roles.put(detectiveId, Role.DETECTIVE);
        }
        for (int i = 2; i < players.size(); i++) {
            roles.put(players.get(i), Role.BYSTANDER);
        }
    }

    private void applySecretIdentities() {
        List<Player> players = new ArrayList<>();
        for (UUID playerId : alivePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                players.add(player);
            }
        }
        gameManager.getSecretIdentityManager().applyUniqueIdentities(players);
    }

    private void showRoleTitle(Player player, Role role) {
        if (player == null || role == null) {
            return;
        }
        Title title = switch (role) {
            case MURDERER -> Title.title(
                    ChatUtil.component("&c&lMurderer      "),
                    ChatUtil.component("      &4Don't get caught"),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            );
            case DETECTIVE -> Title.title(
                    ChatUtil.component("&3&lBystander      "),
                    ChatUtil.component("       &dWith a secret weapon"),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            );
            case BYSTANDER -> Title.title(
                    ChatUtil.component("&3&lBystander       "),
                    ChatUtil.component("      &3Kill the murderer"),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            );
        };
        player.showTitle(title);
        // reveal sound 10 ticks after, not sure if it's the exact time
        Bukkit.getScheduler().runTaskLater(MurderPlugin.getInstance(), task -> {
            if (!player.isOnline()) {
                return;
            }
            player.playSound(player.getLocation(), Sound.ENTITY_GHAST_HURT, 1.0f, 1.0f);
        }, 10L);
    }

    public static void hideNametag(Player player) {
        if (player == null || Bukkit.getScoreboardManager() == null) {
            return;
        }
        Team team = getOrCreateHiddenTeam();
        for (String entry : collectNametagEntries(player)) {
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
        }
    }

    public static void showNametag(Player player) {
        if (player == null || Bukkit.getScoreboardManager() == null) {
            return;
        }
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(HIDDEN_NAMETAG_TEAM);
        if (team == null) {
            return;
        }
        for (String entry : collectNametagEntries(player)) {
            if (team.hasEntry(entry)) {
                team.removeEntry(entry);
            }
        }
    }

    private static Team getOrCreateHiddenTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(HIDDEN_NAMETAG_TEAM);
        if (team == null) {
            team = scoreboard.registerNewTeam(HIDDEN_NAMETAG_TEAM);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        return team;
    }

    private static List<String> collectNametagEntries(Player player) {
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        if (player.getName() != null && !player.getName().isBlank()) {
            entries.add(player.getName());
        }
        if (player.getPlayerProfile() != null) {
            String profileName = player.getPlayerProfile().getName();
            if (profileName != null && !profileName.isBlank()) {
                entries.add(profileName);
            }
        }
        return new ArrayList<>(entries);
    }

    private void applyInstantAttackSpeed(ItemStack item) {
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(MurderPlugin.getInstance(), "instant_attack_speed");
        meta.addAttributeModifier(
                Attribute.ATTACK_SPEED,
                new AttributeModifier(
                        key,
                        1000.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.HAND
                )
        );
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
    }

    private void openChatMenu(Player player) {
        UUID playerId = player.getUniqueId();
        if (chatHotbars.containsKey(playerId)) {
            return;
        }
        ItemStack[] saved = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            saved[i] = player.getInventory().getItem(i);
        }
        chatHotbars.put(playerId, saved);
        ItemStack[] menu = QuickChatMenu.buildMenuHotbar();
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, menu[i]);
        }
        applyChatMenuCooldown(playerId);
    }

    private void closeChatMenu(Player player) {
        UUID playerId = player.getUniqueId();
        ItemStack[] saved = chatHotbars.remove(playerId);
        if (saved == null) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, saved[i]);
        }
        player.getInventory().setHeldItemSlot(8);
        applyChatMenuCooldown(playerId);
    }

    private void sendQuickChatMessage(Player sender, String message) {
        String name = resolveChatName(sender);
        String formatted = ChatColor.translateAlternateColorCodes('&', "&f" + name + " &8• &7" + message);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.sendMessage(formatted);
        }
    }

    private String resolveQuickChatMessage(Player sender, String legacyName, String defaultMessage) {
        if (!QuickChatMenu.LIME_DYE_NAME.equals(legacyName)) {
            return ChatColor.stripColor(defaultMessage);
        }
        List<String> nearbyNames = findNearbyPlayerNames(sender, 15.0);
        if (nearbyNames.isEmpty()) {
            return "I am next to nobody!";
        }
        return "I am next to " + String.join("&7, ", nearbyNames) + "&7!";
    }

    private List<String> findNearbyPlayerNames(Player sender, double radius) {
        List<String> names = new ArrayList<>();
        if (sender == null || sender.getWorld() == null) {
            return names;
        }
        double radiusSquared = radius * radius;
        for (UUID playerId : arena.getActivePlayers()) {
            if (sender.getUniqueId().equals(playerId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || player.getWorld() == null) {
                continue;
            }
            if (!player.getWorld().equals(sender.getWorld())) {
                continue;
            }
            if (player.getLocation().distanceSquared(sender.getLocation()) <= radiusSquared) {
                names.add(resolveChatName(player));
            }
        }
        return names;
    }

    private String resolveChatName(Player player) {
        String displayName = gameManager.getSecretIdentityManager().getColoredName(player);
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return player.getName();
    }

    private void applyChatMenuCooldown(UUID playerId) {
        chatMenuCooldown.add(playerId);
        Bukkit.getScheduler().runTaskLater(MurderPlugin.getInstance(), task -> chatMenuCooldown.remove(playerId), 1L);
    }

    private boolean isAlive(UUID playerId) {
        return alivePlayers.contains(playerId);
    }

    private Player resolveDamager(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntityEvent)) {
            return null;
        }
        if (byEntityEvent.getDamager() instanceof Player damager) {
            return damager;
        }
        if (byEntityEvent.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private void updateAliveCountDisplays() {
        int aliveCount = alivePlayers.size();
        Set<UUID> alive = new HashSet<>(alivePlayers);
        for (UUID playerId : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            if (alive.contains(playerId)) {
                player.setLevel(aliveCount);
                player.setExp(1.0f);
                continue;
            }
            player.setLevel(0);
            player.setExp(0.0f);
        }
    }

    public boolean isGameOver() {
        return hasMurdererWon() || haveBystandersWon();
    }

    public boolean hasMurdererWon() {
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

    public boolean haveBystandersWon() {
        return murdererId != null && !alivePlayers.contains(murdererId);
    }

    private void sendRoleRevealMessages() {
        List<UUID> orderedPlayers = new ArrayList<>(arena.getActivePlayers());
        orderedPlayers.sort(Comparator.comparingInt(this::rolePriority));

        for (UUID playerId : orderedPlayers) {
            Role role = getRole(playerId);
            if (role == null) {
                continue;
            }
            String identityName = resolveIdentityDisplayName(playerId);
            String realPlayerName = resolveRealPlayerName(playerId);
            boolean dead = !alivePlayers.contains(playerId);
            String roleToken = formatRoleToken(role, dead);
            arena.sendArenaMessage(identityName + " &f» &7" + realPlayerName + " " + roleToken);
        }
    }

    private void sendWinnerMessage() {
        if (hasMurdererWon()) {
            arena.sendArenaMessage("&7The &cmurderer &7has killed everyone!");
            return;
        }

        String killerIdentityName = resolveMurdererKillerIdentityName();
        arena.sendArenaMessage("&7The &cmurderer &7has been killed by " + killerIdentityName + "&7!");
    }

    private String resolveMurdererKillerIdentityName() {
        if (murdererKillerId != null) {
            String displayName = resolveIdentityDisplayName(murdererKillerId);
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
        }
        for (UUID playerId : alivePlayers) {
            if (playerId.equals(murdererId)) {
                continue;
            }
            String displayName = resolveIdentityDisplayName(playerId);
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
        }
        return "&fUnknown";
    }

    private int rolePriority(UUID playerId) {
        Role role = getRole(playerId);
        if (role == null) {
            return 3;
        }
        return switch (role) {
            case BYSTANDER -> 0;
            case DETECTIVE -> 1;
            case MURDERER -> 2;
        };
    }

    private String resolveIdentityDisplayName(UUID playerId) {
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

    private String resolveRealPlayerName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        String offlineName = Bukkit.getOfflinePlayer(playerId).getName();
        return offlineName == null ? playerId.toString() : offlineName;
    }

    private String formatRoleToken(Role role, boolean dead) {
        String strike = dead ? "&m" : "";
        return switch (role) {
            case BYSTANDER -> "&a" + strike + "(bystander)";
            case DETECTIVE -> "&d" + strike + "(detective)";
            case MURDERER -> "&c" + strike + "(murderer)";
        };
    }

    private void updateChatCompletionsForActivePlayers() {
        List<String> identityCompletions = collectIdentityCompletions();
        for (UUID playerId : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            player.setCustomChatCompletions(identityCompletions);
        }
    }

    private void clearChatCompletions(Player player) {
        player.setCustomChatCompletions(List.of());
    }

    private List<String> collectIdentityCompletions() {
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        for (UUID playerId : arena.getActivePlayers()) {
            String identity = gameManager.getSecretIdentityManager().getCurrentIdentityName(playerId);
            if (identity != null && !identity.isBlank()) {
                identities.add(identity);
            }
        }
        return new ArrayList<>(identities);
    }

}
