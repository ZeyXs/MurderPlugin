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

        Optional<Arena> currentArena = gameManager.getArenaManager().getCurrentArena(player);
        if (currentArena.isPresent()) {
            player.sendMessage(ChatUtil.color("&c◆ &7You are already in an arena."));
            return;
        }

        if (gameManager.getArenaManager().getArenas().size() == 1) {
            Arena arena = gameManager.getArenaManager().getArenas().get(0);
            arena.addPlayer(player, gameManager);
            return;
        }

        Optional<Arena> optionalArena = findArena(args);
        if (optionalArena.isEmpty()) {
            player.sendMessage(ChatUtil.color("&c◆ &7That arena doesn't exist."));
            return;
        }

        Arena arena = optionalArena.get();
        if (arena.getArenaState() instanceof ActiveArenaState) {
            player.sendMessage("&a◆ &7The game is still running. You joined as spectator.");
            return;
        }

        arena.addPlayer(player, gameManager);

    }

    public Optional<Arena> findArena(String[] commandArgs) {
        StringBuilder name = new StringBuilder();

        int index = 0;
        for (String arg : commandArgs) {
            name.append(arg);
            if (index == commandArgs.length - 2) {
                name.append("_");
            }
            index++;
        }
        return gameManager.getArenaManager().findArena(name.toString());
    }

    @Override
    public String getName() {
        return "join";
    }

}
