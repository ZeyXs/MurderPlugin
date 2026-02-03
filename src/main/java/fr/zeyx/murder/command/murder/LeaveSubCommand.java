package fr.zeyx.murder.command.murder;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.command.PlayerSubCommand;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import org.bukkit.entity.Player;

import java.util.Optional;

public class LeaveSubCommand implements PlayerSubCommand {

    private final GameManager gameManager;

    public LeaveSubCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public void execute(Player player, String[] args) {

        Optional<Arena> currentArena = gameManager.getArenaManager().getCurrentArena(player);
        if (currentArena.isEmpty()) {
            player.sendMessage(ChatUtil.prefixed("&cYou are not in an arena."));
            return;
        }

        Arena arena = currentArena.get();
        arena.removePlayer(player, gameManager);

    }

    @Override
    public String getName() {
        return "leave";
    }

}
