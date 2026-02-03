package fr.zeyx.murder;

import fr.zeyx.murder.command.CommandRegistrar;
import fr.zeyx.murder.command.murder.MurderBaseCommand;
import fr.zeyx.murder.command.murder.MurderTabCompletion;
import fr.zeyx.murder.manager.GameManager;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public final class MurderPlugin extends JavaPlugin {

    private static MurderPlugin instance;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        instance = this;

        this.gameManager = new GameManager();

        initCommand();
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
    }

    private void initCommand() {
        CommandRegistrar registrar = new CommandRegistrar(this);
        registerAllCommands(registrar);
    }

    private void registerAllCommands(CommandRegistrar registrar) {
        MurderBaseCommand murderBaseCommand = new MurderBaseCommand(gameManager);
        MurderTabCompletion murderTabCompletion = new MurderTabCompletion(gameManager, murderBaseCommand);
        registerCommand(registrar, "murder", "Base command for the Murder mini-game.", murderBaseCommand, murderTabCompletion);
    }

    private void registerCommand(CommandRegistrar registrar, String name, String description, CommandExecutor executor, TabCompleter tabCompleter) {
        boolean registered = registrar.registerCommand(name, description, executor, tabCompleter);
        if (!registered) {
            getLogger().warning("Failed to register /" + name + " command. It may already exist.");
        }
    }

    public static MurderPlugin getInstance() {
        return instance;
    }
}
