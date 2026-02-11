package fr.zeyx.murder.game.feature;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.game.GameSession;
import fr.zeyx.murder.game.Role;
import fr.zeyx.murder.util.ChatUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KnifeFeature {

    private static final double KNIFE_THROW_SPEED = 4D;
    private static final int KNIFE_THROW_MAX_LIFETIME_TICKS = 20 * 3;
    private static final long KNIFE_RETURN_DELAY_TICKS = 20L * 20L;
    private static final float KNIFE_THROW_SOUND_VOLUME = 1.0F;
    private static final float KNIFE_THROW_SOUND_PITCH = 0.6F;
    private static final int MELEE_BLOOD_PARTICLE_COUNT = 10;
    private static final Component MURDERER_KNIFE_NAME = ChatUtil.itemComponent("&7&oKnife", true);
    private static final Particle KNIFE_TRAIL_PARTICLE = resolveTrailParticle();

    private final Arena arena;
    private final Map<UUID, BukkitTask> thrownKnifeTasks = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> thrownKnifeOwners = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> thrownKnifeItems = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> droppedKnifeOwners = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> droppedKnifeReturnTasks = new ConcurrentHashMap<>();
    private final Set<UUID> handledThrownKnifeProjectiles = ConcurrentHashMap.newKeySet();

    public KnifeFeature(Arena arena) {
        this.arena = arena;
    }

    public boolean handleThrowInteract(PlayerInteractEvent event, Player shooter, GameSession session) {
        ItemStack usedItem = event.getItem();
        if (!isKnifeItem(usedItem)) {
            return false;
        }
        event.setCancelled(true);
        if (!isAliveMurderer(shooter, session)) {
            return true;
        }

        ItemStack thrownKnifeItem = usedItem.clone();
        thrownKnifeItem.setAmount(1);
        Item thrownKnife = shooter.getWorld().dropItem(shooter.getEyeLocation(), thrownKnifeItem.clone());
        thrownKnife.setVelocity(shooter.getEyeLocation().getDirection().normalize().multiply(KNIFE_THROW_SPEED));
        thrownKnife.setPickupDelay(Integer.MAX_VALUE);

        thrownKnifeOwners.put(thrownKnife.getUniqueId(), shooter.getUniqueId());
        thrownKnifeItems.put(thrownKnife.getUniqueId(), thrownKnifeItem.clone());
        startThrownKnifeTask(thrownKnife, session);

        consumeHeldItem(shooter);
        playKnifeThrowSoundToArena();
        return true;
    }

    public void onDamageByEntity(EntityDamageByEntityEvent event, GameSession session) {
        if (!(event.getEntity() instanceof Player victim) || !arena.isPlaying(victim)) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker) || !isAliveMurdererWithKnife(attacker, session)) {
            return;
        }
        event.setCancelled(true);
        Location victimLocation = victim.getLocation();
        if (session != null && session.eliminatePlayer(victim, attacker)) {
            spawnBlood(victimLocation);
        }
    }

    public void onKnifePickup(EntityPickupItemEvent event, GameSession session) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        UUID knifeItemId = event.getItem().getUniqueId();
        UUID ownerId = droppedKnifeOwners.get(knifeItemId);
        if (ownerId == null) {
            return;
        }
        if (!player.getUniqueId().equals(ownerId) || !isAliveMurderer(player, session)) {
            event.setCancelled(true);
            return;
        }
        droppedKnifeOwners.remove(knifeItemId);
        cancelKnifeReturnTask(knifeItemId);
    }

    public void onKnifeDespawn(ItemDespawnEvent event) {
        UUID knifeItemId = event.getEntity().getUniqueId();
        droppedKnifeOwners.remove(knifeItemId);
        cancelKnifeReturnTask(knifeItemId);
    }

    public void clearAllKnifeItems() {
        clearActiveThrownKnifeEntities();
        clearKnifeDropsFromFloor();
    }

    public void clearRuntimeState() {
        for (BukkitTask task : thrownKnifeTasks.values()) {
            task.cancel();
        }
        thrownKnifeTasks.clear();
        clearAllKnifeItems();
        for (BukkitTask task : droppedKnifeReturnTasks.values()) {
            task.cancel();
        }
        droppedKnifeReturnTasks.clear();
        droppedKnifeOwners.clear();
        handledThrownKnifeProjectiles.clear();
    }

    private void startThrownKnifeTask(Item thrownKnife, GameSession session) {
        UUID knifeEntityId = thrownKnife.getUniqueId();
        stopThrownKnifeTask(knifeEntityId);

        BukkitTask knifeTask = new BukkitRunnable() {
            private int livedTicks = 0;
            private Location previousLocation = thrownKnife.getLocation();

            @Override
            public void run() {
                if (!thrownKnife.isValid() || thrownKnife.isDead()) {
                    cleanupThrownKnifeProjectile(knifeEntityId);
                    return;
                }
                livedTicks++;
                if (livedTicks >= KNIFE_THROW_MAX_LIFETIME_TICKS) {
                    handleThrownKnifeCollision(thrownKnife, thrownKnife.getLocation(), null, session);
                    return;
                }
                spawnProjectileTrail(thrownKnife.getLocation());

                Location currentLocation = thrownKnife.getLocation();
                Vector movement = currentLocation.toVector().subtract(previousLocation.toVector());
                double distance = movement.length();
                if (distance <= 0.0D) {
                    if (thrownKnife.isOnGround()) {
                        handleThrownKnifeCollision(thrownKnife, currentLocation, null, session);
                        return;
                    }
                    previousLocation = currentLocation;
                    return;
                }

                Vector direction = movement.clone().normalize();
                RayTraceResult blockHit = thrownKnife.getWorld().rayTraceBlocks(
                        previousLocation,
                        direction,
                        distance,
                        FluidCollisionMode.NEVER,
                        false
                );
                if (blockHit != null) {
                    Location hitLocation = blockHit.getHitPosition().toLocation(thrownKnife.getWorld());
                    handleThrownKnifeCollision(thrownKnife, hitLocation, null, session);
                    return;
                }

                UUID ownerId = readThrownKnifeOwner(knifeEntityId);
                RayTraceResult entityHit = thrownKnife.getWorld().rayTraceEntities(
                        previousLocation,
                        direction,
                        distance,
                        0.2D,
                        entity -> entity instanceof Player hitPlayer
                                && arena.isPlaying(hitPlayer)
                                && (ownerId == null || !ownerId.equals(hitPlayer.getUniqueId()))
                );
                if (entityHit != null && entityHit.getHitEntity() instanceof Player hitPlayer) {
                    handleThrownKnifeCollision(thrownKnife, hitPlayer.getLocation(), hitPlayer, session);
                    return;
                }

                if (thrownKnife.isOnGround()) {
                    handleThrownKnifeCollision(thrownKnife, currentLocation, null, session);
                    return;
                }

                previousLocation = currentLocation;
            }
        }.runTaskTimer(MurderPlugin.getInstance(), 0L, 1L);

        thrownKnifeTasks.put(knifeEntityId, knifeTask);
    }

    private void stopThrownKnifeTask(UUID knifeEntityId) {
        BukkitTask task = thrownKnifeTasks.remove(knifeEntityId);
        if (task != null) {
            task.cancel();
        }
    }

    private void handleThrownKnifeCollision(Item thrownKnife, Location collisionLocation, Player hitVictim, GameSession session) {
        UUID knifeEntityId = thrownKnife.getUniqueId();
        if (!handledThrownKnifeProjectiles.add(knifeEntityId)) {
            return;
        }

        stopThrownKnifeTask(knifeEntityId);
        UUID ownerId = thrownKnifeOwners.remove(knifeEntityId);
        ItemStack knifeItem = thrownKnifeItems.remove(knifeEntityId);
        if (knifeItem == null || knifeItem.getType() == Material.AIR) {
            knifeItem = thrownKnife.getItemStack().clone();
        }

        if (hitVictim != null && ownerId != null && session != null) {
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null && isAliveMurderer(owner, session) && arena.isPlaying(hitVictim)) {
                Location victimLocation = hitVictim.getLocation();
                if (session.eliminatePlayer(hitVictim, owner)) {
                    spawnBlood(victimLocation);
                }
            }
        }

        if (knifeItem.getType() != Material.AIR) {
            dropKnifeItem(collisionLocation == null ? thrownKnife.getLocation() : collisionLocation, ownerId, knifeItem);
        }

        thrownKnife.remove();
    }

    private void cleanupThrownKnifeProjectile(UUID knifeEntityId) {
        stopThrownKnifeTask(knifeEntityId);
        thrownKnifeOwners.remove(knifeEntityId);
        thrownKnifeItems.remove(knifeEntityId);
    }

    private boolean isAliveMurderer(Player player, GameSession session) {
        if (player == null || session == null || !arena.isPlaying(player)) {
            return false;
        }
        if (!session.getAlivePlayers().contains(player.getUniqueId())) {
            return false;
        }
        return session.getRole(player.getUniqueId()) == Role.MURDERER;
    }

    private boolean isAliveMurdererWithKnife(Player player, GameSession session) {
        return isAliveMurderer(player, session) && isKnifeItem(player.getInventory().getItemInMainHand());
    }

    private boolean isKnifeItem(ItemStack item) {
        if (item == null || item.getType() != Material.WOODEN_SWORD || !item.hasItemMeta()) {
            return false;
        }
        Component itemName = item.getItemMeta().displayName();
        return itemName != null && itemName.equals(MURDERER_KNIFE_NAME);
    }

    private UUID readThrownKnifeOwner(UUID projectileId) {
        return projectileId == null ? null : thrownKnifeOwners.get(projectileId);
    }

    private void consumeHeldItem(Player shooter) {
        ItemStack held = shooter.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            return;
        }
        int amount = held.getAmount();
        if (amount <= 1) {
            shooter.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            return;
        }
        held.setAmount(amount - 1);
        shooter.getInventory().setItemInMainHand(held);
    }

    private void playKnifeThrowSoundToArena() {
        for (UUID playerId : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            player.playSound(player.getLocation(), Sound.ENTITY_CREEPER_HURT, KNIFE_THROW_SOUND_VOLUME, KNIFE_THROW_SOUND_PITCH);
        }
    }

    private void dropKnifeItem(Location location, UUID ownerId, ItemStack knifeItem) {
        if (location == null || location.getWorld() == null || knifeItem == null || knifeItem.getType() == Material.AIR || ownerId == null) {
            return;
        }
        Item droppedKnife = location.getWorld().dropItemNaturally(location, knifeItem.clone());
        UUID knifeItemId = droppedKnife.getUniqueId();
        droppedKnifeOwners.put(knifeItemId, ownerId);
        scheduleKnifeReturn(knifeItemId);
    }

    private void clearKnifeDropsFromFloor() {
        for (UUID knifeItemId : Set.copyOf(droppedKnifeOwners.keySet())) {
            Item knifeItem = findKnifeItem(knifeItemId);
            if (knifeItem != null) {
                knifeItem.remove();
            }
            cancelKnifeReturnTask(knifeItemId);
            droppedKnifeOwners.remove(knifeItemId);
        }
    }

    private void scheduleKnifeReturn(UUID knifeItemId) {
        if (knifeItemId == null) {
            return;
        }
        cancelKnifeReturnTask(knifeItemId);
        BukkitTask returnTask = new BukkitRunnable() {
            @Override
            public void run() {
                UUID ownerId = droppedKnifeOwners.get(knifeItemId);
                Item knifeItem = findKnifeItem(knifeItemId);
                if (ownerId == null || knifeItem == null || !knifeItem.isValid()) {
                    droppedKnifeOwners.remove(knifeItemId);
                    cancelKnifeReturnTask(knifeItemId);
                    return;
                }

                Player owner = Bukkit.getPlayer(ownerId);
                if (owner != null && owner.isOnline()) {
                    ItemStack returningKnife = knifeItem.getItemStack().clone();
                    Map<Integer, ItemStack> leftovers = owner.getInventory().addItem(returningKnife);
                    for (ItemStack leftover : leftovers.values()) {
                        owner.getWorld().dropItemNaturally(owner.getLocation(), leftover);
                    }
                }

                knifeItem.remove();
                droppedKnifeOwners.remove(knifeItemId);
                cancelKnifeReturnTask(knifeItemId);
            }
        }.runTaskLater(MurderPlugin.getInstance(), KNIFE_RETURN_DELAY_TICKS);
        droppedKnifeReturnTasks.put(knifeItemId, returnTask);
    }

    private void cancelKnifeReturnTask(UUID knifeItemId) {
        BukkitTask task = droppedKnifeReturnTasks.remove(knifeItemId);
        if (task != null) {
            task.cancel();
        }
    }

    private Item findKnifeItem(UUID knifeItemId) {
        if (knifeItemId == null) {
            return null;
        }
        org.bukkit.entity.Entity entity = Bukkit.getEntity(knifeItemId);
        if (entity instanceof Item item) {
            return item;
        }
        return null;
    }

    private void spawnProjectileTrail(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().spawnParticle(KNIFE_TRAIL_PARTICLE, location, 2, 0.025D, 0.025D, 0.025D, 0.0D);
    }

    private void spawnBlood(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().spawnParticle(
                Particle.BLOCK,
                location.clone().add(0.0D, 1.0D, 0.0D),
                MELEE_BLOOD_PARTICLE_COUNT,
                0.25D,
                0.45D,
                0.25D,
                Material.REDSTONE_BLOCK.createBlockData()
        );
    }

    private void clearActiveThrownKnifeEntities() {
        Set<UUID> activeThrownIds = new HashSet<>(thrownKnifeOwners.keySet());
        activeThrownIds.addAll(thrownKnifeItems.keySet());
        for (UUID knifeEntityId : activeThrownIds) {
            stopThrownKnifeTask(knifeEntityId);
            Item thrownKnife = findKnifeItem(knifeEntityId);
            if (thrownKnife != null) {
                thrownKnife.remove();
            }
            thrownKnifeOwners.remove(knifeEntityId);
            thrownKnifeItems.remove(knifeEntityId);
        }
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
}
