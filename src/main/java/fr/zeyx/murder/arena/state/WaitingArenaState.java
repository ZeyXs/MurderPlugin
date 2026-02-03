package fr.zeyx.murder.arena.state;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.ArenaState;
import fr.zeyx.murder.gui.EquipmentMenu;
import fr.zeyx.murder.gui.ProfileMenu;
import fr.zeyx.murder.gui.ShopMenu;
import fr.zeyx.murder.manager.GameManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class WaitingArenaState extends ArenaState {

    private final GameManager gameManager;
    private final Arena arena;

    public WaitingArenaState(GameManager gameManager, Arena arena) {
        this.gameManager = gameManager;
        this.arena = arena;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!arena.isPlaying(player)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.hasItem()) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if(!event.getItem().hasItemMeta()) return;

        event.setCancelled(true);
        ItemStack item = event.getItem();
        Component itemName = item.getItemMeta().displayName();
        if (itemName == null) return;
        if (itemName.equals(arena.LEAVE_ITEM)) {
            arena.removePlayer(player, gameManager);
            return;
        }
        if (itemName.equals(arena.HOW_TO_PLAY_ITEM)) {
            player.openBook(item);
            return;
        }
        if (itemName.equals(arena.SELECT_EQUIPMENT_ITEM)) {
            new EquipmentMenu().open(player);
            return;
        }
        if (itemName.equals(arena.VIEW_STATS_ITEM)) {
            new ProfileMenu().open(player);
            return;
        }
        if (itemName.equals(arena.STORE_ITEM)) {
            new ShopMenu().open(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (arena.isPlaying(player)) arena.removePlayer(player, gameManager);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (arena.isPlaying(player)) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (arena.isPlaying(damager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (!(event.getRemover() instanceof Player remover)) return;
        if (arena.isPlaying(remover)) {
            event.setCancelled(true);
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
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (arena.isPlaying(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        Player player = (Player) event.getEntity();
        if (arena.isPlaying(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (arena.isPlaying(player)) event.setCancelled(true);
    }

}
