package fr.zeyx.murder.command.murder.subcommand;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.command.CommandResult;
import fr.zeyx.murder.command.PlayerSubCommand;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.TextUtil;
import org.bukkit.entity.Player;

import java.util.Optional;

public class LeaveSubCommand implements PlayerSubCommand {

    private final GameManager gameManager;

    public LeaveSubCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public CommandResult execute(Player player, String[] args) {
        if (args.length > 0) {
            return CommandResult.INVALID_USAGE;
        }

        Optional<Arena> currentArena = gameManager.getArenaManager().getCurrentArena(player);
        if (currentArena.isEmpty()) {
            player.sendMessage(TextUtil.prefixed("&cYou are not in an arena."));
            return CommandResult.FAILURE;
        }

        Arena arena = currentArena.get();
        arena.removePlayer(player, gameManager);
        return CommandResult.SUCCESS;

    }

    @Override
    public String getName() {
        return "leave";
    }

    @Override
    public String getUsage() {
        return "/murder leave";
    }

    @Override
    public String getDescription() {
        return "Leave the game.";
    }

}
