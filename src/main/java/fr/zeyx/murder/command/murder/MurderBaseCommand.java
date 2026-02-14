package fr.zeyx.murder.command.murder;

import fr.zeyx.murder.command.CommandSenderSubCommand;
import fr.zeyx.murder.command.CommandResult;
import fr.zeyx.murder.command.PlayerSubCommand;
import fr.zeyx.murder.command.SubCommand;
import fr.zeyx.murder.command.murder.subcommand.*;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MurderBaseCommand implements CommandExecutor {

    private final List<SubCommand<?>> subCommandList = new ArrayList<>();

    public MurderBaseCommand(GameManager gameManager) {
        subCommandList.add(new ArenaSubCommand(gameManager));
        subCommandList.add(new DebugSubCommand(gameManager));
        subCommandList.add(new JoinSubCommand(gameManager));
        subCommandList.add(new LobbySubCommand(gameManager));
        subCommandList.add(new LeaveSubCommand(gameManager));
        subCommandList.add(new VoteSubCommand(gameManager));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendGlobalUsage(sender);
            return true;
        }

        String subCommandString = args[0];
        SubCommand<?> subCommand = findSubCommand(subCommandString);
        if (subCommand == null) {
            sendGlobalUsage(sender);
            return true;
        }

        String[] subCommandArgs = Arrays.copyOfRange(args, 1, args.length);
        String permission = subCommand.getPermission();
        if (permission != null && !permission.isBlank() && !sender.hasPermission(permission)) {
            sender.sendMessage(TextUtil.prefixed("&cYou don't have permission to use this command."));
            return true;
        }

        CommandResult result;
        if (subCommand instanceof PlayerSubCommand playerSubCommand) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(TextUtil.prefixed("&cYou must be a player to run this command."));
                return true;
            }
            result = playerSubCommand.execute((Player) sender, subCommandArgs);
        } else {
            CommandSenderSubCommand commandSenderSubCommand = (CommandSenderSubCommand) subCommand;
            result = commandSenderSubCommand.execute(sender, subCommandArgs);
        }

        if (result == CommandResult.INVALID_USAGE) {
            sendSubUsage(sender, subCommand);
        }
        return true;
    }

    public List<SubCommand<?>> getSubCommandList() {
        return subCommandList;
    }

    public SubCommand<?> findSubCommand(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.toLowerCase();
        for (SubCommand<?> subCommand : subCommandList) {
            if (subCommand.getName().equalsIgnoreCase(normalized)) {
                return subCommand;
            }
            for (String alias : subCommand.getAliases()) {
                if (alias != null && alias.equalsIgnoreCase(normalized)) {
                    return subCommand;
                }
            }
        }
        return null;
    }

    private void sendGlobalUsage(CommandSender sender) {
        List<SubCommand<?>> visible = subCommandList.stream()
                .filter(sub -> {
                    String permission = sub.getPermission();
                    return permission == null || permission.isBlank() || sender.hasPermission(permission);
                })
                .toList();
        String names = visible.stream()
                .map(SubCommand::getName)
                .collect(Collectors.joining("|"));
        sender.sendMessage(TextUtil.prefixed("&cUsage: /murder <" + names + ">"));
        for (SubCommand<?> subCommand : visible) {
            String description = subCommand.getDescription();
            if (description == null || description.isBlank()) {
                sender.sendMessage(TextUtil.prefixed("&7" + subCommand.getUsage()));
            } else {
                sender.sendMessage(TextUtil.prefixed("&7" + subCommand.getUsage() + " &8- &7" + description));
            }
        }
    }

    private void sendSubUsage(CommandSender sender, SubCommand<?> subCommand) {
        sender.sendMessage(TextUtil.prefixed("&cUsage: " + subCommand.getUsage()));
        String description = subCommand.getDescription();
        if (description != null && !description.isBlank()) {
            sender.sendMessage(TextUtil.prefixed("&7" + description));
        }
    }
}
