package fr.zeyx.murder.manager;

import fr.mrmicky.fastboard.FastBoard;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ScoreboardManager implements Listener {

    private final Map<UUID, FastBoard> boards = new HashMap<>();

    public void showLobbyBoard(Player player, int lobbyPlayers) {
        String playerCountColor = lobbyPlayers < 4 ? "&c" : "&a";
        updateBoard(player, "&c&lMURDER", Arrays.asList(
                "",
                "&b&lPlaying",
                playerCountColor + lobbyPlayers,
                " ",
                "&e&lStats",
                "Rank: &7Coal",
                "Coins: &70",
                "Karma: &71000",
                "  ",
                "&a&lRole Chance",
                "Murderer: &70.0%",
                "Detective: &70.0%",
                "   ",
                "&bplay.server.net"
        ));
    }

    public void showGameBoard(Player player) {
        updateBoard(player, "&c&lMURDER", Arrays.asList(
                "",
                "&bIn Game",
                "&7Good luck!",
                " ",
                "&bplay.server.net"
        ));
    }

    public void updateBoard(Player player, String title, List<String> lines) {
        FastBoard board = boards.computeIfAbsent(player.getUniqueId(), key -> new FastBoard(player));
        if (title != null) {
            board.updateTitle(ChatColor.translateAlternateColorCodes('&', title));
        }
        board.updateLines(colorize(lines));
    }

    public void updateLines(Player player, List<String> lines) {
        FastBoard board = boards.get(player.getUniqueId());
        if (board == null) {
            updateBoard(player, "&c&lMURDER", lines);
            return;
        }
        board.updateLines(colorize(lines));
    }

    public void clear(Player player) {
        FastBoard board = boards.remove(player.getUniqueId());
        if (board != null) {
            board.delete();
        }
    }

    public boolean hasBoard(Player player) {
        return boards.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    private List<String> colorize(List<String> lines) {
        if (lines == null) {
            return List.of();
        }
        return lines.stream()
                .map(line -> line == null ? "" : ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList());
    }
}
