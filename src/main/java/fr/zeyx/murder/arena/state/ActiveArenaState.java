package fr.zeyx.murder.arena.state;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.task.ActiveArenaTask;
import fr.zeyx.murder.game.GameSession;
import fr.zeyx.murder.game.session.SessionTabCompletionService;
import fr.zeyx.murder.manager.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChatTabCompleteEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public class ActiveArenaState extends PlayingArenaState {

    private final GameManager gameManager;
    private final Arena arena;
    private final SessionTabCompletionService tabCompletionService;
    private ActiveArenaTask activeArenaTask;
    private GameSession session;

    public ActiveArenaState(GameManager gameManager, Arena arena) {
        super(gameManager, arena);
        this.gameManager = gameManager;
        this.arena = arena;
        this.tabCompletionService = new SessionTabCompletionService(gameManager.getSecretIdentityManager());
    }

    @Override
    public void onEnable() {
        super.onEnable();

        session = new GameSession(gameManager, arena);
        session.start();

        activeArenaTask = new ActiveArenaTask(gameManager, arena, session);
        activeArenaTask.runTaskTimer(MurderPlugin.getInstance(), 0, 1);
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

    @Override
    protected boolean useSecretIdentityInChat() {
        return true;
    }

    @Override
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        super.onQuit(event);
        if (session != null) {
            session.clearTransientState(event.getPlayer());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!arena.isPlaying(player)) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!event.hasItem()) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!event.getItem().hasItemMeta()) {
            return;
        }

        Component itemName = event.getItem().getItemMeta().displayName();
        if (itemName == null || session == null) {
            return;
        }

        String legacyName = LegacyComponentSerializer.legacySection().serialize(itemName);
        if (session.handleInteract(player, itemName, legacyName)) {
            event.setCancelled(true);
        }
    }

    @Override
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !arena.isPlaying(player)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            player.setFallDistance(0f);
        }
        event.setCancelled(true);
        event.setDamage(0.0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerChatTabComplete(PlayerChatTabCompleteEvent event) {
        if (session == null || !arena.isPlaying(event.getPlayer())) {
            return;
        }
        tabCompletionService.handlePlayerChatTabComplete(event, arena);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        if (session == null || event.isCommand() || !(event.getSender() instanceof Player player) || !arena.isPlaying(player)) {
            return;
        }
        tabCompletionService.handleAsyncTabComplete(event, arena);
    }
}
