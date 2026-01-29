package fr.zeyx.murder;

import fr.zeyx.murder.command.murder.MurderBaseCommand;
import fr.zeyx.murder.command.murder.MurderTabCompletion;
import fr.zeyx.murder.manager.GameManager;
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

    private void initCommand() {
        MurderBaseCommand murderBaseCommand = new MurderBaseCommand(gameManager);
        getCommand("murder").setExecutor(murderBaseCommand);
        getCommand("murder").setTabCompleter(new MurderTabCompletion(gameManager, murderBaseCommand));
    }

    public static MurderPlugin getInstance() {
        return instance;
    }
}
