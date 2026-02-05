package fr.zeyx.murder.command.murder;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.command.CommandArgs;
import fr.zeyx.murder.command.CommandResult;
import fr.zeyx.murder.command.PlayerSubCommand;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ArenaSubCommand implements PlayerSubCommand {

    private GameManager gameManager;

    public ArenaSubCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public CommandResult execute(Player player, String[] args) {

        if (args.length == 0) {
            return CommandResult.INVALID_USAGE;
        }

        if (args[0].equalsIgnoreCase("list")) {
            if (gameManager.getArenaManager().getArenas().isEmpty()) {
                player.sendMessage(ChatUtil.prefixed("&cNo arenas have been setup."));
                return CommandResult.FAILURE;
            }

            player.sendMessage(ChatUtil.prefixed("&7Arenas list:"));
            for (Arena arena : gameManager.getArenaManager().getArenas()) {
                player.sendMessage(ChatUtil.prefixed("&7- &a" + arena.getName()));
            }

            return CommandResult.SUCCESS;
        }

        if (args[0].equalsIgnoreCase("create")) {
            // TODO: Better permission system for all subcommands.
            if (!player.hasPermission("murder.admin")) {
                player.sendMessage(ChatUtil.prefixed("&cYou don't have permission to use this command."));
                return CommandResult.FAILURE;
            }
            gameManager.getSetupWizardManager().startWizard(player, null);
            return CommandResult.SUCCESS;
        }

        if (args[0].equalsIgnoreCase("edit")) {
            if (!player.hasPermission("murder.admin")) {
                player.sendMessage(ChatUtil.prefixed("&cYou don't have permission to use this command."));
                return CommandResult.FAILURE;
            }

            if (args.length <= 1) {
                return CommandResult.INVALID_USAGE;
            }

            player.sendMessage(ChatUtil.prefixed("&cThis command is currently disabled."));
            return CommandResult.FAILURE;

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
                return CommandResult.FAILURE;
            }

            if (args.length == 1) {
                return CommandResult.INVALID_USAGE;
            }

            String arenaName = CommandArgs.joinArgs(args, 1);
            Optional<Arena> optionalArena = gameManager.getArenaManager().findArena(arenaName);
            if (optionalArena.isEmpty()) {
                player.sendMessage(ChatUtil.prefixed("&cNo arena by that name exists."));
                return CommandResult.FAILURE;
            }

            Arena arena = optionalArena.get();
            gameManager.getArenaManager().removeArena(arena);
            gameManager.getConfigurationManager().removeArena(arena);
            player.sendMessage(ChatUtil.prefixed("&aArena successfully removed."));
            return CommandResult.SUCCESS;
        }

        return CommandResult.INVALID_USAGE;
    }

    @Override
    public String getName() {
        return "arena";
    }

    @Override
    public String getUsage() {
        return "/murder arena <list|create|edit|remove>";
    }

    @Override
    public String getDescription() {
        return "Arena management.";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args == null) {
            return List.of();
        }
        if (args.length == 1) {
            return CommandArgs.filterByPrefix(Arrays.asList("list", "create", "edit", "remove"), args[0]);
        }
        if (args.length == 2 && ("edit".equalsIgnoreCase(args[0]) || "remove".equalsIgnoreCase(args[0]))) {
            return CommandArgs.filterByPrefix(
                    gameManager.getArenaManager().getArenas().stream().map(Arena::getName).toList(),
                    args[1]
            );
        }
        return List.of();
    }

}
