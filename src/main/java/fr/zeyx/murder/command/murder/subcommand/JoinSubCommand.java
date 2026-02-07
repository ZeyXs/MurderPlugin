package fr.zeyx.murder.command.murder.subcommand;


import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.state.ActiveArenaState;
import fr.zeyx.murder.command.CommandResult;
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
    public CommandResult execute(Player player, String[] args) {
        if (gameManager.getArenaManager().getArenas().isEmpty()) {
            player.sendMessage(ChatUtil.prefixed("&cThere are no arenas to join."));
            return CommandResult.FAILURE;
        }

        if (args.length > 0) {
            return CommandResult.INVALID_USAGE;
        }

        Optional<Arena> currentArena = gameManager.getArenaManager().getCurrentArena(player);
        if (currentArena.isPresent()) {
            player.sendMessage(ChatUtil.prefixed("&cYou are already in an arena."));
            return CommandResult.FAILURE;
        }

        if (gameManager.getConfigurationManager().getLobbyLocation() == null) {
            player.sendMessage(ChatUtil.prefixed("&cLobby location is not set."));
            return CommandResult.FAILURE;
        }

        Arena arena = gameManager.getArenaManager().getArenas().getFirst();
        if (arena.getArenaState() instanceof ActiveArenaState) {
            player.sendMessage(ChatUtil.prefixed("&cThe game is already running."));
            return CommandResult.FAILURE;
        }

        arena.addPlayer(player, gameManager);
        return CommandResult.SUCCESS;
    }

    @Override
    public String getName() {
        return "join";
    }

    @Override
    public String getUsage() {
        return "/murder join";
    }

    @Override
    public String getDescription() {
        return "Join the game.";
    }

}
