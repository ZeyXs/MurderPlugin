package fr.zeyx.murder.arena.state;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.ArenaState;
import fr.zeyx.murder.manager.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public abstract class PlayingArenaState extends ArenaState {

    private final GameManager gameManager;
    private final Arena arena;

    protected PlayingArenaState(GameManager gameManager, Arena arena) {
        this.gameManager = gameManager;
        this.arena = arena;
    }

    protected final GameManager getGameManager() {
        return gameManager;
    }

    protected final Arena getArena() {
        return arena;
    }

    protected boolean shouldCancelFor(Player player) {
        return arena.isPlaying(player);
    }

    protected boolean useSecretIdentityInChat() {
        return false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (shouldCancelFor(player)) {
            arena.removePlayer(player, gameManager);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!shouldCancelFor(player)) {
            return;
        }
        String name = player.getName();
        if (useSecretIdentityInChat()) {
            name = gameManager.getSecretIdentityManager().getColoredName(player);
            event.setMessage(gameManager.getSecretIdentityManager().colorizeIdentityMentions(arena.getActivePlayers(), event.getMessage(), ChatColor.GRAY));
        }
        event.setFormat(ChatColor.translateAlternateColorCodes('&', "&f" + name + " &8• &7%2$s"));
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && shouldCancelFor(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player damager && shouldCancelFor(damager)) {
            event.setCancelled(true);
            return;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter
                && shouldCancelFor(shooter)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player remover && shouldCancelFor(remover)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (shouldCancelFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (shouldCancelFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && shouldCancelFor(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && shouldCancelFor(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (shouldCancelFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

}
