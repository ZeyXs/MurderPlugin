package fr.zeyx.murder.arena.setup;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.TemporaryArena;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.arena.task.SetupWizardTask;
import fr.zeyx.murder.util.ChatUtil;
import fr.zeyx.murder.util.ItemBuilder;
import net.wesjd.anvilgui.AnvilGUI;
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
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class SetupWizardManager implements Listener {

    private GameManager gameManager;
    private Map<UUID, TemporaryArena> inWizard = new HashMap<>();

    private static final int MIN_SPAWN_SPOTS = 15;
    private static final int MIN_EMERALD_SPOTS = 1;

    private final Component ADD_SPAWN_SPOT_ITEM_NAME = ChatUtil.itemComponent("&aAdd Spawn Spot &7(Right-click)");
    private final Component ADD_EMERALD_SPOT_ITEM_NAME = ChatUtil.itemComponent("&2Add Emerald Spawn &7(Right-click)");
    private final Component SET_ARENA_DISPLAY_NAME_ITEM_NAME = ChatUtil.itemComponent("&3Set Arena Name &7(Right-click)");
    private final Component SAVE_ARENA_ITEM_NAME = ChatUtil.itemComponent("&aSave Arena &7(Right-click)");
    private final Component CANCEL_ITEM_NAME = ChatUtil.itemComponent("&cCancel &7(Right-click)");

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
        gameManager.getConfigurationManager().saveRollback(player);

        player.setGameMode(GameMode.CREATIVE);
        player.getInventory().clear();
        player.sendMessage(ChatUtil.prefixed("&aArena setup mode activated."));
        player.sendMessage(ChatUtil.prefixed("&eRemaining spawn spots to place: &b" + remainingSpawnSpots(temporaryArena)));
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

        player.getInventory().clear();
        gameManager.getConfigurationManager().loadRollback(player);

        player.sendActionBar(Component.empty());
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
                player.sendMessage(ChatUtil.prefixed("&cThat spawn spot is already set."));
                return;
            }
            if (arena.getSpawnSpots().size() >= MIN_SPAWN_SPOTS) {
                player.sendMessage(ChatUtil.prefixed("&cYou already placed the maximum of &e" + MIN_SPAWN_SPOTS + " &cspawn spots."));
                return;
            }
            arena.getSpawnSpots().add(location);
            if (arena.getSpawnLocation() == null) {
                arena.setSpawnLocation(location);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, 2);
            player.sendMessage(ChatUtil.prefixed("&aSpawn spot added! &d" + ChatUtil.displayLocation(location)));
            player.sendMessage(ChatUtil.prefixed("&eRemaining spawn spots to place: &b" + remainingSpawnSpots(arena)));

        } else if (Objects.equals(itemName, SET_ARENA_DISPLAY_NAME_ITEM_NAME)) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 50, 1);
            new AnvilGUI.Builder()
                    .title("◆ Arena name")
                    .itemLeft(new ItemBuilder(Material.PAPER).setName(ChatUtil.itemComponent("Choose a name")).toItemStack())
                    .plugin(MurderPlugin.getInstance())
                    .onClick((anvilPlayer, state) -> {
                        if (gameManager.getArenaManager().getArenas().stream().anyMatch(allArenas -> allArenas.getDisplayName().equalsIgnoreCase(state.getText()))) {
                            return List.of(AnvilGUI.ResponseAction.replaceInputText("This arena already exists!"));
                        }
                        arena.setDisplayName(state.getText());
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, 2);
                        player.sendMessage(ChatUtil.prefixed("&aSet arena display name to &3" + state.getText() + "&7!"));
                        return List.of(AnvilGUI.ResponseAction.close());
                    }).open(player);

        } else if (Objects.equals(itemName, ADD_EMERALD_SPOT_ITEM_NAME)) {
            Location location = player.getLocation();
            if (containsLocation(arena.getEmeraldSpots(), location)) {
                player.sendMessage(ChatUtil.prefixed("&cThat emerald spawn is already set."));
                return;
            }
            arena.getEmeraldSpots().add(location);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, 2);
            player.sendMessage(ChatUtil.prefixed("&aEmerald spawn added! &d" + ChatUtil.displayLocation(location)));

        } else if (Objects.equals(itemName, SAVE_ARENA_ITEM_NAME)) {

            if (arena.getName() == null || arena.getName().isEmpty()) {
                player.sendMessage(ChatUtil.prefixed("&cPlease set a display name for the arena."));
                return;
            }

            if (arena.getSpawnSpots().size() < MIN_SPAWN_SPOTS) {
                player.sendMessage(ChatUtil.prefixed("&cPlease set at least &e" + MIN_SPAWN_SPOTS + " &cspawn spots."));
                return;
            }

            if (arena.getEmeraldSpots().size() < MIN_EMERALD_SPOTS) {
                player.sendMessage(ChatUtil.prefixed("&cPlease set at least &e" + MIN_EMERALD_SPOTS + " &cemerald spawn spot."));
                return;
            }

            Arena saved = arena.toArena();
            gameManager.getArenaManager().addArena(saved);
            gameManager.getConfigurationManager().saveArena(saved);
            endWizard(player);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 50, 1);
            player.sendMessage(ChatUtil.prefixed("&aSuccessfully created &a" + saved.getDisplayName() + "&7!"));

        } else if (Objects.equals(itemName, CANCEL_ITEM_NAME)) {
            endWizard(player);
            player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 50, 2);
            player.sendMessage(ChatUtil.prefixed("&cArena creation cancelled."));
        }

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

}
