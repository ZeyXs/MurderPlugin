package fr.zeyx.murder.arena.state;

import fr.zeyx.murder.gui.EquipmentMenu;
import fr.zeyx.murder.gui.ProfileMenu;
import fr.zeyx.murder.gui.ShopMenu;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.manager.GameManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class WaitingArenaState extends PlayingArenaState {

    public WaitingArenaState(GameManager gameManager, Arena arena) {
        super(gameManager, arena);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Arena arena = getArena();
        GameManager gameManager = getGameManager();
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

}
