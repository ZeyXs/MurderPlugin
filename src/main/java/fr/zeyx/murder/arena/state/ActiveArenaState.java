package fr.zeyx.murder.arena.state;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.task.ActiveArenaTask;
import fr.zeyx.murder.game.GameSession;
import fr.zeyx.murder.game.Role;
import fr.zeyx.murder.game.service.TabCompletionService;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChatTabCompleteEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveArenaState extends PlayingArenaState {

    private static final double GUN_ARROW_SPEED = 4D;
    private static final double KNIFE_THROW_SPEED = 4D;
    private static final float GUN_SOUND_VOLUME = 1.0F;
    private static final float GUN_SOUND_PITCH = 2.0F;
    private static final long GUN_COOLDOWN_MILLIS = 2000L;
    private static final int GUN_ARROW_MAX_LIFETIME_TICKS = 20 * 3;
    private static final long KNIFE_RETURN_DELAY_TICKS = 20L * 20L;
    private static final Particle GUN_TRAIL_PARTICLE = resolveTrailParticle();
    private static final float KNIFE_THROW_SOUND_VOLUME = 1.0F;
    private static final float KNIFE_THROW_SOUND_PITCH = 0.6F;
    private static final int MELEE_BLOOD_PARTICLE_COUNT = 10;
    private static final Component MURDERER_KNIFE_NAME = ChatUtil.itemComponent("&7&oKnife", true);

    private final GameManager gameManager;
    private final Arena arena;
    private final TabCompletionService tabCompletionService;
    private final Map<UUID, BukkitTask> gunTrailTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> gunCooldownEndAt = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> gunReloadTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> thrownKnifeTasks = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> thrownKnifeOwners = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> thrownKnifeItems = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> droppedKnifeOwners = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> droppedKnifeReturnTasks = new ConcurrentHashMap<>();
    private final Set<UUID> handledThrownKnifeProjectiles = ConcurrentHashMap.newKeySet();
    private ActiveArenaTask activeArenaTask;
    private GameSession session;

    public ActiveArenaState(GameManager gameManager, Arena arena) {
        super(gameManager, arena);
        this.gameManager = gameManager;
        this.arena = arena;
        this.tabCompletionService = new TabCompletionService(gameManager.getSecretIdentityManager());
    }

    @Override
    public void onEnable() {
        super.onEnable();

        session = new GameSession(gameManager, arena);
        session.start();

        activeArenaTask = new ActiveArenaTask(gameManager, arena, this, session);
        activeArenaTask.runTaskTimer(MurderPlugin.getInstance(), 0, 1);
    }

    @Override
    public void onDisable() {
        if (activeArenaTask != null) {
            activeArenaTask.cancel();
        }
        clearGunRuntimeState();
        super.onDisable();
    }

    public GameSession getSession() {
        return session;
    }

    @Override
    protected boolean useSecretIdentityInChat() {
        return true;
    }

    @Override
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!arena.isPlaying(player)) {
            return;
        }
        if (session != null) {
            session.handlePlayerDisconnect(player);
        }
        arena.removePlayer(player, gameManager);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!arena.isPlaying(player)) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!event.hasItem()) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!event.getItem().hasItemMeta()) {
            return;
        }
        if (handleKnifeThrow(event, player)) {
            return;
        }
        if (handleGunInteract(event, player)) {
            return;
        }

        Component itemName = event.getItem().getItemMeta().displayName();
        if (itemName == null || session == null) {
            return;
        }

        String legacyName = LegacyComponentSerializer.legacySection().serialize(itemName);
        if (session.handleInteract(player, itemName, legacyName)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow) || !gameManager.getGunManager().isGunProjectile(arrow)) {
            return;
        }
        if (event.getHitEntity() instanceof Player victim && arrow.getShooter() instanceof Player shooter) {
            tryEliminateHitPlayer(shooter, victim);
        }
        if (event.getHitEntity() != null || event.getHitBlock() != null) {
            arrow.remove();
        }
        stopTrailTask(arrow.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !arena.isPlaying(victim)) {
            return;
        }
        if (event.getDamager() instanceof Player attacker && isAliveMurdererWithKnife(attacker)) {
            event.setCancelled(true);
            Location victimLocation = victim.getLocation();
            if (session.eliminatePlayer(victim, attacker)) {
                spawnMeleeBlood(victimLocation);
            }
        }
    }

    @EventHandler
    public void onKnifePickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        UUID knifeItemId = event.getItem().getUniqueId();
        UUID ownerId = droppedKnifeOwners.get(knifeItemId);
        if (ownerId == null) {
            return;
        }
        if (!player.getUniqueId().equals(ownerId) || !isAliveMurderer(player)) {
            event.setCancelled(true);
            return;
        }
        droppedKnifeOwners.remove(knifeItemId);
        cancelKnifeReturnTask(knifeItemId);
    }

    @EventHandler
    public void onKnifeDespawn(ItemDespawnEvent event) {
        UUID knifeItemId = event.getEntity().getUniqueId();
        droppedKnifeOwners.remove(knifeItemId);
        cancelKnifeReturnTask(knifeItemId);
    }

    @Override
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !arena.isPlaying(player)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            player.setFallDistance(0f);
        }
        event.setCancelled(true);
        event.setDamage(0.0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerChatTabComplete(PlayerChatTabCompleteEvent event) {
        if (session == null || !arena.isPlaying(event.getPlayer())) {
            return;
        }
        tabCompletionService.handlePlayerChatTabComplete(event, arena);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        if (session == null || event.isCommand() || !(event.getSender() instanceof Player player) || !arena.isPlaying(player)) {
            return;
        }
        tabCompletionService.handleAsyncTabComplete(event, arena);
    }

    private boolean handleGunInteract(PlayerInteractEvent event, Player shooter) {
        ItemStack usedItem = event.getItem();
        if (!gameManager.getGunManager().isGunItem(usedItem)) {
            return false;
        }

        event.setCancelled(true);
        if (!shouldUseReloadCooldown(shooter)) {
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

        startReloadCooldown(shooter);
        startTrailTask(arrow);
        return true;
    }

    private boolean handleKnifeThrow(PlayerInteractEvent event, Player shooter) {
        ItemStack usedItem = event.getItem();
        if (!isKnifeItem(usedItem)) {
            return false;
        }
        event.setCancelled(true);
        if (!isAliveMurderer(shooter)) {
            return true;
        }

        ItemStack thrownKnifeItem = usedItem.clone();
        thrownKnifeItem.setAmount(1);
        Item thrownKnife = shooter.getWorld().dropItem(shooter.getEyeLocation(), thrownKnifeItem.clone());
        thrownKnife.setVelocity(shooter.getEyeLocation().getDirection().normalize().multiply(KNIFE_THROW_SPEED));
        thrownKnife.setPickupDelay(Integer.MAX_VALUE);

        thrownKnifeOwners.put(thrownKnife.getUniqueId(), shooter.getUniqueId());
        thrownKnifeItems.put(thrownKnife.getUniqueId(), thrownKnifeItem.clone());
        startThrownKnifeTask(thrownKnife);

        consumeHeldItem(shooter);
        playKnifeThrowSoundToArena();
        return true;
    }

    private void startThrownKnifeTask(Item thrownKnife) {
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
                if (livedTicks >= GUN_ARROW_MAX_LIFETIME_TICKS) {
                    handleThrownKnifeCollision(thrownKnife, thrownKnife.getLocation(), null);
                    return;
                }
                spawnProjectileTrail(thrownKnife.getLocation());

                Location currentLocation = thrownKnife.getLocation();
                Vector movement = currentLocation.toVector().subtract(previousLocation.toVector());
                double distance = movement.length();
                if (distance <= 0.0D) {
                    if (thrownKnife.isOnGround()) {
                        handleThrownKnifeCollision(thrownKnife, currentLocation, null);
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
                    handleThrownKnifeCollision(thrownKnife, hitLocation, null);
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
                    handleThrownKnifeCollision(thrownKnife, hitPlayer.getLocation(), hitPlayer);
                    return;
                }

                if (thrownKnife.isOnGround()) {
                    handleThrownKnifeCollision(thrownKnife, currentLocation, null);
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

    private void handleThrownKnifeCollision(Item thrownKnife, Location collisionLocation, Player hitVictim) {
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

        if (hitVictim != null && ownerId != null) {
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null && isAliveMurderer(owner) && arena.isPlaying(hitVictim)) {
                Location victimLocation = hitVictim.getLocation();
                if (session.eliminatePlayer(hitVictim, owner)) {
                    spawnMeleeBlood(victimLocation);
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
                spawnProjectileTrail(arrow.getLocation());
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

    private boolean shouldUseReloadCooldown(Player player) {
        return player != null
                && arena.isPlaying(player)
                && session != null
                && session.getAlivePlayers().contains(player.getUniqueId());
    }

    private boolean isOnCooldown(Player player) {
        Long cooldownEnd = gunCooldownEndAt.get(player.getUniqueId());
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }

    private void startReloadCooldown(Player shooter) {
        UUID playerId = shooter.getUniqueId();
        long cooldownEndAt = System.currentTimeMillis() + GUN_COOLDOWN_MILLIS;
        gunCooldownEndAt.put(playerId, cooldownEndAt);
        shooter.setExp(0.0F);
        stopReloadTask(playerId);

        BukkitTask reloadTask = new BukkitRunnable() {
            @Override
            public void run() {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline() || !shouldUseReloadCooldown(player)) {
                    clearCooldown(playerId, false);
                    return;
                }
                Long endAt = gunCooldownEndAt.get(playerId);
                if (endAt == null) {
                    clearCooldown(playerId, true);
                    return;
                }
                long remaining = Math.max(0L, endAt - System.currentTimeMillis());
                float progress = 1.0F - (float) remaining / (float) GUN_COOLDOWN_MILLIS;
                player.setExp(Math.max(0.0F, Math.min(1.0F, progress)));
                if (remaining == 0L) {
                    clearCooldown(playerId, true);
                }
            }
        }.runTaskTimer(MurderPlugin.getInstance(), 1L, 1L);

        gunReloadTasks.put(playerId, reloadTask);
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

    private void tryEliminateHitPlayer(Player shooter, Player victim) {
        if (session == null || shooter.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (!arena.isPlaying(shooter) || !arena.isPlaying(victim)) {
            return;
        }
        if (!session.getAlivePlayers().contains(shooter.getUniqueId())) {
            return;
        }
        session.eliminatePlayer(victim, shooter);
    }

    private boolean isAliveMurderer(Player player) {
        if (player == null || session == null || !arena.isPlaying(player)) {
            return false;
        }
        if (!session.getAlivePlayers().contains(player.getUniqueId())) {
            return false;
        }
        return session.getRole(player.getUniqueId()) == Role.MURDERER;
    }

    private boolean isAliveMurdererWithKnife(Player player) {
        return isAliveMurderer(player) && isKnifeItem(player.getInventory().getItemInMainHand());
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
        if (location == null || location.getWorld() == null || knifeItem == null || knifeItem.getType() == Material.AIR) {
            return;
        }
        if (ownerId == null) {
            return;
        }
        Item droppedKnife = location.getWorld().dropItemNaturally(location, knifeItem.clone());
        UUID knifeItemId = droppedKnife.getUniqueId();
        droppedKnifeOwners.put(knifeItemId, ownerId);
        scheduleKnifeReturn(knifeItemId);
    }

    public void clearKnifeDropsFromFloor() {
        for (UUID knifeItemId : Set.copyOf(droppedKnifeOwners.keySet())) {
            Item knifeItem = findKnifeItem(knifeItemId);
            if (knifeItem != null) {
                knifeItem.remove();
            }
            cancelKnifeReturnTask(knifeItemId);
            droppedKnifeOwners.remove(knifeItemId);
        }
    }

    public void clearAllKnifeItems() {
        clearActiveThrownKnifeEntities();
        clearKnifeDropsFromFloor();
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
        location.getWorld().spawnParticle(GUN_TRAIL_PARTICLE, location, 2, 0.025D, 0.025D, 0.025D, 0.0D);
    }

    private void spawnMeleeBlood(Location location) {
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

    private void clearGunRuntimeState() {
        for (BukkitTask task : gunTrailTasks.values()) {
            task.cancel();
        }
        gunTrailTasks.clear();
        for (BukkitTask task : gunReloadTasks.values()) {
            task.cancel();
        }
        gunReloadTasks.clear();
        gunCooldownEndAt.clear();
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
