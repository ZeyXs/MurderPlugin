package fr.zeyx.murder.manager;

import fr.zeyx.murder.MurderPlugin;
import org.bukkit.plugin.PluginManager;

public class GameManager {

    private final ConfigurationManager configurationManager;
    private final ArenaManager arenaManager;
    private final SetupWizardManager setupWizardManager;
    private final ScoreboardManager scoreboardManager;

    public GameManager() {
        this.configurationManager = new ConfigurationManager();
        this.arenaManager = new ArenaManager(configurationManager.loadArenas());
        this.setupWizardManager = new SetupWizardManager(this);
        this.scoreboardManager = new ScoreboardManager();
        registerListeners();
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public ConfigurationManager getConfigurationManager() {
        return configurationManager;
    }

    public SetupWizardManager getSetupWizardManager() {
        return setupWizardManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    private void registerListeners() {
        PluginManager pluginManager = MurderPlugin.getInstance().getServer().getPluginManager();
        pluginManager.registerEvents(setupWizardManager, MurderPlugin.getInstance());
        pluginManager.registerEvents(scoreboardManager, MurderPlugin.getInstance());
    }

}
