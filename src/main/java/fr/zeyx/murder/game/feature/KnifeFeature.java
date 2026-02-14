package fr.zeyx.murder.game.feature;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.game.GameSession;
import fr.zeyx.murder.game.QuickChatMenu;
import fr.zeyx.murder.game.Role;
import fr.zeyx.murder.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KnifeFeature {

    private static final double KNIFE_THROW_SPEED = 4D;
    private static final int KNIFE_THROW_MAX_LIFETIME_TICKS = 20 * 3;
    private static final long KNIFE_RETURN_DELAY_TICKS = 20L * 20L;
    private static final double BLOCK_COLLISION_FACE_OFFSET = 0.62D;
    private static final double SAFE_DROP_ELEVATION_OFFSET = 0.05D;
    private static final float KNIFE_THROW_SOUND_VOLUME = 1.0F;
    private static final float KNIFE_THROW_SOUND_PITCH = 0.6F;
    private static final int MELEE_BLOOD_PARTICLE_COUNT = 10;

    private static final String MURDERER_BUY_KNIFE_DISABLED_NAME = "&7&lBuy Knife&r &7• Right Click";
    private static final String MURDERER_BUY_KNIFE_ENABLED_NAME = "&a&lBuy Knife&r &7• Right Click";
    private static final String MURDERER_BUY_KNIFE_DISABLED_LEGACY = ChatColor.translateAlternateColorCodes('&', MURDERER_BUY_KNIFE_DISABLED_NAME);
    private static final String MURDERER_BUY_KNIFE_ENABLED_LEGACY = ChatColor.translateAlternateColorCodes('&', MURDERER_BUY_KNIFE_ENABLED_NAME);

    private static final int MURDERER_BUY_KNIFE_COST = 5;
    private static final int MURDERER_BUY_KNIFE_SLOT = 3;
    private static final Component MURDERER_KNIFE_NAME = TextUtil.itemComponent("&7&oKnife", true);
    private static final Particle KNIFE_TRAIL_PARTICLE = resolveTrailParticle();

    private final Arena arena;
    private final Map<UUID, BukkitTask> thrownKnifeTasks = new ConcurrentHashMap<>();
    private final Map<UUID, ThrownKnifeData> thrownKnifeProjectiles = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> droppedKnifeOwners = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> droppedKnifeReturnTasks = new ConcurrentHashMap<>();
    private final Set<UUID> lockedKnifeItems = ConcurrentHashMap.newKeySet();
    private final Set<UUID> handledThrownKnifeProjectiles = ConcurrentHashMap.newKeySet();

    private static final class ThrownKnifeData {
        private final UUID ownerId;
        private final UUID knifeItemId;
        private final ItemStack knifeItem;

        private ThrownKnifeData(UUID ownerId, UUID knifeItemId, ItemStack knifeItem) {
            this.ownerId = ownerId;
            this.knifeItemId = knifeItemId;
            this.knifeItem = knifeItem;
        }
    }

    public KnifeFeature(Arena arena) {
        this.arena = arena;
    }

    public boolean isBuyKnifeItem(String legacyName) {
        return MURDERER_BUY_KNIFE_DISABLED_LEGACY.equals(legacyName)
                || MURDERER_BUY_KNIFE_ENABLED_LEGACY.equals(legacyName);
    }

    public void updateBuyKnifeItem(UUID murdererId, java.util.List<UUID> alivePlayers, EmeraldFeature emeraldFeature) {
        if (murdererId == null || alivePlayers == null || !alivePlayers.contains(murdererId)) {
            return;
        }
        Player murderer = Bukkit.getPlayer(murdererId);
        if (murderer == null || !murderer.isOnline()) {
            return;
        }
        boolean canBuy = emeraldFeature != null
                && emeraldFeature.getMissingEmeralds(murdererId, MURDERER_BUY_KNIFE_COST) == 0;
        setMurdererBuyKnifeItem(murderer, canBuy);
    }

    public void handleBuyKnifeInteract(Player murderer, GameSession session, EmeraldFeature emeraldFeature) {
        if (murderer == null || session == null || emeraldFeature == null) {
            return;
        }
        if (!isAliveMurderer(murderer, session)) {
            return;
        }
        int missingEmeralds = emeraldFeature.getMissingEmeralds(murderer.getUniqueId(), MURDERER_BUY_KNIFE_COST);
        if (missingEmeralds > 0) {
            sendNeedEmeraldsMessage(murderer, MURDERER_BUY_KNIFE_COST);
            setMurdererBuyKnifeItem(murderer, false);
            return;
        }
        if (!emeraldFeature.trySpendEmeralds(murderer, MURDERER_BUY_KNIFE_COST)) {
            sendNeedEmeraldsMessage(murderer, MURDERER_BUY_KNIFE_COST);
            setMurdererBuyKnifeItem(murderer, false);
            return;
        }
        giveKnifeToMurderer(murderer);
        setMurdererBuyKnifeItem(
                murderer,
                emeraldFeature.getMissingEmeralds(murderer.getUniqueId(), MURDERER_BUY_KNIFE_COST) == 0
        );
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
        Item droppedKnife = spawnKnifeItemAtThrower(shooter, thrownKnifeItem);
        if (droppedKnife == null) {
            return true;
        }

        Arrow projectile = shooter.launchProjectile(Arrow.class);
        Vector throwVelocity = shooter.getEyeLocation().getDirection().normalize().multiply(KNIFE_THROW_SPEED);
        projectile.setVelocity(throwVelocity);
        projectile.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);

        UUID projectileId = projectile.getUniqueId();
        thrownKnifeProjectiles.put(projectileId, new ThrownKnifeData(shooter.getUniqueId(), droppedKnife.getUniqueId(), thrownKnifeItem.clone()));
        startThrownKnifeTask(projectile);

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

    public void onProjectileHit(ProjectileHitEvent event, GameSession session) {
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        UUID projectileId = arrow.getUniqueId();
        ThrownKnifeData knifeData = thrownKnifeProjectiles.get(projectileId);
        if (knifeData == null || !handledThrownKnifeProjectiles.add(projectileId)) {
            return;
        }

        stopThrownKnifeTask(projectileId);
        thrownKnifeProjectiles.remove(projectileId);
        unlockKnifeItemPickup(knifeData.knifeItemId);

        Location collisionLocation = resolveCollisionLocation(arrow, event);
        teleportKnifeItem(knifeData.knifeItemId, collisionLocation);

        if (event.getHitEntity() instanceof Player victim) {
            handleKnifeProjectileVictimHit(knifeData.ownerId, victim, session);
        }

        arrow.remove();
        handledThrownKnifeProjectiles.remove(projectileId);
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
        if (lockedKnifeItems.contains(knifeItemId)) {
            event.setCancelled(true);
            return;
        }
        if (!player.getUniqueId().equals(ownerId) || !isAliveMurderer(player, session)) {
            event.setCancelled(true);
            return;
        }
        droppedKnifeOwners.remove(knifeItemId);
        lockedKnifeItems.remove(knifeItemId);
        cancelKnifeReturnTask(knifeItemId);
    }

    public void onKnifeDespawn(ItemDespawnEvent event) {
        UUID knifeItemId = event.getEntity().getUniqueId();
        droppedKnifeOwners.remove(knifeItemId);
        lockedKnifeItems.remove(knifeItemId);
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
        thrownKnifeProjectiles.clear();
        for (BukkitTask task : droppedKnifeReturnTasks.values()) {
            task.cancel();
        }
        droppedKnifeReturnTasks.clear();
        droppedKnifeOwners.clear();
        lockedKnifeItems.clear();
        handledThrownKnifeProjectiles.clear();
    }

    public int getKnifeCount(Player player) {
        if (player == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isKnifeItem(item)) {
                count += item.getAmount();
            }
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isKnifeItem(offHand)) {
            count += offHand.getAmount();
        }
        return count;
    }

    private Item spawnKnifeItemAtThrower(Player shooter, ItemStack knifeItem) {
        if (shooter == null || shooter.getWorld() == null || knifeItem == null || knifeItem.getType() == Material.AIR) {
            return null;
        }
        Location dropLocation = shooter.getLocation().clone().add(0.0D, 1.0D, 0.0D);
        Item droppedKnife = shooter.getWorld().dropItem(dropLocation, knifeItem.clone());
        droppedKnife.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
        UUID itemId = droppedKnife.getUniqueId();
        droppedKnifeOwners.put(itemId, shooter.getUniqueId());
        lockedKnifeItems.add(itemId);
        scheduleKnifeReturn(itemId);
        return droppedKnife;
    }

    private void startThrownKnifeTask(Arrow projectile) {
        UUID projectileId = projectile.getUniqueId();
        stopThrownKnifeTask(projectileId);

        BukkitTask knifeTask = new BukkitRunnable() {
            private int livedTicks = 0;

            @Override
            public void run() {
                if (!projectile.isValid() || projectile.isDead()) {
                    handleKnifeProjectileTimeout(projectileId);
                    return;
                }
                livedTicks++;
                if (livedTicks >= KNIFE_THROW_MAX_LIFETIME_TICKS) {
                    handleKnifeProjectileTimeout(projectileId);
                    projectile.remove();
                    return;
                }
                spawnProjectileTrail(projectile.getLocation());
                if (projectile.isOnGround()) {
                    Location collisionLocation = projectile.getLocation();
                    handleKnifeProjectileGroundCollision(projectile, collisionLocation);
                }
            }
        }.runTaskTimer(MurderPlugin.getInstance(), 0L, 1L);

        thrownKnifeTasks.put(projectileId, knifeTask);
    }

    private void stopThrownKnifeTask(UUID projectileId) {
        BukkitTask task = thrownKnifeTasks.remove(projectileId);
        if (task != null) {
            task.cancel();
        }
    }

    private void handleKnifeProjectileGroundCollision(Arrow projectile, Location collisionLocation) {
        UUID projectileId = projectile.getUniqueId();
        ThrownKnifeData knifeData = thrownKnifeProjectiles.get(projectileId);
        if (knifeData == null || !handledThrownKnifeProjectiles.add(projectileId)) {
            return;
        }
        stopThrownKnifeTask(projectileId);
        thrownKnifeProjectiles.remove(projectileId);
        unlockKnifeItemPickup(knifeData.knifeItemId);
        teleportKnifeItem(knifeData.knifeItemId, collisionLocation);
        projectile.remove();
        handledThrownKnifeProjectiles.remove(projectileId);
    }

    private void handleKnifeProjectileTimeout(UUID projectileId) {
        ThrownKnifeData knifeData = thrownKnifeProjectiles.get(projectileId);
        if (knifeData == null || !handledThrownKnifeProjectiles.add(projectileId)) {
            return;
        }
        stopThrownKnifeTask(projectileId);
        thrownKnifeProjectiles.remove(projectileId);
        returnKnifeToOwner(knifeData);
        handledThrownKnifeProjectiles.remove(projectileId);
    }

    private void handleKnifeProjectileVictimHit(UUID ownerId, Player victim, GameSession session) {
        if (ownerId == null || victim == null || session == null) {
            return;
        }
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || owner.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (!isAliveMurderer(owner, session) || !arena.isPlaying(victim)) {
            return;
        }
        Location victimLocation = victim.getLocation();
        if (session.eliminatePlayer(victim, owner)) {
            spawnBlood(victimLocation);
        }
    }

    private void returnKnifeToOwner(ThrownKnifeData knifeData) {
        if (knifeData == null) {
            return;
        }
        UUID knifeItemId = knifeData.knifeItemId;
        Item knifeEntity = findKnifeItem(knifeItemId);
        ItemStack knifeStack = null;
        if (knifeEntity != null && knifeEntity.isValid()) {
            knifeStack = knifeEntity.getItemStack().clone();
        } else if (knifeData.knifeItem != null) {
            knifeStack = knifeData.knifeItem.clone();
        }

        Player owner = Bukkit.getPlayer(knifeData.ownerId);
        if (owner != null && owner.isOnline() && knifeStack != null && knifeStack.getType() != Material.AIR) {
            Map<Integer, ItemStack> leftovers = owner.getInventory().addItem(knifeStack);
            for (ItemStack leftover : leftovers.values()) {
                owner.getWorld().dropItemNaturally(owner.getLocation(), leftover);
            }
        }

        if (knifeEntity != null && knifeEntity.isValid()) {
            knifeEntity.remove();
        }

        droppedKnifeOwners.remove(knifeItemId);
        unlockKnifeItemPickup(knifeItemId);
        cancelKnifeReturnTask(knifeItemId);
    }

    private Location resolveCollisionLocation(Arrow projectile, ProjectileHitEvent event) {
        if (projectile == null || projectile.getWorld() == null) {
            return null;
        }
        Block hitBlock = event == null ? null : event.getHitBlock();
        if (hitBlock != null) {
            return resolveSafeBlockCollisionLocation(projectile, event, hitBlock);
        }
        if (event != null && event.getHitEntity() != null) {
            return resolveSafeDropLocation(projectile.getLocation().clone().add(0.0D, SAFE_DROP_ELEVATION_OFFSET, 0.0D));
        }
        return resolveSafeDropLocation(projectile.getLocation().clone());
    }

    private Location resolveSafeBlockCollisionLocation(Arrow projectile, ProjectileHitEvent event, Block hitBlock) {
        Location blockCenter = hitBlock.getLocation().add(0.5D, 0.5D, 0.5D);
        BlockFace hitFace = event == null ? null : event.getHitBlockFace();
        if (hitFace != null) {
            Location outsideFaceLocation = blockCenter.clone().add(
                    hitFace.getModX() * BLOCK_COLLISION_FACE_OFFSET,
                    hitFace.getModY() * BLOCK_COLLISION_FACE_OFFSET,
                    hitFace.getModZ() * BLOCK_COLLISION_FACE_OFFSET
            );
            return resolveSafeDropLocation(outsideFaceLocation);
        }

        Vector projectileVelocity = projectile.getVelocity().clone();
        if (projectileVelocity.lengthSquared() > 0.0D) {
            projectileVelocity.normalize().multiply(0.45D);
            return resolveSafeDropLocation(projectile.getLocation().clone().subtract(projectileVelocity));
        }
        return resolveSafeDropLocation(projectile.getLocation().clone());
    }

    private Location resolveSafeDropLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return location;
        }
        Location centeredBase = centerToBlock(location);
        if (isDropSpace(centeredBase)) {
            return centeredBase.add(0.0D, SAFE_DROP_ELEVATION_OFFSET, 0.0D);
        }

        int[] offsets = {-1, 0, 1};
        for (int y = 0; y <= 2; y++) {
            for (int x : offsets) {
                for (int z : offsets) {
                    Location candidate = centeredBase.clone().add(x, y, z);
                    if (isDropSpace(candidate)) {
                        return candidate.add(0.0D, SAFE_DROP_ELEVATION_OFFSET, 0.0D);
                    }
                }
            }
        }
        return centeredBase.add(0.0D, 1.0D + SAFE_DROP_ELEVATION_OFFSET, 0.0D);
    }

    private Location centerToBlock(Location location) {
        return new Location(
                location.getWorld(),
                location.getBlockX() + 0.5D,
                location.getBlockY(),
                location.getBlockZ() + 0.5D,
                location.getYaw(),
                location.getPitch()
        );
    }

    private boolean isDropSpace(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Block block = location.getBlock();
        Block above = block.getRelative(BlockFace.UP);
        return block.isPassable() && above.isPassable();
    }

    private void teleportKnifeItem(UUID knifeItemId, Location collisionLocation) {
        Item knifeItem = findKnifeItem(knifeItemId);
        if (knifeItem == null || !knifeItem.isValid()) {
            return;
        }
        if (collisionLocation != null && collisionLocation.getWorld() != null) {
            knifeItem.teleport(collisionLocation);
            knifeItem.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
        }
    }

    private void unlockKnifeItemPickup(UUID knifeItemId) {
        if (knifeItemId == null) {
            return;
        }
        lockedKnifeItems.remove(knifeItemId);
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

    private void giveKnifeToMurderer(Player murderer) {
        if (murderer == null || !murderer.isOnline()) {
            return;
        }
        ItemStack knife = createKnifeItem();
        Map<Integer, ItemStack> leftovers = murderer.getInventory().addItem(knife);
        for (ItemStack leftover : leftovers.values()) {
            murderer.getWorld().dropItemNaturally(murderer.getLocation(), leftover);
        }
    }

    private ItemStack createKnifeItem() {
        ItemStack knife = new ItemStack(Material.WOODEN_SWORD, 1);
        ItemMeta meta = knife.getItemMeta();
        if (meta == null) {
            return knife;
        }
        meta.displayName(MURDERER_KNIFE_NAME);
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
        knife.setItemMeta(meta);
        return knife;
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

    private void clearKnifeDropsFromFloor() {
        for (UUID knifeItemId : Set.copyOf(droppedKnifeOwners.keySet())) {
            Item knifeItem = findKnifeItem(knifeItemId);
            if (knifeItem != null) {
                knifeItem.remove();
            }
            cancelKnifeReturnTask(knifeItemId);
            droppedKnifeOwners.remove(knifeItemId);
            lockedKnifeItems.remove(knifeItemId);
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
                    lockedKnifeItems.remove(knifeItemId);
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
                lockedKnifeItems.remove(knifeItemId);
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
        for (UUID projectileId : Set.copyOf(thrownKnifeProjectiles.keySet())) {
            stopThrownKnifeTask(projectileId);
            org.bukkit.entity.Entity projectile = Bukkit.getEntity(projectileId);
            if (projectile instanceof Arrow arrow) {
                arrow.remove();
            }
            thrownKnifeProjectiles.remove(projectileId);
            handledThrownKnifeProjectiles.remove(projectileId);
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

    private void sendNeedEmeraldsMessage(Player player, int requiredCost) {
        if (player == null) {
            return;
        }
        int required = Math.max(1, requiredCost);
        player.sendMessage(TextUtil.component("&cYou need " + required + " emeralds to do that!"));
    }

    private void setMurdererBuyKnifeItem(Player murderer, boolean active) {
        if (murderer == null) {
            return;
        }
        if (isQuickChatMenuOpen(murderer)) {
            return;
        }
        Material expectedMaterial = active ? Material.LIME_DYE : Material.GRAY_DYE;
        String expectedLegacyName = active ? MURDERER_BUY_KNIFE_ENABLED_LEGACY : MURDERER_BUY_KNIFE_DISABLED_LEGACY;
        ItemStack current = murderer.getInventory().getItem(MURDERER_BUY_KNIFE_SLOT);
        if (current != null && current.getType() == expectedMaterial && current.hasItemMeta()) {
            Component currentName = current.getItemMeta().displayName();
            if (currentName != null) {
                String legacy = org.bukkit.ChatColor.stripColor(LegacyComponentSerializer.legacySection().serialize(currentName));
                String expected = org.bukkit.ChatColor.stripColor(expectedLegacyName);
                if (legacy != null && legacy.equals(expected)) {
                    return;
                }
            }
        }
        murderer.getInventory().setItem(
                MURDERER_BUY_KNIFE_SLOT,
                new fr.zeyx.murder.util.ItemBuilder(expectedMaterial)
                        .setName(TextUtil.itemComponent(active ? MURDERER_BUY_KNIFE_ENABLED_NAME : MURDERER_BUY_KNIFE_DISABLED_NAME))
                        .toItemStack()
        );
    }

    private boolean isQuickChatMenuOpen(Player player) {
        if (player == null) {
            return false;
        }
        ItemStack slotEight = player.getInventory().getItem(8);
        if (slotEight == null || !slotEight.hasItemMeta()) {
            return false;
        }
        Component itemName = slotEight.getItemMeta().displayName();
        if (itemName == null) {
            return false;
        }
        String legacyName = LegacyComponentSerializer.legacySection().serialize(itemName);
        return QuickChatMenu.CLOSE_NAME.equals(legacyName);
    }
}
