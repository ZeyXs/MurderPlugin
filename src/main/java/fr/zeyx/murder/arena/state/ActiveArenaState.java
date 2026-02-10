package fr.zeyx.murder.arena.state;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.task.ActiveArenaTask;
import fr.zeyx.murder.game.GameSession;
import fr.zeyx.murder.game.session.SessionTabCompletionService;
import fr.zeyx.murder.manager.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChatTabCompleteEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveArenaState extends PlayingArenaState {

    private static final double GUN_ARROW_SPEED = 4D;
    private static final float GUN_SOUND_VOLUME = 1.0F;
    private static final float GUN_SOUND_PITCH = 2.0F;
    private static final long GUN_COOLDOWN_MILLIS = 3000L;
    private static final int GUN_ARROW_MAX_LIFETIME_TICKS = 20 * 3;
    private static final Particle GUN_TRAIL_PARTICLE = resolveTrailParticle();

    private final GameManager gameManager;
    private final Arena arena;
    private final SessionTabCompletionService tabCompletionService;
    private final Map<UUID, BukkitTask> gunTrailTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> gunCooldownEndAt = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> gunReloadTasks = new ConcurrentHashMap<>();
    private ActiveArenaTask activeArenaTask;
    private GameSession session;

    public ActiveArenaState(GameManager gameManager, Arena arena) {
        super(gameManager, arena);
        this.gameManager = gameManager;
        this.arena = arena;
        this.tabCompletionService = new SessionTabCompletionService(gameManager.getSecretIdentityManager());
    }

    @Override
    public void onEnable() {
        super.onEnable();

        session = new GameSession(gameManager, arena);
        session.start();

        activeArenaTask = new ActiveArenaTask(gameManager, arena, session);
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
                arrow.getWorld().spawnParticle(GUN_TRAIL_PARTICLE, arrow.getLocation(), 2, 0.025D, 0.025D, 0.025D, 0.0D);
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
