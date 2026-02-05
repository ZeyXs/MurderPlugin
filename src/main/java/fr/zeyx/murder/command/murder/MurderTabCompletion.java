package fr.zeyx.murder.command.murder;

import fr.zeyx.murder.command.CommandArgs;
import fr.zeyx.murder.command.SubCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MurderTabCompletion implements TabCompleter {

    private final MurderBaseCommand baseCommand;

    public MurderTabCompletion(MurderBaseCommand baseCommand) {
        this.baseCommand = baseCommand;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {

        if (args == null || args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            Set<String> candidates = new LinkedHashSet<>();
            for (SubCommand<?> sub : baseCommand.getSubCommandList()) {
                String permission = sub.getPermission();
                if (permission != null && !permission.isBlank() && !sender.hasPermission(permission)) {
                    continue;
                }
                candidates.add(sub.getName());
                candidates.addAll(sub.getAliases());
            }
            return CommandArgs.filterByPrefix(candidates.stream().collect(Collectors.toList()), args[0]);
        }

        SubCommand<?> subCommand = baseCommand.findSubCommand(args[0]);
        if (subCommand == null) {
            return Collections.emptyList();
        }
        String permission = subCommand.getPermission();
        if (permission != null && !permission.isBlank() && !sender.hasPermission(permission)) {
            return Collections.emptyList();
        }
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return subCommand.tabComplete(sender, subArgs);
    }

}
