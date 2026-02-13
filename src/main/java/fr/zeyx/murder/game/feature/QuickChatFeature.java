package fr.zeyx.murder.game.feature;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.game.QuickChatMenu;
import fr.zeyx.murder.manager.SecretIdentityManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class QuickChatFeature {

    private static final String NO_DEAD_BODY_NEARBY_MESSAGE = "&cYou are not near anyone!";

    private final Arena arena;
    private final SecretIdentityManager secretIdentityManager;
    private final Supplier<? extends Collection<UUID>> alivePlayersSupplier;
    private final Map<UUID, ItemStack[]> chatHotbars = new HashMap<>();
    private final Set<UUID> chatMenuCooldown = new HashSet<>();

    public QuickChatFeature(
            Arena arena,
            SecretIdentityManager secretIdentityManager,
            Supplier<? extends Collection<UUID>> alivePlayersSupplier
    ) {
        this.arena = arena;
        this.secretIdentityManager = secretIdentityManager;
        this.alivePlayersSupplier = alivePlayersSupplier;
    }

    public boolean handleInteract(Player player, String legacyName) {
        if (player == null || legacyName == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (chatMenuCooldown.contains(playerId)) {
            return true;
        }
        if (QuickChatMenu.CHAT_BOOK_NAME.equals(legacyName)) {
            openChatMenu(player);
            return true;
        }
        if (!chatHotbars.containsKey(playerId)) {
            return false;
        }
        if (QuickChatMenu.CLOSE_NAME.equals(legacyName)) {
            closeChatMenu(player);
            return true;
        }
        String message = QuickChatMenu.resolveMessage(legacyName);
        if (message == null) {
            return true;
        }
        closeChatMenu(player);
        String resolvedMessage = resolveQuickChatMessage(player, legacyName, message);
        if (resolvedMessage != null) {
            sendQuickChatMessage(player, resolvedMessage);
        }
        return true;
    }

    public void clearTransientState(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        chatHotbars.remove(playerId);
        chatMenuCooldown.remove(playerId);
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
        String formatted = ChatColor.translateAlternateColorCodes('&', "&f" + resolveChatName(sender) + " &8• &7" + message);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.sendMessage(formatted);
        }
    }

    private String resolveQuickChatMessage(Player sender, String legacyName, String defaultMessage) {
        if (!QuickChatMenu.LIME_DYE_NAME.equals(legacyName)) {
            return ChatColor.stripColor(defaultMessage);
        }
        List<String> nearbyNames = findNearbyAlivePlayerNames(sender, 15.0);
        if (nearbyNames.isEmpty()) {
            sendNoDeadBodyNearbyMessage(sender);
            return null;
        }
        return "I am next to " + String.join("&7, ", nearbyNames) + "&7!";
    }

    private List<String> findNearbyAlivePlayerNames(Player sender, double radius) {
        List<String> names = new ArrayList<>();
        if (sender == null || sender.getWorld() == null) {
            return names;
        }
        double radiusSquared = radius * radius;
        for (UUID playerId : resolveAlivePlayerIds()) {
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

    private Collection<UUID> resolveAlivePlayerIds() {
        if (alivePlayersSupplier == null) {
            return arena.getActivePlayers();
        }
        Collection<UUID> alivePlayers = alivePlayersSupplier.get();
        if (alivePlayers == null) {
            return List.of();
        }
        return alivePlayers;
    }

    private void sendNoDeadBodyNearbyMessage(Player player) {
        if (player == null) {
            return;
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', NO_DEAD_BODY_NEARBY_MESSAGE));
    }

    private String resolveChatName(Player player) {
        String displayName = secretIdentityManager.getColoredName(player);
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return player.getName();
    }

    private void applyChatMenuCooldown(UUID playerId) {
        chatMenuCooldown.add(playerId);
        Bukkit.getScheduler().runTaskLater(MurderPlugin.getInstance(), task -> chatMenuCooldown.remove(playerId), 1L);
    }
}
