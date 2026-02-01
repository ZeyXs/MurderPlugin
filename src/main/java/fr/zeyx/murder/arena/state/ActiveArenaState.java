package fr.zeyx.murder.arena.state;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.ArenaState;
import fr.zeyx.murder.arena.task.ActiveArenaTask;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ActiveArenaState extends ArenaState {

    private final GameManager gameManager;
    private final Arena arena;
    private ActiveArenaTask activeArenaTask;

    public final List<UUID> alivePlayers = new ArrayList<>();
    private final Map<UUID, Role> roles = new HashMap<>();
    private UUID murdererId;
    private UUID detectiveId;

    public ActiveArenaState(GameManager gameManager, Arena arena) {
        this.gameManager = gameManager;
        this.arena = arena;
    }

    @Override
    public void onEnable() {
        super.onEnable();

        alivePlayers.addAll(arena.getActivePlayers());

        assignRoles();
        applySecretIdentities();

        activeArenaTask = new ActiveArenaTask(gameManager, arena, this);
        activeArenaTask.runTaskTimer(MurderPlugin.getInstance(), 0, 8);

        for (UUID playerId : alivePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            Location spawn = pickSpawnLocation();
            if (spawn != null) {
                player.teleport(spawn);
            }
            player.getInventory().clear();
            gameManager.getScoreboardManager().showGameBoard(player);
            notifyRole(player, roles.get(playerId));
        }
    }

    private Location pickSpawnLocation() {
        List<Location> spawnSpots = arena.getSpawnSpots();
        if (spawnSpots != null && !spawnSpots.isEmpty()) {
            return spawnSpots.get(ThreadLocalRandom.current().nextInt(spawnSpots.size()));
        }
        return arena.getSpawnLocation();
    }

    private void assignRoles() {
        roles.clear();
        murdererId = null;
        detectiveId = null;
        List<UUID> players = new ArrayList<>(alivePlayers);
        if (players.isEmpty()) {
            return;
        }
        Collections.shuffle(players);
        murdererId = players.get(0);
        roles.put(murdererId, Role.MURDERER);
        if (players.size() > 1) {
            detectiveId = players.get(1);
            roles.put(detectiveId, Role.DETECTIVE);
        }
        for (int i = 2; i < players.size(); i++) {
            roles.put(players.get(i), Role.BYSTANDER);
        }
    }

    private void applySecretIdentities() {
        List<Player> players = new ArrayList<>();
        for (UUID playerId : alivePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                players.add(player);
            }
        }
        gameManager.getSecretIdentityManager().applyUniqueIdentities(players);
    }

    private void notifyRole(Player player, Role role) {
        if (player == null || role == null) {
            return;
        }
        switch (role) {
            case MURDERER -> player.sendMessage(ChatUtil.prefixedComponent("&cYou are the Murderer."));
            case DETECTIVE -> player.sendMessage(ChatUtil.prefixedComponent("&bYou are the Detective."));
            case BYSTANDER -> player.sendMessage(ChatUtil.prefixedComponent("&aYou are a Bystander."));
        }
    }

    public Role getRole(UUID playerId) {
        return roles.get(playerId);
    }

    public enum Role {
        MURDERER,
        DETECTIVE,
        BYSTANDER
    }

    public void endGame() {
        for (UUID playerId : alivePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(ChatUtil.prefixedComponent("&7End of the game!"));
        }
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
            alivePlayers.remove(player.getUniqueId());
            roles.remove(player.getUniqueId());
            if (player.getUniqueId().equals(murdererId)) {
                murdererId = null;
            }
            if (player.getUniqueId().equals(detectiveId)) {
                detectiveId = null;
            }
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

        String itemName = event.getItem().getItemMeta().getDisplayName();
        if (itemName == null) return;
        if (ChatUtil.stripColor(itemName).equalsIgnoreCase(ChatUtil.stripColor(arena.LEAVE_ITEM))) {
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
