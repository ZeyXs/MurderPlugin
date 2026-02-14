package fr.zeyx.murder.game.feature;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.game.GameSession;
import fr.zeyx.murder.game.Role;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GunFeature {

    private static final double GUN_ARROW_SPEED = 4D;
    private static final float GUN_SOUND_VOLUME = 1.0F;
    private static final float GUN_SOUND_PITCH = 2.0F;
    private static final long BASE_GUN_COOLDOWN_MILLIS = 2750L;
    private static final double UPGRADED_COOLDOWN_MULTIPLIER = 0.5D;
    private static final int BASE_GUN_VERSION = 1;
    private static final int GUN_ARROW_MAX_LIFETIME_TICKS = 20 * 3;
    private static final long INNOCENT_GUN_PICKUP_LOCK_MILLIS = 10000L;
    private static final int INNOCENT_SHOOTER_BLINDNESS_TICKS = 20 * 10;
    private static final Particle GUN_TRAIL_PARTICLE = resolveTrailParticle();

    private final GameManager gameManager;
    private final Arena arena;
    private final Map<UUID, BukkitTask> gunTrailTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> gunCooldownEndAt = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> gunReloadTasks = new ConcurrentHashMap<>();
    private final Map<UUID, DroppedGunData> droppedGunItems = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> gunUpgradeLevels = new ConcurrentHashMap<>();

    public GunFeature(GameManager gameManager, Arena arena) {
        this.gameManager = gameManager;
        this.arena = arena;
    }

    private static final class DroppedGunData {
        private final UUID blockedPickerId;
        private final long blockedUntilAt;

        private DroppedGunData(UUID blockedPickerId, long blockedUntilAt) {
            this.blockedPickerId = blockedPickerId;
            this.blockedUntilAt = blockedUntilAt;
        }

        private boolean isBlockedFor(UUID playerId, long now) {
            return blockedPickerId != null
                    && blockedPickerId.equals(playerId)
                    && now < blockedUntilAt;
        }
    }

    public boolean handleInteract(PlayerInteractEvent event, Player shooter, GameSession session) {
        if (!gameManager.getGunManager().isGunItem(event.getItem())) {
            return false;
        }

        event.setCancelled(true);
        if (!shouldUseReloadCooldown(shooter, session)) {
            return true;
        }
        if (isOnCooldown(shooter)) {
            return true;
        }

        Arrow arrow = shooter.launchProjectile(Arrow.class);
        Vector velocity = shooter.getEyeLocation().getDirection().normalize().multiply(GUN_ARROW_SPEED);
        arrow.setVelocity(velocity);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        gameManager.getGunManager().markGunProjectile(arrow);
        shooter.getWorld().playSound(shooter.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, GUN_SOUND_VOLUME, GUN_SOUND_PITCH);

        startReloadCooldown(shooter, session);
        startTrailTask(arrow);
        return true;
    }

    public void onProjectileHit(ProjectileHitEvent event, GameSession session) {
        if (!(event.getEntity() instanceof Arrow arrow) || !gameManager.getGunManager().isGunProjectile(arrow)) {
            return;
        }
        if (event.getHitEntity() instanceof Player victim && arrow.getShooter() instanceof Player shooter) {
            tryEliminateHitPlayer(shooter, victim, session);
        }
        if (event.getHitEntity() != null || event.getHitBlock() != null) {
            arrow.remove();
        }
        stopTrailTask(arrow.getUniqueId());
    }

    public void clearRuntimeState() {
        for (BukkitTask task : gunTrailTasks.values()) {
            task.cancel();
        }
        gunTrailTasks.clear();
        for (BukkitTask task : gunReloadTasks.values()) {
            task.cancel();
        }
        gunReloadTasks.clear();
        gunCooldownEndAt.clear();
        gunUpgradeLevels.clear();
        clearAllDroppedGuns();
    }

    public int getGunUpgradeLevel(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return Math.max(0, gunUpgradeLevels.getOrDefault(playerId, 0));
    }

    public int getGunVersion(UUID playerId) {
        return BASE_GUN_VERSION + getGunUpgradeLevel(playerId);
    }

    public boolean applyEmeraldUpgrade(Player player) {
        if (player == null || !player.isOnline() || player.getWorld() == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (getGunCount(player) <= 0) {
            // First trade without a gun gives the current base version (v1 by default).
            return giveGunVersion(player, getGunVersion(playerId));
        }

        int previousLevel = getGunUpgradeLevel(playerId);
        int nextLevel = previousLevel + 1;
        int nextVersion = BASE_GUN_VERSION + nextLevel;
        if (!upgradePlayerGunItem(player, nextVersion)) {
            return false;
        }
        gunUpgradeLevels.put(playerId, nextLevel);
        return true;
    }

    public int getGunCount(Player player) {
        if (player == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (gameManager.getGunManager().isGunItem(item)) {
                count += item.getAmount();
            }
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (gameManager.getGunManager().isGunItem(offHand)) {
            count += offHand.getAmount();
        }
        return count;
    }

    public boolean giveGun(Player player) {
        if (player == null || !player.isOnline() || player.getWorld() == null) {
            return false;
        }
        ItemStack gun = gameManager.getGunManager().createGunItem();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(gun);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        return true;
    }

    public boolean upgradePlayerGunItem(Player player, int targetVersion) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        boolean updated = false;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!gameManager.getGunManager().isGunItem(item)) {
                continue;
            }
            player.getInventory().setItem(slot, createGunVersionStack(item.getAmount(), targetVersion));
            updated = true;
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (gameManager.getGunManager().isGunItem(offHand)) {
            player.getInventory().setItemInOffHand(createGunVersionStack(offHand.getAmount(), targetVersion));
            updated = true;
        }
        return updated;
    }

    public void clearAllDroppedGuns() {
        for (UUID itemId : Set.copyOf(droppedGunItems.keySet())) {
            Item droppedGun = resolveItem(itemId);
            if (droppedGun != null) {
                droppedGun.remove();
            }
        }
        droppedGunItems.clear();

        Set<org.bukkit.World> worlds = collectArenaWorlds();
        for (org.bukkit.World world : worlds) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (gameManager.getGunManager().isGunItem(item.getItemStack())) {
                    item.remove();
                }
            }
        }
    }

    public void onPlayerEliminated(Player victim, GameSession session) {
        if (victim == null || session == null) {
            return;
        }
        clearCooldown(victim.getUniqueId(), false);
        Role victimRole = session.getRole(victim.getUniqueId());
        if (!isInnocent(victimRole)) {
            return;
        }
        dropGunFromPlayer(victim, null, 0L);
    }

    public void onGunPickup(EntityPickupItemEvent event, GameSession session) {
        ItemStack pickedStack = event.getItem().getItemStack();
        if (!gameManager.getGunManager().isGunItem(pickedStack)) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        if (session == null || !arena.isPlaying(player) || !session.getAlivePlayers().contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        Role role = session.getRole(player.getUniqueId());
        if (role == Role.MURDERER) {
            event.setCancelled(true);
            return;
        }
        if (getGunCount(player) > 0) {
            event.setCancelled(true);
            return;
        }

        UUID itemId = event.getItem().getUniqueId();
        DroppedGunData dropData = droppedGunItems.get(itemId);
        if (dropData != null && dropData.isBlockedFor(player.getUniqueId(), System.currentTimeMillis())) {
            event.setCancelled(true);
            return;
        }
        droppedGunItems.remove(itemId);
    }

    public void onGunDespawn(ItemDespawnEvent event) {
        if (!gameManager.getGunManager().isGunItem(event.getEntity().getItemStack())) {
            return;
        }
        event.setCancelled(true);
    }

    private void startTrailTask(Arrow arrow) {
        UUID projectileId = arrow.getUniqueId();
        stopTrailTask(projectileId);
        BukkitTask task = new BukkitRunnable() {
            private int livedTicks = 0;

            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isDead()) {
                    stopTrailTask(projectileId);
                    return;
                }
                livedTicks++;
                if (livedTicks >= GUN_ARROW_MAX_LIFETIME_TICKS) {
                    arrow.remove();
                    stopTrailTask(projectileId);
                    return;
                }
                spawnProjectileTrail(arrow);
                if (arrow.isOnGround()) {
                    arrow.remove();
                    stopTrailTask(projectileId);
                }
            }
        }.runTaskTimer(MurderPlugin.getInstance(), 0L, 1L);
        gunTrailTasks.put(projectileId, task);
    }

    private void stopTrailTask(UUID projectileId) {
        BukkitTask task = gunTrailTasks.remove(projectileId);
        if (task != null) {
            task.cancel();
        }
    }

    private boolean shouldUseReloadCooldown(Player player, GameSession session) {
        return player != null
                && arena.isPlaying(player)
                && session != null
                && session.getAlivePlayers().contains(player.getUniqueId());
    }

    private boolean isOnCooldown(Player player) {
        Long cooldownEnd = gunCooldownEndAt.get(player.getUniqueId());
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }

    private void startReloadCooldown(Player shooter, GameSession session) {
        UUID playerId = shooter.getUniqueId();
        long cooldownDuration = resolveCooldownDuration(playerId);
        long cooldownEndAt = System.currentTimeMillis() + cooldownDuration;
        gunCooldownEndAt.put(playerId, cooldownEndAt);
        shooter.setExp(0.0F);
        stopReloadTask(playerId);

        BukkitTask reloadTask = new BukkitRunnable() {
            @Override
            public void run() {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline() || !shouldUseReloadCooldown(player, session)) {
                    clearCooldown(playerId, false);
                    return;
                }
                Long endAt = gunCooldownEndAt.get(playerId);
                if (endAt == null) {
                    clearCooldown(playerId, true);
                    return;
                }
                long remaining = Math.max(0L, endAt - System.currentTimeMillis());
                float progress = 1.0F - (float) remaining / (float) cooldownDuration;
                player.setExp(Math.max(0.0F, Math.min(1.0F, progress)));
                if (remaining == 0L) {
                    clearCooldown(playerId, true);
                }
            }
        }.runTaskTimer(MurderPlugin.getInstance(), 1L, 1L);

        gunReloadTasks.put(playerId, reloadTask);
    }

    private long resolveCooldownDuration(UUID playerId) {
        int upgradeLevel = getGunUpgradeLevel(playerId);
        double reductionFactor = Math.pow(UPGRADED_COOLDOWN_MULTIPLIER, Math.max(0, upgradeLevel));
        return Math.max(1L, Math.round(BASE_GUN_COOLDOWN_MILLIS * reductionFactor));
    }

    private void stopReloadTask(UUID playerId) {
        BukkitTask task = gunReloadTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void clearCooldown(UUID playerId, boolean markReady) {
        stopReloadTask(playerId);
        gunCooldownEndAt.remove(playerId);
        if (!markReady) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.setExp(1.0F);
        }
    }

    private void tryEliminateHitPlayer(Player shooter, Player victim, GameSession session) {
        if (session == null || shooter.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (!arena.isPlaying(shooter) || !arena.isPlaying(victim)) {
            return;
        }
        if (!session.getAlivePlayers().contains(shooter.getUniqueId())) {
            return;
        }

        Role shooterRole = session.getRole(shooter.getUniqueId());
        Role victimRole = session.getRole(victim.getUniqueId());
        String shooterIdentity = resolveIdentityDisplayName(shooter);
        String victimIdentity = resolveIdentityDisplayName(victim);
        boolean eliminated = session.eliminatePlayer(victim, shooter);
        if (!eliminated) {
            return;
        }
        if (isInnocent(shooterRole) && isInnocent(victimRole)) {
            arena.sendArenaMessage(shooterIdentity + " &7shot the innocent bystander " + victimIdentity + "&7!");
            shooter.sendMessage(TextUtil.component("&4Continuing to kill bystanders will make it harder for you to become the murderer or to get a gun!"));
            applyInnocentFriendlyFirePenalty(shooter);
        }
    }

    private void spawnProjectileTrail(Arrow arrow) {
        if (arrow.getWorld() == null) {
            return;
        }
        arrow.getWorld().spawnParticle(GUN_TRAIL_PARTICLE, arrow.getLocation(), 2, 0.025D, 0.025D, 0.025D, 0.0D);
    }

    private static Particle resolveTrailParticle() {
        try {
            return Particle.valueOf("FIREWORK");
        } catch (IllegalArgumentException ignored) {
        }
        try {
            return Particle.valueOf("FIREWORKS_SPARK");
        } catch (IllegalArgumentException ignored) {
        }
        return Particle.CRIT;
    }

    private void applyInnocentFriendlyFirePenalty(Player shooter) {
        if (shooter == null || !shooter.isOnline()) {
            return;
        }
        resetGunUpgradeProgress(shooter.getUniqueId());
        long blockedUntilAt = System.currentTimeMillis() + INNOCENT_GUN_PICKUP_LOCK_MILLIS;
        dropGunFromPlayer(shooter, shooter.getUniqueId(), blockedUntilAt);
        clearCooldown(shooter.getUniqueId(), true);
        shooter.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, INNOCENT_SHOOTER_BLINDNESS_TICKS, 0, false, false, false));
    }

    private boolean dropGunFromPlayer(Player player, UUID blockedPickerId, long blockedUntilAt) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        ItemStack droppedGunStack = removeOneGunFromInventory(player);
        if (droppedGunStack == null || droppedGunStack.getType().isAir()) {
            return false;
        }

        Location location = player.getLocation();
        Item droppedGun = location.getWorld().dropItemNaturally(location, droppedGunStack);
        droppedGunItems.put(droppedGun.getUniqueId(), new DroppedGunData(blockedPickerId, blockedUntilAt));
        return true;
    }

    private ItemStack removeOneGunFromInventory(Player player) {
        if (player == null) {
            return null;
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack slotItem = player.getInventory().getItem(slot);
            if (!gameManager.getGunManager().isGunItem(slotItem)) {
                continue;
            }
            ItemStack oneGun = slotItem.clone();
            oneGun.setAmount(1);

            int amount = slotItem.getAmount();
            if (amount <= 1) {
                player.getInventory().setItem(slot, null);
            } else {
                slotItem.setAmount(amount - 1);
                player.getInventory().setItem(slot, slotItem);
            }
            return oneGun;
        }
        return null;
    }

    private boolean giveGunVersion(Player player, int version) {
        if (player == null || !player.isOnline() || player.getWorld() == null) {
            return false;
        }
        ItemStack gun = gameManager.getGunManager().createGunItemVersion(version);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(gun);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        return true;
    }

    private ItemStack createGunVersionStack(int amount, int version) {
        ItemStack upgraded = gameManager.getGunManager().createGunItemVersion(version);
        upgraded.setAmount(Math.max(1, amount));
        return upgraded;
    }

    private void resetGunUpgradeProgress(UUID playerId) {
        if (playerId == null) {
            return;
        }
        gunUpgradeLevels.remove(playerId);
    }

    private boolean isInnocent(Role role) {
        return role == Role.BYSTANDER || role == Role.DETECTIVE;
    }

    private String resolveIdentityDisplayName(Player player) {
        if (player == null) {
            return "&fUnknown";
        }
        String identityDisplay = gameManager.getSecretIdentityManager().getCurrentIdentityDisplayName(player.getUniqueId());
        if (identityDisplay != null && !identityDisplay.isBlank()) {
            return identityDisplay;
        }
        String colored = gameManager.getSecretIdentityManager().getColoredName(player);
        if (colored != null && !colored.isBlank()) {
            return colored;
        }
        return "&f" + player.getName();
    }

    private Item resolveItem(UUID entityId) {
        if (entityId == null) {
            return null;
        }
        org.bukkit.entity.Entity entity = Bukkit.getEntity(entityId);
        if (entity instanceof Item item) {
            return item;
        }
        return null;
    }

    private Set<org.bukkit.World> collectArenaWorlds() {
        Set<org.bukkit.World> worlds = new HashSet<>();
        if (arena.getSpawnLocation() != null && arena.getSpawnLocation().getWorld() != null) {
            worlds.add(arena.getSpawnLocation().getWorld());
        }
        if (arena.getSpawnSpots() != null) {
            for (Location spawn : arena.getSpawnSpots()) {
                if (spawn != null && spawn.getWorld() != null) {
                    worlds.add(spawn.getWorld());
                }
            }
        }
        for (UUID playerId : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getWorld() != null) {
                worlds.add(player.getWorld());
            }
        }
        return worlds;
    }
}
