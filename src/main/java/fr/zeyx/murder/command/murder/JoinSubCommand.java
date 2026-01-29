package fr.zeyx.murder.command.murder;


import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.state.ActiveArenaState;
import fr.zeyx.murder.command.PlayerSubCommand;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import org.bukkit.entity.Player;

import java.util.Optional;

public class JoinSubCommand implements PlayerSubCommand {

    private final GameManager gameManager;

    public JoinSubCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public void execute(Player player, String[] args) {
        if (gameManager.getArenaManager().getArenas().isEmpty()) {
            player.sendMessage(ChatUtil.color("&c◆ &7There are no arenas to join."));
            return;
        }

        if (args.length > 0) {
            player.sendMessage(ChatUtil.color("&cUsage : /murder join"));
            return;
        }

        Optional<Arena> currentArena = gameManager.getArenaManager().getCurrentArena(player);
        if (currentArena.isPresent()) {
            player.sendMessage(ChatUtil.color("&c◆ &7You are already in an arena."));
            return;
        }

        if (gameManager.getConfigurationManager().getLobbyLocation() == null) {
            player.sendMessage(ChatUtil.color("&c◆ &7Lobby location is not set."));
            return;
        }

        Arena arena = gameManager.getArenaManager().getArenas().get(0);
        if (arena.getArenaState() instanceof ActiveArenaState) {
            player.sendMessage(ChatUtil.color("&c◆ &7The game is already running."));
            return;
        }

        arena.addPlayer(player, gameManager);

    }

    @Override
    public String getName() {
        return "join";
    }

}
