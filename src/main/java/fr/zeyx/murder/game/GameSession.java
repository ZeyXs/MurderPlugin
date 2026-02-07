package fr.zeyx.murder.game;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import fr.zeyx.murder.util.ItemBuilder;
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

import java.time.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
    private UUID murdererId;
    private UUID detectiveId;

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
                    chestplateMeta.addItemFlags(ItemFlag.HIDE_DYE);
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
    }

    public void endGame() {
        for (UUID playerId : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            player.setGameMode(GameMode.SPECTATOR);
            showNametag(player);
            gameManager.getSecretIdentityManager().resetIdentity(player);
            player.sendMessage(ChatUtil.prefixed("&7End of the game!"));
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
        alivePlayers.remove(playerId);
        roles.remove(playerId);
        if (playerId != null) {
            if (playerId.equals(murdererId)) {
                murdererId = null;
            }
            if (playerId.equals(detectiveId)) {
                detectiveId = null;
            }
        }
    }

    public UUID getMurdererId() {
        return murdererId;
    }

    public UUID getDetectiveId() {
        return detectiveId;
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
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    public static void showNametag(Player player) {
        if (player == null || Bukkit.getScoreboardManager() == null) {
            return;
        }
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(HIDDEN_NAMETAG_TEAM);
        if (team != null && team.hasEntry(player.getName())) {
            team.removeEntry(player.getName());
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
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
    }
}
