package fr.zeyx.murder.command.murder;

import fr.zeyx.murder.command.CommandSenderSubCommand;
import fr.zeyx.murder.command.PlayerSubCommand;
import fr.zeyx.murder.command.SubCommand;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class MurderBaseCommand implements CommandExecutor {

    private List<SubCommand<?>> subCommandList = new ArrayList<>();

    public MurderBaseCommand(GameManager gameManager) {
        subCommandList.add(new ArenaSubCommand(gameManager));
        subCommandList.add(new DebugSubCommand(gameManager));
        subCommandList.add(new JoinSubCommand(gameManager));
        subCommandList.add(new LobbySubCommand(gameManager));
        subCommandList.add(new LeaveSubCommand(gameManager));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatUtil.color("&cUsage : /murder <join|arena|leave|debug|lobby>"));
            return false;
        }

        String subCommandString = args[0];
        Optional<SubCommand<?>> subCommandOptional = subCommandList.stream()
                .filter(subCommand -> subCommand.getName().equalsIgnoreCase(subCommandString))
                .findFirst();


        if (subCommandOptional.isEmpty()) {
            sender.sendMessage(ChatUtil.color("&cUsage : /murder <join|arena|leave|debug|lobby>"));
            return false;
        }

        String[] subCommandArgs = Arrays.copyOfRange(args, 1, args.length);
        SubCommand<?> subCommand = subCommandOptional.get();
        if (subCommand instanceof PlayerSubCommand playerSubCommand) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatUtil.color("&cYou must be a player to run this command."));
                return false;
            }
            playerSubCommand.execute((Player) sender, subCommandArgs);
        } else {
            CommandSenderSubCommand commandSenderSubCommand = (CommandSenderSubCommand) subCommand;
            commandSenderSubCommand.execute(sender, subCommandArgs);
        }

        return true;
    }

    public List<SubCommand<?>> getSubCommandList() {
        return subCommandList;
    }
}
