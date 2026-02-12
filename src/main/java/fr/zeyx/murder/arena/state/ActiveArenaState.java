package fr.zeyx.murder.arena.state;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.task.ActiveArenaTask;
import fr.zeyx.murder.game.GameSession;
import fr.zeyx.murder.game.feature.GunFeature;
import fr.zeyx.murder.game.feature.KnifeFeature;
import fr.zeyx.murder.game.service.TabCompletionService;
import fr.zeyx.murder.manager.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChatTabCompleteEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public class ActiveArenaState extends PlayingArenaState {

    private final GameManager gameManager;
    private final Arena arena;
    private final TabCompletionService tabCompletionService;
    private final GunFeature gunFeature;
    private final KnifeFeature knifeFeature;
    private ActiveArenaTask activeArenaTask;
    private GameSession session;

    public ActiveArenaState(GameManager gameManager, Arena arena) {
        super(gameManager, arena);
        this.gameManager = gameManager;
        this.arena = arena;
        this.tabCompletionService = new TabCompletionService(gameManager.getSecretIdentityManager());
        this.gunFeature = new GunFeature(gameManager, arena);
        this.knifeFeature = new KnifeFeature(arena);
    }

    @Override
    public void onEnable() {
        super.onEnable();

        session = new GameSession(gameManager, arena, gunFeature);
        session.start();

        activeArenaTask = new ActiveArenaTask(gameManager, arena, this, session);
        activeArenaTask.runTaskTimer(MurderPlugin.getInstance(), 0, 1);
    }

    @Override
    public void onDisable() {
        if (activeArenaTask != null) {
            activeArenaTask.cancel();
        }
        gunFeature.clearRuntimeState();
        knifeFeature.clearRuntimeState();
        super.onDisable();
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
        Player player = event.getPlayer();
        if (!arena.isPlaying(player)) {
            return;
        }
        if (session != null) {
            session.handlePlayerDisconnect(player);
        }
        arena.removePlayer(player, gameManager);
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
        if (knifeFeature.handleThrowInteract(event, player, session)) {
            return;
        }
        if (gunFeature.handleInteract(event, player, session)) {
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

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        gunFeature.onProjectileHit(event, session);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        knifeFeature.onDamageByEntity(event, session);
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        knifeFeature.onKnifePickup(event, session);
        gunFeature.onGunPickup(event, session);
    }

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent event) {
        knifeFeature.onKnifeDespawn(event);
        gunFeature.onGunDespawn(event);
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

    public void clearAllKnifeItems() {
        knifeFeature.clearAllKnifeItems();
    }

    public void clearAllGunItems() {
        gunFeature.clearAllDroppedGuns();
    }
}
