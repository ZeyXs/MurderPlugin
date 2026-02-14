package fr.zeyx.murder.game.feature;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.game.Role;
import fr.zeyx.murder.gui.EquipmentMenu;
import fr.zeyx.murder.gui.ProfileMenu;
import fr.zeyx.murder.gui.TeleportSelectorMenu;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.TextUtil;
import fr.zeyx.murder.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntSupplier;

public class SpectatorFeature {

    private static final String SPECTATOR_TARGET_SELECTOR_NAME = "&b&lTeleport Selector &r&7• Right Click";
    private static final String SPECTATOR_TARGET_SELECTOR_OLD_NAME = "&b&lTarget Selector &r&7• Right Click";
    private static final String SPECTATOR_VISIBILITY_TOGGLE_NAME = "&c&lSpectator Visibility &r&7• Right Click";
    private static final String SPECTATOR_TARGET_SELECTOR_LEGACY = ChatColor.translateAlternateColorCodes('&', SPECTATOR_TARGET_SELECTOR_NAME);
    private static final String SPECTATOR_TARGET_SELECTOR_OLD_LEGACY = ChatColor.translateAlternateColorCodes('&', SPECTATOR_TARGET_SELECTOR_OLD_NAME);
    private static final String SPECTATOR_VISIBILITY_TOGGLE_LEGACY = ChatColor.translateAlternateColorCodes('&', SPECTATOR_VISIBILITY_TOGGLE_NAME);

    private final GameManager gameManager;
    private final Arena arena;
    private final List<UUID> alivePlayers;
    private final Function<UUID, String> identityDisplayNameResolver;
    private final TeleportSelectorMenu teleportSelectorMenu;
    private final Map<UUID, Boolean> spectatorVisibility = new HashMap<>();

    public SpectatorFeature(GameManager gameManager,
                            Arena arena,
                            List<UUID> alivePlayers,
                            Function<UUID, String> identityDisplayNameResolver,
                            Function<Player, String> chatNameResolver,
                            Function<UUID, Role> roleResolver,
                            Function<UUID, Integer> weaponCountResolver,
                            IntSupplier murdererKillCountResolver) {
        this.gameManager = gameManager;
        this.arena = arena;
        this.alivePlayers = alivePlayers;
        this.identityDisplayNameResolver = identityDisplayNameResolver;
        this.teleportSelectorMenu = new TeleportSelectorMenu(
                gameManager,
                identityDisplayNameResolver,
                chatNameResolver,
                roleResolver,
                weaponCountResolver,
                murdererKillCountResolver
        );
    }

    public void clearState() {
        spectatorVisibility.clear();
    }

    public void prepareSpectator(Player victim, Player killer) {
        UUID victimId = victim.getUniqueId();
        spectatorVisibility.put(victimId, true);

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
        prepareForEndGame(player);
        restoreVisibilityFor(player);
    }

    public void onAliveListChanged(UUID playerId) {
        if (playerId == null || !isSpectator(playerId)) {
            return;
        }
        spectatorVisibility.putIfAbsent(playerId, true);
    }

    public void updateSpectatorBoards(int roundTimeLeftSeconds) {
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
            gameManager.getScoreboardManager().showSpectatorBoard(player, roundTimeLeftSeconds, aliveCount, spectatorCount);
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
        if (itemName != null && itemName.equals(arena.LEAVE_ITEM)) {
            arena.removePlayer(player, gameManager);
            return true;
        }
        if (itemName != null && itemName.equals(arena.SELECT_EQUIPMENT_ITEM)) {
            new EquipmentMenu().open(player);
            return true;
        }
        if (itemName != null && itemName.equals(arena.VIEW_STATS_ITEM)) {
            new ProfileMenu().open(player);
            return true;
        }
        if (isTeleportSelectorName(legacyName)) {
            teleportSelectorMenu.open(player, alivePlayers);
            return true;
        }
        if (isVisibilityToggleName(legacyName)) {
            toggleSpectatorVisibility(player);
            return true;
        }
        return true;
    }

    private void setSpectatorHotbar(Player player) {
        player.getInventory().setItem(0, new ItemBuilder(Material.COMPASS).setName(TextUtil.itemComponent(SPECTATOR_TARGET_SELECTOR_NAME)).toItemStack());
        player.getInventory().setItem(3, new ItemBuilder(Material.ENDER_CHEST).setName(arena.SELECT_EQUIPMENT_ITEM).toItemStack());

        ItemStack statsHead = new ItemStack(Material.PLAYER_HEAD);
        if (statsHead.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            skullMeta.displayName(arena.VIEW_STATS_ITEM);
            statsHead.setItemMeta(skullMeta);
        }
        player.getInventory().setItem(5, statsHead);
        updateVisibilityToggleItem(player, spectatorVisibility.getOrDefault(player.getUniqueId(), true));
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
                TextUtil.component("&cYOU DIED!"),
                TextUtil.component("&cKilled by: " + killerName),
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

    private void toggleSpectatorVisibility(Player spectator) {
        UUID spectatorId = spectator.getUniqueId();
        boolean enabled = !spectatorVisibility.getOrDefault(spectatorId, true);
        spectatorVisibility.put(spectatorId, enabled);
        updateVisibilityToggleItem(spectator, enabled);
        applyVisibilityForViewer(spectator);
        spectator.sendMessage(TextUtil.prefixed(enabled
                ? "&7Spectators are now &avisible&7."
                : "&7Spectators are now &chidden&7."));
    }

    private void updateVisibilityToggleItem(Player spectator, boolean visible) {
        Material material = visible ? Material.REDSTONE : Material.GUNPOWDER;
        spectator.getInventory().setItem(7, new ItemBuilder(material).setName(TextUtil.itemComponent(SPECTATOR_VISIBILITY_TOGGLE_NAME)).toItemStack());
    }

    private boolean isTeleportSelectorName(String legacyName) {
        if (legacyName == null || legacyName.isBlank()) {
            return false;
        }
        if (SPECTATOR_TARGET_SELECTOR_LEGACY.equals(legacyName) || SPECTATOR_TARGET_SELECTOR_OLD_LEGACY.equals(legacyName)) {
            return true;
        }
        String stripped = org.bukkit.ChatColor.stripColor(legacyName);
        if (stripped == null || stripped.isBlank()) {
            return false;
        }
        String normalized = stripped.toLowerCase(Locale.ROOT);
        return normalized.contains("teleport selector") || normalized.contains("target selector");
    }

    private boolean isVisibilityToggleName(String legacyName) {
        if (legacyName == null || legacyName.isBlank()) {
            return false;
        }
        if (SPECTATOR_VISIBILITY_TOGGLE_LEGACY.equals(legacyName)) {
            return true;
        }
        String stripped = org.bukkit.ChatColor.stripColor(legacyName);
        if (stripped == null || stripped.isBlank()) {
            return false;
        }
        String normalized = stripped.toLowerCase(Locale.ROOT);
        return normalized.contains("spectator visibility");
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
