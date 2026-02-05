package fr.zeyx.murder.command;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface SubCommand<T extends CommandSender> {

    CommandResult execute(T sender, String[] args);

    String getName();

    default List<String> getAliases() {
        return List.of();
    }

    default String getUsage() {
        return "/" + getName();
    }

    default String getDescription() {
        return "";
    }

    default String getPermission() {
        return null;
    }

    default List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
