package fr.zeyx.murder.command.murder;

import fr.zeyx.murder.command.PlayerSubCommand;
import fr.zeyx.murder.command.CommandResult;
import fr.zeyx.murder.command.CommandArgs;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class LobbySubCommand implements PlayerSubCommand {

    private final GameManager gameManager;

    public LobbySubCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public CommandResult execute(Player player, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("set")) {
            return CommandResult.INVALID_USAGE;
        }

        Location location = player.getLocation();
        gameManager.getConfigurationManager().setLobbyLocation(location);
        player.sendMessage(ChatUtil.prefixed("&aLobby location set to &d" + ChatUtil.displayLocation(location)));
        return CommandResult.SUCCESS;
    }

    @Override
    public String getName() {
        return "lobby";
    }

    @Override
    public String getUsage() {
        return "/murder lobby set";
    }

    @Override
    public String getDescription() {
        return "Set the lobby location.";
    }

    @Override
    public String getPermission() {
        return "murder.admin";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args == null) {
            return List.of();
        }
        if (args.length == 1) {
            return CommandArgs.filterByPrefix(List.of("set"), args[0]);
        }
        return List.of();
    }
}
