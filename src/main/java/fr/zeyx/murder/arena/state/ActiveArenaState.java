package fr.zeyx.murder.arena.state;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.ArenaState;
import fr.zeyx.murder.arena.task.ActiveArenaTask;
import fr.zeyx.murder.game.GameSession;
import fr.zeyx.murder.manager.GameManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;

public class ActiveArenaState extends ArenaState {

    private final GameManager gameManager;
    private final Arena arena;
    private ActiveArenaTask activeArenaTask;
    private GameSession session;

    public ActiveArenaState(GameManager gameManager, Arena arena) {
        this.gameManager = gameManager;
        this.arena = arena;
    }

    @Override
    public void onEnable() {
        super.onEnable();

        session = new GameSession(gameManager, arena);
        session.start();

        activeArenaTask = new ActiveArenaTask(gameManager, arena, session);
        activeArenaTask.runTaskTimer(MurderPlugin.getInstance(), 0, 8);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (activeArenaTask != null) {
            activeArenaTask.cancel();
        }
    }

    public GameSession getSession() {
        return session;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                event.setCancelled(true);
                return;
            }
            event.setDamage(0);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (arena.isPlaying(player)) {
            arena.removePlayer(player, gameManager);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!arena.isPlaying(player)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.hasItem()) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if(!event.getItem().hasItemMeta()) return;

        Component itemName = event.getItem().getItemMeta().displayName();
        if (itemName == null) return;
        if (itemName.equals(arena.LEAVE_ITEM)) {
            event.setCancelled(true);
            arena.removePlayer(player, gameManager);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (arena.isPlaying(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (arena.isPlaying(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        Player player = (Player) event.getEntity();
        if (arena.isPlaying(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (arena.isPlaying(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(EntityDropItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (arena.isPlaying(player)) event.setCancelled(true);
        }
    }

}
