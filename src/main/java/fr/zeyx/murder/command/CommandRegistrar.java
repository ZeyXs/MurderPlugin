package fr.zeyx.murder.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class CommandRegistrar {

    private final JavaPlugin plugin;
    private final CommandMap commandMap;

    public CommandRegistrar(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.commandMap = resolveCommandMap();
    }

    public boolean registerCommand(String name, String description, CommandExecutor executor, TabCompleter tabCompleter) {
        String normalizedName = normalizeName(name);
        if (commandExists(normalizedName)) {
            return false;
        }

        Command command = new DynamicCommand(normalizedName, description, plugin, executor, tabCompleter);
        return commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), command);
    }

    private boolean commandExists(String name) {
        if (commandMap instanceof SimpleCommandMap simpleCommandMap) {
            return simpleCommandMap.getCommand(name) != null;
        }
        try {
            Method getCommand = commandMap.getClass().getMethod("getCommand", String.class);
            Object existing = getCommand.invoke(commandMap, name);
            return existing != null;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return false;
        }
    }

    private CommandMap resolveCommandMap() {
        try {
            Method getCommandMap = Bukkit.getServer().getClass().getMethod("getCommandMap");
            return (CommandMap) getCommandMap.invoke(Bukkit.getServer());
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Unable to resolve CommandMap.", exception);
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Command name cannot be null or empty.");
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static final class DynamicCommand extends Command implements PluginIdentifiableCommand {

        private final Plugin plugin;
        private final CommandExecutor executor;
        private final TabCompleter tabCompleter;

        private DynamicCommand(String name, String description, Plugin plugin, CommandExecutor executor, TabCompleter tabCompleter) {
            super(name, description == null ? "" : description, "/" + name, Collections.emptyList());
            this.plugin = plugin;
            this.executor = executor;
            this.tabCompleter = tabCompleter;
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            if (executor == null) {
                return false;
            }
            return executor.onCommand(sender, this, commandLabel, args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
            if (tabCompleter == null) {
                return super.tabComplete(sender, alias, args);
            }
            List<String> completions = tabCompleter.onTabComplete(sender, this, alias, args);
            return completions == null ? Collections.emptyList() : completions;
        }

        @Override
        public Plugin getPlugin() {
            return plugin;
        }
    }
}
