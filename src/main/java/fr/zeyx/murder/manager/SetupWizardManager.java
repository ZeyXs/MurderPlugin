package fr.zeyx.murder.manager;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.TemporaryArena;
import fr.zeyx.murder.arena.task.SetupWizardTask;
import fr.zeyx.murder.util.TextUtil;
import fr.zeyx.murder.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SetupWizardManager implements Listener {

    private final GameManager gameManager;
    private final Map<UUID, TemporaryArena> inWizard = new ConcurrentHashMap<>();
    private final Map<UUID, String> editingArenaNames = new ConcurrentHashMap<>();
    private final Set<UUID> awaitingArenaNameInput = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> removeSpotArmAt = new ConcurrentHashMap<>();
    private SetupWizardTask setupWizardTask;

    private static final int SET_NAME_SLOT = 0;
    private static final int SPAWN_SPOT_SLOT = 1;
    private static final int EMERALD_SPOT_SLOT = 2;
    private static final int CANCEL_SLOT = 7;
    private static final int SAVE_SLOT = 8;

    private static final int MIN_SPAWN_SPOTS = 15;
    private static final int MIN_EMERALD_SPOTS = 1;
    private static final long REMOVE_SPOT_ARM_DELAY_MILLIS = 650L;
    private static final double SPOT_MATCH_HORIZONTAL_RADIUS = 1.35D;
    private static final double SPOT_MATCH_HORIZONTAL_RADIUS_SQUARED = SPOT_MATCH_HORIZONTAL_RADIUS * SPOT_MATCH_HORIZONTAL_RADIUS;
    private static final double SPOT_MATCH_VERTICAL_TOLERANCE = 1.5D;

    private final Component ADD_SPAWN_SPOT_ITEM_NAME = TextUtil.itemComponent("&a&lAdd Spawn Spot &r&7• Right Click");
    private final Component ADD_EMERALD_SPOT_ITEM_NAME = TextUtil.itemComponent("&2&lAdd Emerald Spawn &r&7• Right Click");
    private final Component SET_ARENA_DISPLAY_NAME_ITEM_NAME = TextUtil.itemComponent("&3&lSet Arena Name &r&7• Right Click");
    private final Component SAVE_ARENA_ITEM_NAME = TextUtil.itemComponent("&a&lSave Arena &r&7• Right Click");
    private final Component CANCEL_ITEM_NAME = TextUtil.itemComponent("&c&lCancel &r&7• Right Click");

    public SetupWizardManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public Map<UUID, TemporaryArena> getPlayersInWizard() {
        return inWizard;
    }

    public void startWizard(Player player, Arena arena) {
        if (inWizard(player)) {
            endWizard(player);
        }

        TemporaryArena temporaryArena;

        if (arena == null) temporaryArena = new TemporaryArena();
        else temporaryArena = new TemporaryArena(arena);

        ensureSetupTaskRunning();

        UUID playerId = player.getUniqueId();
        inWizard.put(playerId, temporaryArena);
        if (arena == null || arena.getName() == null || arena.getName().isBlank()) {
            editingArenaNames.remove(playerId);
        } else {
            editingArenaNames.put(playerId, arena.getName());
        }
        awaitingArenaNameInput.remove(playerId);
        removeSpotArmAt.remove(playerId);
        gameManager.getConfigurationManager().saveRollback(player);

        player.setGameMode(GameMode.CREATIVE);
        player.getInventory().clear();
        player.sendMessage(TextUtil.prefixed("&aArena setup mode activated"));
        player.sendMessage(TextUtil.prefixed("&7Remaining spawn spots to place: &e" + remainingSpawnSpots(temporaryArena)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, 1);

        player.getInventory().setItem(SET_NAME_SLOT, createSetArenaNameItem());
        player.getInventory().setItem(SPAWN_SPOT_SLOT, createAddSpawnSpotItem());
        player.getInventory().setItem(EMERALD_SPOT_SLOT, createAddEmeraldSpotItem());
        player.getInventory().setItem(CANCEL_SLOT, createCancelItem());
        player.getInventory().setItem(SAVE_SLOT, createSaveArenaItem());
        refreshContextualHotbarItems(player);

    }

    public void endWizard(Player player) {
        UUID playerId = player.getUniqueId();
        inWizard.remove(playerId);
        editingArenaNames.remove(playerId);
        awaitingArenaNameInput.remove(playerId);
        removeSpotArmAt.remove(playerId);
        if (setupWizardTask != null) {
            setupWizardTask.clearVisualsForPlayer(playerId);
        }

        player.getInventory().clear();
        gameManager.getConfigurationManager().loadRollback(player);

        player.sendActionBar(Component.empty());
    }

    public void shutdown() {
        for (UUID playerId : new ArrayList<>(inWizard.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                endWizard(player);
                continue;
            }
            inWizard.remove(playerId);
            editingArenaNames.remove(playerId);
            awaitingArenaNameInput.remove(playerId);
            removeSpotArmAt.remove(playerId);
        }
        if (setupWizardTask != null) {
            setupWizardTask.clearAllVisuals();
            setupWizardTask.cancel();
            setupWizardTask = null;
        }
    }

    public boolean inWizard(Player player) {
        return inWizard.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onItemInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!inWizard(player)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.hasItem()) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if(!event.getItem().hasItemMeta()) return;

        event.setCancelled(true);
        TemporaryArena arena = inWizard.get(player.getUniqueId());
        if (arena == null) {
            return;
        }

        int heldSlot = player.getInventory().getHeldItemSlot();
        Material heldType = event.getItem().getType();
        if (heldType == Material.REDSTONE) {
            if (!isRemoveSpotInteractionArmed(player.getUniqueId())) {
                return;
            }
            if (heldSlot == SPAWN_SPOT_SLOT) {
                removeSpawnSpotAtPlayer(player, arena);
                refreshContextualHotbarItems(player);
                return;
            }
            if (heldSlot == EMERALD_SPOT_SLOT) {
                removeEmeraldSpotAtPlayer(player, arena);
                refreshContextualHotbarItems(player);
                return;
            }
        }

        Component itemName = event.getItem().getItemMeta().displayName();

        if (Objects.equals(itemName, ADD_SPAWN_SPOT_ITEM_NAME)) {
            Location location = player.getLocation();
            if (containsLocation(arena.getSpawnSpots(), location)) {
                player.sendMessage(TextUtil.prefixed("&7That spawn spot is already set"));
                return;
            }
            if (countActiveSpots(arena.getSpawnSpots()) >= MIN_SPAWN_SPOTS) {
                player.sendMessage(TextUtil.prefixed("&7You already placed the maximum of &e" + MIN_SPAWN_SPOTS + " &7spawn spots"));
                return;
            }
            int spawnIndex = addIndexedSpot(arena.getSpawnSpots(), location);
            if (arena.getSpawnLocation() == null) {
                arena.setSpawnLocation(location.clone());
            }
            armRemoveSpotInteraction(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, 2);
            player.sendMessage(TextUtil.prefixed("&7Spawn spot &d#" + (spawnIndex + 1) + " &7added! &e" + TextUtil.displayLocation(location)));
            player.sendMessage(TextUtil.prefixed("&7Remaining spawn spots to place: &e" + remainingSpawnSpots(arena)));
            refreshContextualHotbarItems(player);

        } else if (Objects.equals(itemName, SET_ARENA_DISPLAY_NAME_ITEM_NAME)) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 50, 1);
            startChatNameInput(player, arena);

        } else if (Objects.equals(itemName, ADD_EMERALD_SPOT_ITEM_NAME)) {
            Location location = player.getLocation();
            if (containsLocation(arena.getEmeraldSpots(), location)) {
                player.sendMessage(TextUtil.prefixed("&7That emerald spawn is already set"));
                return;
            }
            int emeraldIndex = addIndexedSpot(arena.getEmeraldSpots(), location);
            armRemoveSpotInteraction(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, 2);
            player.sendMessage(TextUtil.prefixed("&7Emerald spawn &a#" + (emeraldIndex + 1) + " &7added! &e" + TextUtil.displayLocation(location)));
            refreshContextualHotbarItems(player);

        } else if (Objects.equals(itemName, SAVE_ARENA_ITEM_NAME)) {

            if (arena.getName() == null || arena.getName().isEmpty()) {
                player.sendMessage(TextUtil.prefixed("&7Please set a display name for the arena"));
                return;
            }

            if (countActiveSpots(arena.getSpawnSpots()) < MIN_SPAWN_SPOTS) {
                player.sendMessage(TextUtil.prefixed("&7Please set at least &e" + MIN_SPAWN_SPOTS + " &7spawn spots"));
                return;
            }

            if (countActiveSpots(arena.getEmeraldSpots()) < MIN_EMERALD_SPOTS) {
                player.sendMessage(TextUtil.prefixed("&7Please set at least &e" + MIN_EMERALD_SPOTS + " &7emerald spawn spot"));
                return;
            }

            Arena saved = arena.toArena();
            String editedArenaName = editingArenaNames.get(player.getUniqueId());
            gameManager.getArenaManager().upsertArena(editedArenaName, saved);
            if (editedArenaName != null
                    && !editedArenaName.isBlank()
                    && !editedArenaName.equalsIgnoreCase(saved.getName())) {
                gameManager.getConfigurationManager().removeArena(editedArenaName);
            }
            gameManager.getConfigurationManager().saveArena(saved);
            endWizard(player);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 50, 1);
            if (editedArenaName == null || editedArenaName.isBlank()) {
                player.sendMessage(TextUtil.prefixed("&7Successfully created &e&l" + saved.getDisplayName() + "&r&7!"));
            } else {
                player.sendMessage(TextUtil.prefixed("&7Successfully updated &e&l" + saved.getDisplayName() + "&r&7!"));
            }

        } else if (Objects.equals(itemName, CANCEL_ITEM_NAME)) {
            endWizard(player);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, 0.5f);
            player.sendMessage(TextUtil.prefixed("&7Arena creation cancelled"));
        }

    }

    @EventHandler
    public void onNameChatInput(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!awaitingArenaNameInput.contains(playerId) || !inWizard(player)) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();

        Bukkit.getScheduler().runTask(MurderPlugin.getInstance(), task -> {
            if (!awaitingArenaNameInput.remove(playerId)) {
                return;
            }
            if (!inWizard(player)) {
                return;
            }
            TemporaryArena arena = inWizard.get(playerId);
            if (arena == null) {
                return;
            }

            String input = message == null ? "" : message.trim();
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(TextUtil.prefixed("&7Arena name input cancelled"));
                return;
            }

            if (!trySetArenaDisplayName(player, arena, input)) {
                awaitingArenaNameInput.add(playerId);
                player.sendMessage(TextUtil.prefixed("&7Type another arena name in chat, or type &ccancel"));
            }
        });
    }

    @EventHandler
    public void onBreakBlock(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (inWizard(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onBreakPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (inWizard(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (inWizard(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (inWizard(player)) event.setCancelled(true);
    }

    private int remainingSpawnSpots(TemporaryArena arena) {
        return Math.max(0, MIN_SPAWN_SPOTS - countActiveSpots(arena.getSpawnSpots()));
    }

    private boolean containsLocation(List<Location> locations, Location location) {
        return findSpotIndexAtBlock(locations, location) != null;
    }

    private void startChatNameInput(Player player, TemporaryArena arena) {
        if (player == null || arena == null) {
            return;
        }
        awaitingArenaNameInput.add(player.getUniqueId());
        String currentName = arena.getDisplayName();
        if (currentName != null && !currentName.isBlank()) {
            player.sendMessage(TextUtil.prefixed("&7Current arena display name: &e&l" + currentName));
        }
        player.sendMessage(TextUtil.prefixed("&7Type the arena name in chat, or type &ccancel"));
    }

    private boolean trySetArenaDisplayName(Player player, TemporaryArena arena, String input) {
        if (arena == null || input == null) {
            return false;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            if (player != null) {
                player.sendMessage(TextUtil.prefixed("&7Arena name cannot be empty"));
            }
            return false;
        }
        if (isDuplicateArenaDisplayName(arena, trimmed)) {
            if (player != null) {
                player.sendMessage(TextUtil.prefixed("&7This arena already exists"));
            }
            return false;
        }
        arena.setDisplayName(trimmed);
        if (player != null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, 2);
            player.sendMessage(TextUtil.prefixed("&7Set arena display name to &e&l" + trimmed + "&r&7!"));
        }
        return true;
    }

    private boolean isDuplicateArenaDisplayName(TemporaryArena currentArena, String candidateDisplayName) {
        if (candidateDisplayName == null || candidateDisplayName.isBlank()) {
            return true;
        }
        String currentDisplay = currentArena == null ? null : currentArena.getDisplayName();
        for (Arena existing : gameManager.getArenaManager().getArenas()) {
            if (existing == null) {
                continue;
            }
            String existingDisplay = existing.getDisplayName();
            if (existingDisplay == null || !existingDisplay.equalsIgnoreCase(candidateDisplayName)) {
                continue;
            }
            if (currentDisplay != null && currentDisplay.equalsIgnoreCase(candidateDisplayName)) {
                continue;
            }
            return true;
        }
        return false;
    }

    public void refreshContextualHotbarItems(Player player) {
        if (player == null || !inWizard(player)) {
            return;
        }
        TemporaryArena arena = inWizard.get(player.getUniqueId());
        if (arena == null) {
            return;
        }

        Integer spawnIndex = findSpotIndexNearLocation(arena.getSpawnSpots(), player.getLocation());
        if (spawnIndex != null) {
            player.getInventory().setItem(SPAWN_SPOT_SLOT, createRemoveSpawnSpotItem(spawnIndex + 1));
        } else {
            player.getInventory().setItem(SPAWN_SPOT_SLOT, createAddSpawnSpotItem());
        }

        Integer emeraldIndex = findSpotIndexNearLocation(arena.getEmeraldSpots(), player.getLocation());
        if (emeraldIndex != null) {
            player.getInventory().setItem(EMERALD_SPOT_SLOT, createRemoveEmeraldSpotItem(emeraldIndex + 1));
        } else {
            player.getInventory().setItem(EMERALD_SPOT_SLOT, createAddEmeraldSpotItem());
        }
    }

    private void removeSpawnSpotAtPlayer(Player player, TemporaryArena arena) {
        Integer removedIndex = findSpotIndexNearLocation(arena.getSpawnSpots(), player.getLocation());
        if (removedIndex == null) {
            player.sendMessage(TextUtil.prefixed("&7You are not standing on a spawn spot"));
            return;
        }
        Location removedSpotLocation = arena.getSpawnSpots().get(removedIndex);
        removeIndexedSpotByIndex(arena.getSpawnSpots(), removedIndex);
        if (isSameBlock(arena.getSpawnLocation(), removedSpotLocation)) {
            arena.setSpawnLocation(findFirstActiveSpot(arena.getSpawnSpots()));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 50, 1.2f);
        player.sendMessage(TextUtil.prefixed("&7Removed spawn spot &d#" + (removedIndex + 1)));
        player.sendMessage(TextUtil.prefixed("&7Remaining spawn spots to place: &e" + remainingSpawnSpots(arena)));
    }

    private void removeEmeraldSpotAtPlayer(Player player, TemporaryArena arena) {
        Integer removedIndex = findSpotIndexNearLocation(arena.getEmeraldSpots(), player.getLocation());
        if (removedIndex == null) {
            player.sendMessage(TextUtil.prefixed("&7You are not standing on an emerald spot"));
            return;
        }
        removeIndexedSpotByIndex(arena.getEmeraldSpots(), removedIndex);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 50, 1.2f);
        player.sendMessage(TextUtil.prefixed("&7Removed emerald spawn &a#" + (removedIndex + 1)));
    }

    private int addIndexedSpot(List<Location> locations, Location location) {
        if (locations == null) {
            return -1;
        }
        Location stored = location == null ? null : location.clone();
        for (int index = 0; index < locations.size(); index++) {
            if (locations.get(index) == null) {
                locations.set(index, stored);
                return index;
            }
        }
        locations.add(stored);
        return locations.size() - 1;
    }

    private void removeIndexedSpotByIndex(List<Location> locations, int index) {
        if (locations == null || index < 0 || index >= locations.size()) {
            return;
        }
        locations.set(index, null);
        trimTrailingNulls(locations);
    }

    private Integer findSpotIndexAtBlock(List<Location> locations, Location location) {
        if (locations == null || location == null || location.getWorld() == null) {
            return null;
        }
        for (int index = 0; index < locations.size(); index++) {
            Location existing = locations.get(index);
            if (isSameBlock(existing, location)) {
                return index;
            }
        }
        return null;
    }

    private Integer findSpotIndexNearLocation(List<Location> locations, Location location) {
        if (locations == null || location == null || location.getWorld() == null) {
            return null;
        }
        for (int index = 0; index < locations.size(); index++) {
            Location existing = locations.get(index);
            if (isNearSpot(existing, location)) {
                return index;
            }
        }
        return null;
    }

    private void trimTrailingNulls(List<Location> locations) {
        if (locations == null) {
            return;
        }
        for (int index = locations.size() - 1; index >= 0; index--) {
            if (locations.get(index) != null) {
                break;
            }
            locations.remove(index);
        }
    }

    private int countActiveSpots(List<Location> locations) {
        if (locations == null) {
            return 0;
        }
        int count = 0;
        for (Location location : locations) {
            if (location != null) {
                count++;
            }
        }
        return count;
    }

    private Location findFirstActiveSpot(List<Location> locations) {
        if (locations == null) {
            return null;
        }
        for (Location location : locations) {
            if (location != null) {
                return location.clone();
            }
        }
        return null;
    }

    private boolean isSameBlock(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            return false;
        }
        return first.getWorld().equals(second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private boolean isNearSpot(Location spot, Location location) {
        if (spot == null || location == null || spot.getWorld() == null || location.getWorld() == null) {
            return false;
        }
        if (!spot.getWorld().equals(location.getWorld())) {
            return false;
        }

        double spotX = spot.getBlockX() + 0.5D;
        double spotY = spot.getBlockY() + 0.5D;
        double spotZ = spot.getBlockZ() + 0.5D;

        double dx = location.getX() - spotX;
        double dz = location.getZ() - spotZ;
        double horizontalDistanceSquared = dx * dx + dz * dz;
        if (horizontalDistanceSquared > SPOT_MATCH_HORIZONTAL_RADIUS_SQUARED) {
            return false;
        }

        double verticalDistance = Math.abs(location.getY() - spotY);
        return verticalDistance <= SPOT_MATCH_VERTICAL_TOLERANCE;
    }

    private ItemStack createSetArenaNameItem() {
        return new ItemBuilder(Material.NAME_TAG).setName(SET_ARENA_DISPLAY_NAME_ITEM_NAME).toItemStack();
    }

    private ItemStack createAddSpawnSpotItem() {
        return new ItemBuilder(Material.ENDER_PEARL).setName(ADD_SPAWN_SPOT_ITEM_NAME).toItemStack();
    }

    private ItemStack createAddEmeraldSpotItem() {
        return new ItemBuilder(Material.EMERALD).setName(ADD_EMERALD_SPOT_ITEM_NAME).toItemStack();
    }

    private ItemStack createCancelItem() {
        return new ItemBuilder(Material.RED_TERRACOTTA).setName(CANCEL_ITEM_NAME).toItemStack();
    }

    private ItemStack createSaveArenaItem() {
        return new ItemBuilder(Material.LIME_TERRACOTTA).setName(SAVE_ARENA_ITEM_NAME).toItemStack();
    }

    private ItemStack createRemoveSpawnSpotItem(int spotNumber) {
        Component name = TextUtil.itemComponent("&c&lRemove Spawn Spot &r&7• &d#" + spotNumber);
        return new ItemBuilder(Material.REDSTONE).setName(name).toItemStack();
    }

    private ItemStack createRemoveEmeraldSpotItem(int spotNumber) {
        Component name = TextUtil.itemComponent("&c&lRemove Emerald Spawn &r&7• &a#" + spotNumber);
        return new ItemBuilder(Material.REDSTONE).setName(name).toItemStack();
    }

    private void armRemoveSpotInteraction(UUID playerId) {
        if (playerId == null) {
            return;
        }
        removeSpotArmAt.put(playerId, System.currentTimeMillis() + REMOVE_SPOT_ARM_DELAY_MILLIS);
    }

    private boolean isRemoveSpotInteractionArmed(UUID playerId) {
        if (playerId == null) {
            return true;
        }
        long armAt = removeSpotArmAt.getOrDefault(playerId, 0L);
        return System.currentTimeMillis() >= armAt;
    }

    private void ensureSetupTaskRunning() {
        if (setupWizardTask == null || isTaskCancelled(setupWizardTask)) {
            setupWizardTask = new SetupWizardTask(this);
            setupWizardTask.runTaskTimer(MurderPlugin.getInstance(), 0L, 10L);
        }
    }

    private boolean isTaskCancelled(SetupWizardTask task) {
        if (task == null) {
            return true;
        }
        try {
            return task.isCancelled();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

}
