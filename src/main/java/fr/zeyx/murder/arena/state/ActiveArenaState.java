package fr.zeyx.murder.arena.state;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.task.ActiveArenaTask;
import fr.zeyx.murder.game.GameSession;
import fr.zeyx.murder.game.QuickChatMenu;
import fr.zeyx.murder.manager.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ActiveArenaState extends PlayingArenaState {

    private final GameManager gameManager;
    private final Arena arena;
    private ActiveArenaTask activeArenaTask;
    private GameSession session;
    private final Map<UUID, ItemStack[]> chatHotbars = new HashMap<>();
    private final Set<UUID> chatMenuCooldown = new HashSet<>();

    public ActiveArenaState(GameManager gameManager, Arena arena) {
        super(gameManager, arena);
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

    @Override
    protected boolean useSecretIdentityInChat() {
        return true;
    }

    @Override
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        super.onQuit(event);
        Player player = event.getPlayer();
        chatHotbars.remove(player.getUniqueId());
        chatMenuCooldown.remove(player.getUniqueId());
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
        if (chatMenuCooldown.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        String legacyName = LegacyComponentSerializer.legacySection().serialize(itemName);
        if (QuickChatMenu.CHAT_BOOK_NAME.equals(legacyName)) {
            event.setCancelled(true);
            openChatMenu(player);
            return;
        }
        if (chatHotbars.containsKey(player.getUniqueId())) {
            if (QuickChatMenu.CLOSE_NAME.equals(legacyName)) {
                event.setCancelled(true);
                closeChatMenu(player);
                return;
            }
            String message = QuickChatMenu.resolveMessage(legacyName);
            if (message != null) {
                event.setCancelled(true);
                closeChatMenu(player);
                String resolvedMessage = resolveQuickChatMessage(player, legacyName, message);
                sendQuickChatMessage(player, resolvedMessage);
                return;
            }
        }
        if (itemName.equals(arena.LEAVE_ITEM)) {
            event.setCancelled(true);
            arena.removePlayer(player, gameManager);
        }
    }

    private void openChatMenu(Player player) {
        UUID playerId = player.getUniqueId();
        if (chatHotbars.containsKey(playerId)) {
            return;
        }
        ItemStack[] saved = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            saved[i] = player.getInventory().getItem(i);
        }
        chatHotbars.put(playerId, saved);
        ItemStack[] menu = QuickChatMenu.buildMenuHotbar();
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, menu[i]);
        }
        applyChatMenuCooldown(playerId);
    }

    private void closeChatMenu(Player player) {
        UUID playerId = player.getUniqueId();
        ItemStack[] saved = chatHotbars.remove(playerId);
        if (saved == null) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, saved[i]);
        }
        player.getInventory().setHeldItemSlot(8);
        applyChatMenuCooldown(playerId);
    }

    private void sendQuickChatMessage(Player sender, String message) {
        String name = resolveChatName(sender);
        String formatted = ChatColor.translateAlternateColorCodes('&', "&f" + name + " &8• &7" + message);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.sendMessage(formatted);
        }
    }

    private String resolveQuickChatMessage(Player sender, String legacyName, String defaultMessage) {
        if (!QuickChatMenu.LIME_DYE_NAME.equals(legacyName)) {
            return ChatColor.stripColor(defaultMessage);
        }
        List<String> nearbyNames = findNearbyPlayerNames(sender, 15.0);
        if (nearbyNames.isEmpty()) {
            return "I am next to nobody!";
        }
        return "I am next to " + String.join(", ", nearbyNames) + "!";
    }

    private List<String> findNearbyPlayerNames(Player sender, double radius) {
        List<String> names = new ArrayList<>();
        if (sender == null || sender.getWorld() == null) {
            return names;
        }
        double radiusSquared = radius * radius;
        for (UUID playerId : arena.getActivePlayers()) {
            if (sender.getUniqueId().equals(playerId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || player.getWorld() == null) {
                continue;
            }
            if (!player.getWorld().equals(sender.getWorld())) {
                continue;
            }
            if (player.getLocation().distanceSquared(sender.getLocation()) <= radiusSquared) {
                names.add(resolveChatName(player));
            }
        }
        return names;
    }

    private String resolveChatName(Player player) {
        String identity = gameManager.getSecretIdentityManager().getCurrentIdentityName(player.getUniqueId());
        if (identity != null && !identity.isBlank()) {
            return identity;
        }
        return player.getName();
    }

    public void clearChatMenu(Player player) {
        if (player == null) {
            return;
        }
        chatHotbars.remove(player.getUniqueId());
        chatMenuCooldown.remove(player.getUniqueId());
    }

    private void applyChatMenuCooldown(UUID playerId) {
        chatMenuCooldown.add(playerId);
        Bukkit.getScheduler().runTaskLater(MurderPlugin.getInstance(), task -> chatMenuCooldown.remove(playerId), 1L);
    }

}
