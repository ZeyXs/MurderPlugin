package fr.zeyx.murder.command.murder;

import fr.zeyx.murder.command.PlayerSubCommand;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class LobbySubCommand implements PlayerSubCommand {

    private final GameManager gameManager;

    public LobbySubCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public void execute(Player player, String[] args) {
        if (!player.hasPermission("murder.admin")) {
            player.sendMessage(ChatUtil.color("&cYou don't have permission to use this command."));
            return;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("set")) {
            player.sendMessage(ChatUtil.color("&cUsage : /murder lobby set"));
            return;
        }

        Location location = player.getLocation();
        gameManager.getConfigurationManager().setLobbyLocation(location);
        player.sendMessage(ChatUtil.color("&a◆ &7Lobby location set to &d" + ChatUtil.displayLocation(location)));
    }

    @Override
    public String getName() {
        return "lobby";
    }
}
