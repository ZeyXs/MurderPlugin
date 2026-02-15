package fr.zeyx.murder.arena.setup;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.TemporaryArena;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.arena.task.SetupWizardTask;
import fr.zeyx.murder.util.TextUtil;
import fr.zeyx.murder.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.tuple.ImmutablePair;
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
    private final Set<UUID> awaitingArenaNameInput = ConcurrentHashMap.newKeySet();

    private static final int MIN_SPAWN_SPOTS = 15;
    private static final int MIN_EMERALD_SPOTS = 1;

    private final Component ADD_SPAWN_SPOT_ITEM_NAME = TextUtil.itemComponent("&aAdd Spawn Spot &7(Right-click)");
    private final Component ADD_EMERALD_SPOT_ITEM_NAME = TextUtil.itemComponent("&2Add Emerald Spawn &7(Right-click)");
    private final Component SET_ARENA_DISPLAY_NAME_ITEM_NAME = TextUtil.itemComponent("&3Set Arena Name &7(Right-click)");
    private final Component SAVE_ARENA_ITEM_NAME = TextUtil.itemComponent("&aSave Arena &7(Right-click)");
    private final Component CANCEL_ITEM_NAME = TextUtil.itemComponent("&cCancel &7(Right-click)");

    public SetupWizardManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public Map<UUID, TemporaryArena> getPlayersInWizard() {
        return inWizard;
    }

    public void startWizard(Player player, Arena arena) {
        TemporaryArena temporaryArena;

        if (arena == null) temporaryArena = new TemporaryArena();
        else temporaryArena = new TemporaryArena(arena);

        SetupWizardTask setupWizardTask = new SetupWizardTask(this);
        setupWizardTask.runTaskTimer(MurderPlugin.getInstance(), 0, 10);

        inWizard.put(player.getUniqueId(), temporaryArena);
        awaitingArenaNameInput.remove(player.getUniqueId());
        gameManager.getConfigurationManager().saveRollback(player);

        player.setGameMode(GameMode.CREATIVE);
        player.getInventory().clear();
        player.sendMessage(TextUtil.prefixed("&aArena setup mode activated."));
        player.sendMessage(TextUtil.prefixed("&eRemaining spawn spots to place: &b" + remainingSpawnSpots(temporaryArena)));
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 50, 1);

        Map<Material, ImmutablePair<Component, Integer>> wizardItems = Map.of(
                Material.NAME_TAG, new ImmutablePair<>(SET_ARENA_DISPLAY_NAME_ITEM_NAME, 0),
                Material.ENDER_PEARL, new ImmutablePair<>(ADD_SPAWN_SPOT_ITEM_NAME, 1),
                Material.EMERALD, new ImmutablePair<>(ADD_EMERALD_SPOT_ITEM_NAME, 2),
                Material.RED_BANNER, new ImmutablePair<>(CANCEL_ITEM_NAME, 7),
                Material.GREEN_BANNER, new ImmutablePair<>(SAVE_ARENA_ITEM_NAME, 8)
        );
        wizardItems.forEach((material, pair) -> {
            player.getInventory().setItem(
                    pair.getRight(),
                    new ItemBuilder(material)
                            .setName(pair.getLeft())
                            .toItemStack()
            );
        });

    }

    public void endWizard(Player player) {
        inWizard.remove(player.getUniqueId());
        awaitingArenaNameInput.remove(player.getUniqueId());

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
        }
    }

    public boolean inWizard(Player player) {
        return inWizard.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onItemInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!inWizard(player)) return;
        if (!event.hasItem()) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if(!event.getItem().hasItemMeta()) return;

        event.setCancelled(true);
        Component itemName = event.getItem().getItemMeta().displayName();
        TemporaryArena arena = inWizard.get(player.getUniqueId());

        if (Objects.equals(itemName, ADD_SPAWN_SPOT_ITEM_NAME)) {
            Location location = player.getLocation();
            if (containsLocation(arena.getSpawnSpots(), location)) {
                player.sendMessage(TextUtil.prefixed("&cThat spawn spot is already set."));
                return;
            }
            if (arena.getSpawnSpots().size() >= MIN_SPAWN_SPOTS) {
                player.sendMessage(TextUtil.prefixed("&cYou already placed the maximum of &e" + MIN_SPAWN_SPOTS + " &cspawn spots."));
                return;
            }
            arena.getSpawnSpots().add(location);
            if (arena.getSpawnLocation() == null) {
                arena.setSpawnLocation(location);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, 2);
            player.sendMessage(TextUtil.prefixed("&aSpawn spot added! &d" + TextUtil.displayLocation(location)));
            player.sendMessage(TextUtil.prefixed("&eRemaining spawn spots to place: &b" + remainingSpawnSpots(arena)));

        } else if (Objects.equals(itemName, SET_ARENA_DISPLAY_NAME_ITEM_NAME)) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 50, 1);
            startChatNameInput(player, arena);

        } else if (Objects.equals(itemName, ADD_EMERALD_SPOT_ITEM_NAME)) {
            Location location = player.getLocation();
            if (containsLocation(arena.getEmeraldSpots(), location)) {
                player.sendMessage(TextUtil.prefixed("&cThat emerald spawn is already set."));
                return;
            }
            arena.getEmeraldSpots().add(location);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, 2);
            player.sendMessage(TextUtil.prefixed("&aEmerald spawn added! &d" + TextUtil.displayLocation(location)));

        } else if (Objects.equals(itemName, SAVE_ARENA_ITEM_NAME)) {

            if (arena.getName() == null || arena.getName().isEmpty()) {
                player.sendMessage(TextUtil.prefixed("&cPlease set a display name for the arena."));
                return;
            }

            if (arena.getSpawnSpots().size() < MIN_SPAWN_SPOTS) {
                player.sendMessage(TextUtil.prefixed("&cPlease set at least &e" + MIN_SPAWN_SPOTS + " &cspawn spots."));
                return;
            }

            if (arena.getEmeraldSpots().size() < MIN_EMERALD_SPOTS) {
                player.sendMessage(TextUtil.prefixed("&cPlease set at least &e" + MIN_EMERALD_SPOTS + " &cemerald spawn spot."));
                return;
            }

            Arena saved = arena.toArena();
            gameManager.getArenaManager().addArena(saved);
            gameManager.getConfigurationManager().saveArena(saved);
            endWizard(player);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 50, 1);
            player.sendMessage(TextUtil.prefixed("&aSuccessfully created &a" + saved.getDisplayName() + "&7!"));

        } else if (Objects.equals(itemName, CANCEL_ITEM_NAME)) {
            endWizard(player);
            player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 50, 2);
            player.sendMessage(TextUtil.prefixed("&cArena creation cancelled."));
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
                player.sendMessage(TextUtil.prefixed("&7Arena name input cancelled."));
                return;
            }

            if (!trySetArenaDisplayName(player, arena, input)) {
                awaitingArenaNameInput.add(playerId);
                player.sendMessage(TextUtil.prefixed("&eType another arena name in chat, or type &ccancel&e."));
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
        return Math.max(0, MIN_SPAWN_SPOTS - arena.getSpawnSpots().size());
    }

    private boolean containsLocation(List<Location> locations, Location location) {
        if (locations == null || location == null) {
            return false;
        }
        for (Location existing : locations) {
            if (existing == null || existing.getWorld() == null || location.getWorld() == null) {
                continue;
            }
            if (existing.getWorld().equals(location.getWorld())
                    && existing.getBlockX() == location.getBlockX()
                    && existing.getBlockY() == location.getBlockY()
                    && existing.getBlockZ() == location.getBlockZ()) {
                return true;
            }
        }
        return false;
    }

    private void startChatNameInput(Player player, TemporaryArena arena) {
        if (player == null || arena == null) {
            return;
        }
        awaitingArenaNameInput.add(player.getUniqueId());
        String currentName = arena.getDisplayName();
        if (currentName != null && !currentName.isBlank()) {
            player.sendMessage(TextUtil.prefixed("&7Current arena display name: &3" + currentName));
        }
        player.sendMessage(TextUtil.prefixed("&eType the arena name in chat, or type &ccancel&e."));
    }

    private boolean trySetArenaDisplayName(Player player, TemporaryArena arena, String input) {
        if (arena == null || input == null) {
            return false;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            if (player != null) {
                player.sendMessage(TextUtil.prefixed("&cArena name cannot be empty."));
            }
            return false;
        }
        if (isDuplicateArenaDisplayName(arena, trimmed)) {
            if (player != null) {
                player.sendMessage(TextUtil.prefixed("&cThis arena already exists."));
            }
            return false;
        }
        arena.setDisplayName(trimmed);
        if (player != null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, 2);
            player.sendMessage(TextUtil.prefixed("&aSet arena display name to &3" + trimmed + "&7!"));
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

}
