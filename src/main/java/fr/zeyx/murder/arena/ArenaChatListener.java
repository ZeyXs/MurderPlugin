package fr.zeyx.murder.arena;

import fr.zeyx.murder.arena.state.ActiveArenaState;
import fr.zeyx.murder.manager.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.ChatColor;

import java.util.Optional;

public class ArenaChatListener implements Listener {

    private final GameManager gameManager;

    public ArenaChatListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Optional<Arena> arena = gameManager.getArenaManager().getCurrentArena(player);
        if (arena.isEmpty()) {
            return;
        }
        String name = player.getName();
        if (arena.get().getArenaState() instanceof ActiveArenaState) {
            String identity = gameManager.getSecretIdentityManager().getCurrentIdentityName(player.getUniqueId());
            if (identity != null && !identity.isBlank()) {
                name = identity;
            }
        }
        event.setFormat(ChatColor.translateAlternateColorCodes('&', "&f" + name + " &8• &7%2$s"));
    }
}
