package fr.zeyx.murder.manager;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.state.ActiveArenaState;
import fr.zeyx.murder.game.GameSession;
import fr.zeyx.murder.util.ChatUtil;
import fr.zeyx.murder.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GunManager implements Listener {

    public static final String DETECTIVE_GUN_NAME = "&7&oGun";

    private static final double GUN_ARROW_SPEED = 4D;
    private static final float GUN_SOUND_VOLUME = 1.0F;
    private static final float GUN_SOUND_PITCH = 2.0F;
    private static final int GUN_ARROW_MAX_LIFETIME_TICKS = 20 * 3;
    private static final Particle GUN_TRAIL_PARTICLE = resolveTrailParticle();

    private final GameManager gameManager;
    private final NamespacedKey gunItemKey;
    private final NamespacedKey gunProjectileKey;
    private final Map<UUID, BukkitTask> gunTrailTasks = new ConcurrentHashMap<>();

    public GunManager(GameManager gameManager) {
        this.gameManager = gameManager;
        this.gunItemKey = new NamespacedKey(MurderPlugin.getInstance(), "gun_item");
        this.gunProjectileKey = new NamespacedKey(MurderPlugin.getInstance(), "gun_projectile");
    }

    public ItemStack createGunItem() {
        ItemStack gun = new ItemBuilder(Material.WOODEN_HOE).setName(ChatUtil.itemComponent(DETECTIVE_GUN_NAME, true)).toItemStack();
        ItemMeta gunMeta = gun.getItemMeta();
        if (gunMeta == null) {
            return gun;
        }
        markItemAsGun(gunMeta);
        applyInstantAttackSpeed(gunMeta);
        gun.setItemMeta(gunMeta);
        return gun;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack usedItem = event.getItem();
        if (!isGunItem(usedItem)) {
            return;
        }

        event.setCancelled(true);
        Player shooter = event.getPlayer();
        Arrow arrow = shooter.launchProjectile(Arrow.class);
        Vector velocity = shooter.getEyeLocation().getDirection().normalize().multiply(GUN_ARROW_SPEED);
        arrow.setVelocity(velocity);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.getPersistentDataContainer().set(gunProjectileKey, PersistentDataType.BYTE, (byte) 1);
        shooter.getWorld().playSound(shooter.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, GUN_SOUND_VOLUME, GUN_SOUND_PITCH);
        startTrailTask(arrow);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow) || !isGunProjectile(arrow)) {
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

    public void shutdown() {
        for (BukkitTask task : gunTrailTasks.values()) {
            task.cancel();
        }
        gunTrailTasks.clear();
    }

    private boolean isGunItem(ItemStack item) {
        if (item == null || item.getType() != Material.WOODEN_HOE || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Byte marker = meta.getPersistentDataContainer().get(gunItemKey, PersistentDataType.BYTE);
        if (marker != null && marker == (byte) 1) {
            return true;
        }
        return ChatUtil.itemComponent(DETECTIVE_GUN_NAME, true).equals(meta.displayName());
    }

    private boolean isGunProjectile(Projectile projectile) {
        Byte marker = projectile.getPersistentDataContainer().get(gunProjectileKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void markItemAsGun(ItemMeta meta) {
        meta.getPersistentDataContainer().set(gunItemKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void applyInstantAttackSpeed(ItemMeta meta) {
        NamespacedKey key = new NamespacedKey(MurderPlugin.getInstance(), "gun_instant_attack_speed");
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

    private void tryEliminateHitPlayer(Player shooter, Player victim) {
        if (shooter.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        Optional<Arena> shooterArena = gameManager.getArenaManager().getCurrentArena(shooter);
        if (shooterArena.isEmpty()) {
            return;
        }
        Arena arena = shooterArena.get();
        if (!arena.isPlaying(victim) || !(arena.getArenaState() instanceof ActiveArenaState activeArenaState)) {
            return;
        }
        GameSession session = activeArenaState.getSession();
        if (session == null) {
            return;
        }
        session.eliminatePlayer(victim, shooter);
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
