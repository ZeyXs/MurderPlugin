package fr.zeyx.murder.game.feature;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.game.GameSession;
import fr.zeyx.murder.game.QuickChatMenu;
import fr.zeyx.murder.game.Role;
import fr.zeyx.murder.util.TextUtil;
import fr.zeyx.murder.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class EmeraldFeature {

    private static final int EMERALD_SLOT = 7;
    private static final int EMERALD_SPAWN_INTERVAL_TICKS = 20 * 5;
    private static final int WEAPON_UPGRADE_EMERALD_COUNT = 5;
    private static final String EMERALD_ITEM_NAME = "&fEmerald";
    private static final String MURDERER_PICKUP_MESSAGE = "&aYou found an emerald!";
    private static final String INNOCENT_PICKUP_MESSAGE_TEMPLATE = "&7You found an emerald! &a(%d/5)";
    private static final String GUN_UPGRADE_MESSAGE = "&aA villager swapped your emeralds for an upgraded weapon!";
    private static final Component EMERALD_COMPONENT_NAME = TextUtil.itemComponent(EMERALD_ITEM_NAME, false);

    private final Arena arena;
    private final GunFeature gunFeature;
    private final NamespacedKey emeraldItemKey;
    private final Map<UUID, Integer> emeraldBalances = new ConcurrentHashMap<>();
    private final Map<String, UUID> spawnedEmeraldItemsBySpot = new ConcurrentHashMap<>();
    private final Map<UUID, String> emeraldSpotByItemId = new ConcurrentHashMap<>();
    private int spawnTickCounter;

    public EmeraldFeature(Arena arena, GunFeature gunFeature) {
        this.arena = arena;
        this.gunFeature = gunFeature;
        this.emeraldItemKey = new NamespacedKey(MurderPlugin.getInstance(), "arena_emerald_item");
    }

    public void start(Collection<UUID> roundPlayers) {
        clearAllSpawnedEmeralds();
        emeraldBalances.clear();
        spawnTickCounter = 0;
        if (roundPlayers == null) {
            return;
        }
        for (UUID playerId : roundPlayers) {
            emeraldBalances.put(playerId, 0);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                syncEmeraldSlot(player, 0);
            }
        }
    }

    public void tick(GameSession session) {
        if (session == null) {
            return;
        }
        for (UUID playerId : new ArrayList<>(session.getAlivePlayers())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            int emeraldCount = getEmeraldCount(playerId);
            syncEmeraldSlot(player, emeraldCount);
            maybeUpgradeGunCooldown(player, session, emeraldCount);
        }

        spawnTickCounter++;
        if (spawnTickCounter < EMERALD_SPAWN_INTERVAL_TICKS) {
            return;
        }
        spawnTickCounter = 0;
        spawnRandomEmerald();
    }

    public void onEmeraldPickup(EntityPickupItemEvent event, GameSession session) {
        ItemStack picked = event.getItem().getItemStack();
        if (!isEmeraldItem(picked)) {
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

        event.setCancelled(true);
        UUID itemId = event.getItem().getUniqueId();
        unregisterSpawnedEmerald(itemId);
        if (event.getItem().isValid()) {
            event.getItem().remove();
        }

        UUID playerId = player.getUniqueId();
        int emeraldCount = emeraldBalances.merge(playerId, 1, Integer::sum);
        syncEmeraldSlot(player, emeraldCount);
        player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1.0F, 1.0F);

        Role role = session.getRole(playerId);
        if (role == Role.MURDERER) {
            player.sendMessage(TextUtil.component(MURDERER_PICKUP_MESSAGE));
        } else if (role == Role.BYSTANDER || role == Role.DETECTIVE) {
            player.sendMessage(TextUtil.component(String.format(INNOCENT_PICKUP_MESSAGE_TEMPLATE, emeraldCount)));
        }

        maybeUpgradeGunCooldown(player, session, emeraldCount);
    }

    public void onEmeraldDespawn(ItemDespawnEvent event) {
        if (!isEmeraldItem(event.getEntity().getItemStack())) {
            return;
        }
        unregisterSpawnedEmerald(event.getEntity().getUniqueId());
    }

    public int getEmeraldCount(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return Math.max(0, emeraldBalances.getOrDefault(playerId, 0));
    }

    public int getMissingEmeralds(UUID playerId, int cost) {
        if (cost <= 0) {
            return 0;
        }
        return Math.max(0, cost - getEmeraldCount(playerId));
    }

    public boolean trySpendEmeralds(Player player, int cost) {
        if (player == null || cost <= 0) {
            return true;
        }
        UUID playerId = player.getUniqueId();
        int current = getEmeraldCount(playerId);
        if (current < cost) {
            return false;
        }
        int remaining = Math.max(0, current - cost);
        emeraldBalances.put(playerId, remaining);
        syncEmeraldSlot(player, remaining);
        return true;
    }

    public void clearAllSpawnedEmeralds() {
        for (UUID itemId : Set.copyOf(emeraldSpotByItemId.keySet())) {
            Item item = resolveItem(itemId);
            if (item != null && item.isValid()) {
                item.remove();
            }
        }
        spawnedEmeraldItemsBySpot.clear();
        emeraldSpotByItemId.clear();

        for (World world : collectArenaWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (isEmeraldItem(item.getItemStack())) {
                    item.remove();
                }
            }
        }
    }

    public void clearRuntimeState() {
        clearAllSpawnedEmeralds();
        Set<UUID> trackedPlayers = new HashSet<>(emeraldBalances.keySet());
        trackedPlayers.addAll(arena.getActivePlayers());
        for (UUID playerId : trackedPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                clearEmeraldSlot(player);
            }
        }
        emeraldBalances.clear();
        spawnTickCounter = 0;
    }

    private void maybeUpgradeGunCooldown(Player player, GameSession session, int emeraldCount) {
        if (player == null || session == null || gunFeature == null) {
            return;
        }
        if (emeraldCount < WEAPON_UPGRADE_EMERALD_COUNT) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Role role = session.getRole(playerId);
        if (role != Role.BYSTANDER && role != Role.DETECTIVE) {
            return;
        }
        if (!trySpendEmeralds(player, WEAPON_UPGRADE_EMERALD_COUNT)) {
            return;
        }
        boolean upgraded = gunFeature.applyEmeraldUpgrade(player);
        if (!upgraded) {
            emeraldBalances.merge(playerId, WEAPON_UPGRADE_EMERALD_COUNT, Integer::sum);
            syncEmeraldSlot(player, getEmeraldCount(playerId));
            return;
        }
        player.sendMessage(TextUtil.component(GUN_UPGRADE_MESSAGE));
    }

    private void spawnRandomEmerald() {
        List<Location> spots = arena.getEmeraldSpots();
        if (spots == null || spots.isEmpty()) {
            return;
        }
        pruneInvalidSpawnedEmeralds();
        List<Location> availableSpots = collectAvailableSpots(spots);
        if (availableSpots.isEmpty()) {
            return;
        }

        Location chosenSpot = availableSpots.get(ThreadLocalRandom.current().nextInt(availableSpots.size()));
        if (chosenSpot.getWorld() == null) {
            return;
        }
        Location spawnLocation = toSpawnLocation(chosenSpot);
        ItemStack emeraldItem = createEmeraldItem(1);
        Item spawned = chosenSpot.getWorld().dropItem(spawnLocation, emeraldItem);
        spawned.setVelocity(new Vector(0.0D, 0.0D, 0.0D));

        String spotKey = toSpotKey(chosenSpot);
        spawnedEmeraldItemsBySpot.put(spotKey, spawned.getUniqueId());
        emeraldSpotByItemId.put(spawned.getUniqueId(), spotKey);
    }

    private List<Location> collectAvailableSpots(List<Location> spots) {
        List<Location> available = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Location spot : spots) {
            if (spot == null || spot.getWorld() == null) {
                continue;
            }
            String key = toSpotKey(spot);
            if (!seen.add(key)) {
                continue;
            }
            if (spawnedEmeraldItemsBySpot.containsKey(key)) {
                continue;
            }
            available.add(spot.clone());
        }
        return available;
    }

    private void pruneInvalidSpawnedEmeralds() {
        for (Map.Entry<String, UUID> entry : new ArrayList<>(spawnedEmeraldItemsBySpot.entrySet())) {
            UUID itemId = entry.getValue();
            Item item = resolveItem(itemId);
            if (item == null || !item.isValid() || item.isDead()) {
                spawnedEmeraldItemsBySpot.remove(entry.getKey());
                emeraldSpotByItemId.remove(itemId);
            }
        }
    }

    private void unregisterSpawnedEmerald(UUID itemId) {
        if (itemId == null) {
            return;
        }
        String spotKey = emeraldSpotByItemId.remove(itemId);
        if (spotKey != null) {
            spawnedEmeraldItemsBySpot.remove(spotKey);
        }
    }

    private void syncEmeraldSlot(Player player, int emeraldCount) {
        if (player == null || !player.isOnline() || isQuickChatMenuOpen(player)) {
            return;
        }
        if (emeraldCount <= 0) {
            clearEmeraldSlot(player);
            return;
        }
        int amount = Math.min(64, Math.max(1, emeraldCount));
        ItemStack current = player.getInventory().getItem(EMERALD_SLOT);
        if (isEmeraldItem(current) && current.getAmount() == amount) {
            return;
        }
        player.getInventory().setItem(EMERALD_SLOT, createEmeraldItem(amount));
    }

    private void clearEmeraldSlot(Player player) {
        if (player == null) {
            return;
        }
        ItemStack current = player.getInventory().getItem(EMERALD_SLOT);
        if (isEmeraldItem(current)) {
            player.getInventory().setItem(EMERALD_SLOT, null);
        }
    }

    private boolean isQuickChatMenuOpen(Player player) {
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

    private ItemStack createEmeraldItem(int amount) {
        ItemStack item = new ItemBuilder(Material.EMERALD, Math.max(1, amount))
                .setName(EMERALD_COMPONENT_NAME)
                .toItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(emeraldItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isEmeraldItem(ItemStack item) {
        if (item == null || item.getType() != Material.EMERALD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Byte marker = meta.getPersistentDataContainer().get(emeraldItemKey, PersistentDataType.BYTE);
        if (marker != null && marker == (byte) 1) {
            return true;
        }
        return EMERALD_COMPONENT_NAME.equals(meta.displayName());
    }

    private Location toSpawnLocation(Location location) {
        return new Location(
                location.getWorld(),
                location.getBlockX() + 0.5D,
                location.getBlockY() + 0.15D,
                location.getBlockZ() + 0.5D
        );
    }

    private String toSpotKey(Location location) {
        return location.getWorld().getUID()
                + ":" + location.getBlockX()
                + ":" + location.getBlockY()
                + ":" + location.getBlockZ();
    }

    private Item resolveItem(UUID entityId) {
        if (entityId == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(entityId);
        if (entity instanceof Item item) {
            return item;
        }
        return null;
    }

    private Set<World> collectArenaWorlds() {
        Set<World> worlds = new HashSet<>();
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
        if (arena.getEmeraldSpots() != null) {
            for (Location spot : arena.getEmeraldSpots()) {
                if (spot != null && spot.getWorld() != null) {
                    worlds.add(spot.getWorld());
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
