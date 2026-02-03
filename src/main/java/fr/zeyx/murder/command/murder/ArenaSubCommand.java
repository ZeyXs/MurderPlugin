package fr.zeyx.murder.command.murder;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.command.PlayerSubCommand;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import org.bukkit.entity.Player;

import java.util.Optional;

public class ArenaSubCommand implements PlayerSubCommand {

    private GameManager gameManager;

    public ArenaSubCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public void execute(Player player, String[] args) {

        if (args.length == 0) {
            player.sendMessage(ChatUtil.prefixed("&cUsage: /murder arena <list|create|edit|remove>"));
            return;
        }

        if (args[0].equalsIgnoreCase("list")) {
            if (gameManager.getArenaManager().getArenas().isEmpty()) {
                player.sendMessage(ChatUtil.prefixed("&cNo arenas have been setup."));
                return;
            }

            player.sendMessage(ChatUtil.prefixed("&7Arenas list:"));
            for (Arena arena : gameManager.getArenaManager().getArenas()) {
                player.sendMessage(ChatUtil.prefixed("&7- &a" + arena.getName()));
            }

            return;
        }

        if (args[0].equalsIgnoreCase("create")) {
            // TODO: Better permission system for all subcommands.
            if (!player.hasPermission("murder.admin")) {
                player.sendMessage(ChatUtil.prefixed("&cYou don't have permission to use this command."));
                return;
            }
            gameManager.getSetupWizardManager().startWizard(player, null);
            return;
        }

        if (args[0].equalsIgnoreCase("edit")) {
            if (!player.hasPermission("murder.admin")) {
                player.sendMessage(ChatUtil.prefixed("&cYou don't have permission to use this command."));
                return;
            }

            if (args.length <= 1) {
                player.sendMessage(ChatUtil.prefixed("&cUsage: /murder arena edit <name>"));
                return;
            }

            player.sendMessage(ChatUtil.prefixed("&cThis command is currently disabled."));
            return;

            /*
            Optional<Arena> optionalArena = gameManager.getArenaManager().findArena(args);
            if (optionalArena.isEmpty()) {
                player.sendMessage(Colorize.color("&c◆ &7No arena by that name exists."));
                return;
            }

            Arena arena = optionalArena.get();
            gameManager.getSetupWizardManager().startWizard(player, arena);
            return;*/
        }

        if (args[0].equalsIgnoreCase("remove")) {
            if (!player.hasPermission("murder.admin")) {
                player.sendMessage(ChatUtil.prefixed("&cYou don't have permission to use this command."));
                return;
            }

            if (args.length == 1) {
                player.sendMessage(ChatUtil.prefixed("&cUsage: /murder arena remove <name>"));
                return;
            }

            Optional<Arena> optionalArena = gameManager.getArenaManager().findArena(args[1]);
            if (optionalArena.isEmpty()) {
                player.sendMessage(ChatUtil.prefixed("&cNo arena by that name exists."));
                return;
            }

            Arena arena = optionalArena.get();
            gameManager.getArenaManager().removeArena(arena);
            gameManager.getConfigurationManager().removeArena(arena);
            player.sendMessage(ChatUtil.prefixed("&aArena successfully removed."));
            return;
        }

        player.sendMessage(ChatUtil.prefixed("&cUsage: /murder arena <list|create|edit|remove>"));
    }

    @Override
    public String getName() {
        return "arena";
    }

}
