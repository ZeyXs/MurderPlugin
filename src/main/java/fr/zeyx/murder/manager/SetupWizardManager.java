package fr.zeyx.murder.manager;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.TemporaryArena;
import fr.zeyx.murder.manager.task.SetupWizardTask;
import fr.zeyx.murder.util.ChatUtil;
import fr.zeyx.murder.util.ItemBuilder;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.wesjd.anvilgui.AnvilGUI;
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

import java.util.*;

public class SetupWizardManager implements Listener {

    private GameManager gameManager;
    private Map<UUID, TemporaryArena> inWizard = new HashMap<>();

    private final String SET_ARENA_SPAWN_ITEM_NAME = ChatUtil.color("&aSet Spawn Location &7(Right-click)");
    private final String SET_ARENA_DISPLAY_NAME_ITEM_NAME = ChatUtil.color("&3Set Arena Name &7(Right-click)");
    private final String SAVE_ARENA_ITEM_NAME = ChatUtil.color("&aSave Arena &7(Right-click)");
    private final String CANCEL_ITEM_NAME = ChatUtil.color("&cCancel &7(Right-click)");

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
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 50, 1);

        Map<Material, ImmutablePair<String, Integer>> wizardItems = Map.of(
                Material.NAME_TAG, new ImmutablePair<>(SET_ARENA_DISPLAY_NAME_ITEM_NAME, 0),
                Material.ENDER_PEARL, new ImmutablePair<>(SET_ARENA_SPAWN_ITEM_NAME, 1),
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

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
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
        String itemName = event.getItem().getItemMeta().getDisplayName();
        TemporaryArena arena = inWizard.get(player.getUniqueId());

        if(itemName.equalsIgnoreCase(SET_ARENA_SPAWN_ITEM_NAME)) {
            Location location = player.getLocation();
            arena.setSpawnLocation(location);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, 2);
            player.sendMessage(ChatUtil.prefixed("&aPlayers spawn location set! &d" + ChatUtil.displayLocation(location)));

        } else if (itemName.equalsIgnoreCase(SET_ARENA_DISPLAY_NAME_ITEM_NAME)) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 50, 1);
            new AnvilGUI.Builder()
                    .title("◆ Arena name")
                    .itemLeft(new ItemBuilder(Material.PAPER).setName("Choose a name").toItemStack())
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

        } else if (itemName.equalsIgnoreCase(SAVE_ARENA_ITEM_NAME)) {

            if (arena.getName() == null || arena.getName().isEmpty()) {
                player.sendMessage(ChatUtil.prefixed("&cPlease set a display name for the arena."));
                return;
            }

            if (arena.getSpawnLocation() == null) {
                player.sendMessage(ChatUtil.prefixed("&cPlease set a player spawn location for the arena."));
                return;
            }

            Arena saved = arena.toArena();
            gameManager.getArenaManager().addArena(saved);
            gameManager.getConfigurationManager().saveArena(saved);
            endWizard(player);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 50, 1);
            player.sendMessage(ChatUtil.prefixed("&aSuccessfully created &a" + saved.getDisplayName() + "&7!"));

        } else if (itemName.equalsIgnoreCase(CANCEL_ITEM_NAME)) {
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

}
