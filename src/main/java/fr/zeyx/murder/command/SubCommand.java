package fr.zeyx.murder.command;

import org.bukkit.command.CommandSender;

public interface SubCommand<T extends CommandSender> {

    void execute(T sender, String[] args);
    String getName();

}
