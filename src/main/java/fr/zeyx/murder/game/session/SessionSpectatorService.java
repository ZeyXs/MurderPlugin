package fr.zeyx.murder.game.session;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.gui.EquipmentMenu;
import fr.zeyx.murder.gui.ProfileMenu;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import fr.zeyx.murder.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public class SessionSpectatorService {

    private static final String SPECTATOR_TARGET_SELECTOR_NAME = "&b&lTarget Selector &r&7• Right Click";
    private static final String SPECTATOR_VISIBILITY_NAME = "&c&lSpectator Visibility &r&7• Right Click";
    private static final String SPECTATOR_TARGET_SELECTOR_LEGACY = org.bukkit.ChatColor.translateAlternateColorCodes('&', SPECTATOR_TARGET_SELECTOR_NAME);
    private static final String SPECTATOR_VISIBILITY_LEGACY = org.bukkit.ChatColor.translateAlternateColorCodes('&', SPECTATOR_VISIBILITY_NAME);
    private static final int SPECTATOR_TIME_LEFT_SECONDS = 100;

    private final GameManager gameManager;
    private final Arena arena;
    private final List<UUID> alivePlayers;
    private final Function<UUID, String> identityDisplayNameResolver;
    private final Function<Player, String> chatNameResolver;
    private final Map<UUID, Boolean> spectatorVisibility = new HashMap<>();
    private final Map<UUID, Integer> spectatorTargetIndexes = new HashMap<>();

    public SessionSpectatorService(GameManager gameManager,
                                   Arena arena,
                                   List<UUID> alivePlayers,
                                   Function<UUID, String> identityDisplayNameResolver,
                                   Function<Player, String> chatNameResolver) {
        this.gameManager = gameManager;
        this.arena = arena;
        this.alivePlayers = alivePlayers;
        this.identityDisplayNameResolver = identityDisplayNameResolver;
        this.chatNameResolver = chatNameResolver;
    }

    public void clearState() {
        spectatorVisibility.clear();
        spectatorTargetIndexes.clear();
    }

    public void prepareSpectator(Player victim, Player killer) {
        UUID victimId = victim.getUniqueId();
        spectatorVisibility.put(victimId, true);
        spectatorTargetIndexes.put(victimId, -1);

        victim.removePotionEffect(PotionEffectType.SPEED);
        victim.getInventory().clear();
        victim.getInventory().setArmorContents(new ItemStack[4]);
        victim.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        victim.setFireTicks(0);
        victim.setFallDistance(0f);
        victim.setGameMode(GameMode.ADVENTURE);
        victim.setAllowFlight(true);
        victim.setFlying(true);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
        setSpectatorHotbar(victim);
        showDeathTitle(victim, killer);
    }

    public void prepareForEndGame(Player player) {
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    public void clearTransientState(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        spectatorVisibility.remove(playerId);
        spectatorTargetIndexes.remove(playerId);
        prepareForEndGame(player);
        restoreVisibilityFor(player);
    }

    public void onAliveListChanged(UUID playerId) {
        if (playerId == null || !isSpectator(playerId)) {
            return;
        }
        spectatorVisibility.putIfAbsent(playerId, true);
        spectatorTargetIndexes.putIfAbsent(playerId, -1);
    }

    public void updateSpectatorBoards() {
        int aliveCount = alivePlayers.size();
        int spectatorCount = getSpectatorCount();
        for (UUID playerId : arena.getActivePlayers()) {
            if (!isSpectator(playerId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            gameManager.getScoreboardManager().showSpectatorBoard(player, SPECTATOR_TIME_LEFT_SECONDS, aliveCount, spectatorCount);
        }
    }

    public void refreshPlayerVisibility() {
        for (UUID viewerId : arena.getActivePlayers()) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) {
                applyVisibilityForViewer(viewer);
            }
        }
    }

    public void restoreVisibilityForArenaPlayers() {
        for (UUID playerId : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                restoreVisibilityFor(player);
            }
        }
    }

    public boolean handleInteract(Player player, Component itemName, String legacyName) {
        if (itemName.equals(arena.LEAVE_ITEM)) {
            arena.removePlayer(player, gameManager);
            return true;
        }
        if (itemName.equals(arena.SELECT_EQUIPMENT_ITEM)) {
            new EquipmentMenu().open(player);
            return true;
        }
        if (itemName.equals(arena.VIEW_STATS_ITEM)) {
            new ProfileMenu().open(player);
            return true;
        }
        if (SPECTATOR_TARGET_SELECTOR_LEGACY.equals(legacyName)) {
            selectNextTarget(player);
            return true;
        }
        if (SPECTATOR_VISIBILITY_LEGACY.equals(legacyName)) {
            toggleSpectatorVisibility(player);
            return true;
        }
        return true;
    }

    private void setSpectatorHotbar(Player player) {
        player.getInventory().setItem(0, new ItemBuilder(Material.COMPASS).setName(ChatUtil.itemComponent(SPECTATOR_TARGET_SELECTOR_NAME)).toItemStack());
        player.getInventory().setItem(3, new ItemBuilder(Material.ENDER_CHEST).setName(arena.SELECT_EQUIPMENT_ITEM).toItemStack());

        ItemStack statsHead = new ItemStack(Material.PLAYER_HEAD);
        if (statsHead.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            skullMeta.displayName(arena.VIEW_STATS_ITEM);
            statsHead.setItemMeta(skullMeta);
        }
        player.getInventory().setItem(5, statsHead);
        player.getInventory().setItem(7, new ItemBuilder(Material.REDSTONE).setName(ChatUtil.itemComponent(SPECTATOR_VISIBILITY_NAME)).toItemStack());
        player.getInventory().setItem(8, new ItemBuilder(Material.CLOCK).setName(arena.LEAVE_ITEM).toItemStack());
        player.getInventory().setHeldItemSlot(0);
    }

    private void showDeathTitle(Player victim, Player killer) {
        String killerName = resolveKillerIdentityName(killer);
        if ((killerName == null || killerName.isBlank()) && victim != null) {
            killerName = identityDisplayNameResolver.apply(victim.getUniqueId());
        }
        if (killerName == null || killerName.isBlank()) {
            killerName = "&fUnknown";
        }
        victim.showTitle(Title.title(
                ChatUtil.component("&cYOU DIED!"),
                ChatUtil.component("&cKilled by: " + killerName),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500))
        ));
    }

    private String resolveKillerIdentityName(Player killer) {
        if (killer == null) {
            return null;
        }
        String identityName = gameManager.getSecretIdentityManager().getCurrentIdentityDisplayName(killer.getUniqueId());
        if (identityName != null && !identityName.isBlank()) {
            return identityName;
        }
        String coloredName = gameManager.getSecretIdentityManager().getColoredName(killer);
        if (coloredName != null && !coloredName.isBlank()) {
            return coloredName;
        }
        return "&f" + killer.getName();
    }

    private void selectNextTarget(Player spectator) {
        List<Player> targets = new ArrayList<>();
        for (UUID playerId : alivePlayers) {
            Player target = Bukkit.getPlayer(playerId);
            if (target != null) {
                targets.add(target);
            }
        }
        if (targets.isEmpty()) {
            spectator.sendMessage(ChatUtil.prefixed("&cNo alive players to watch."));
            return;
        }
        UUID spectatorId = spectator.getUniqueId();
        int nextIndex = spectatorTargetIndexes.getOrDefault(spectatorId, -1) + 1;
        if (nextIndex >= targets.size()) {
            nextIndex = 0;
        }
        spectatorTargetIndexes.put(spectatorId, nextIndex);
        Player target = targets.get(nextIndex);
        spectator.teleport(target.getLocation());
        spectator.sendMessage(ChatUtil.prefixed("&7Now watching " + chatNameResolver.apply(target) + "&7."));
    }

    private void toggleSpectatorVisibility(Player spectator) {
        UUID spectatorId = spectator.getUniqueId();
        boolean enabled = !spectatorVisibility.getOrDefault(spectatorId, true);
        spectatorVisibility.put(spectatorId, enabled);
        applyVisibilityForViewer(spectator);
        spectator.sendMessage(ChatUtil.prefixed(enabled
                ? "&7Spectators are now &avisible&7."
                : "&7Spectators are now &chidden&7."));
    }

    private int getSpectatorCount() {
        int count = 0;
        for (UUID playerId : arena.getActivePlayers()) {
            if (isSpectator(playerId)) {
                count++;
            }
        }
        return count;
    }

    private boolean isSpectator(UUID playerId) {
        return playerId != null && arena.getActivePlayers().contains(playerId) && !alivePlayers.contains(playerId);
    }

    private void applyVisibilityForViewer(Player viewer) {
        boolean viewerAlive = alivePlayers.contains(viewer.getUniqueId());
        boolean showSpectators = spectatorVisibility.getOrDefault(viewer.getUniqueId(), true);
        for (UUID targetId : arena.getActivePlayers()) {
            if (viewer.getUniqueId().equals(targetId)) {
                continue;
            }
            Player target = Bukkit.getPlayer(targetId);
            if (target == null) {
                continue;
            }
            boolean targetAlive = alivePlayers.contains(targetId);
            if (viewerAlive) {
                if (targetAlive) {
                    viewer.showPlayer(MurderPlugin.getInstance(), target);
                } else {
                    viewer.hidePlayer(MurderPlugin.getInstance(), target);
                }
                continue;
            }
            if (targetAlive || showSpectators) {
                viewer.showPlayer(MurderPlugin.getInstance(), target);
            } else {
                viewer.hidePlayer(MurderPlugin.getInstance(), target);
            }
        }
    }

    private void restoreVisibilityFor(Player player) {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.equals(player)) {
                continue;
            }
            onlinePlayer.showPlayer(MurderPlugin.getInstance(), player);
            player.showPlayer(MurderPlugin.getInstance(), onlinePlayer);
        }
    }
}
