package fr.zeyx.murder.manager;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.setup.SetupWizardManager;
import org.bukkit.plugin.PluginManager;

public class GameManager {

    private final ConfigurationManager configurationManager;
    private final ArenaManager arenaManager;
    private final SetupWizardManager setupWizardManager;
    private final ScoreboardManager scoreboardManager;
    private final SecretIdentityManager secretIdentityManager;
    private final CorpseManager corpseManager;

    public GameManager() {
        this.configurationManager = new ConfigurationManager();
        this.arenaManager = new ArenaManager(configurationManager.loadArenas());
        this.setupWizardManager = new SetupWizardManager(this);
        this.scoreboardManager = new ScoreboardManager();
        this.secretIdentityManager = new SecretIdentityManager(configurationManager);
        this.corpseManager = new CorpseManager(MurderPlugin.getInstance());
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

    public SecretIdentityManager getSecretIdentityManager() {
        return secretIdentityManager;
    }

    public CorpseManager getCorpseManager() {
        return corpseManager;
    }

    public void shutdown() {
        corpseManager.clearCorpses();
    }

    private void registerListeners() {
        PluginManager pluginManager = MurderPlugin.getInstance().getServer().getPluginManager();
        pluginManager.registerEvents(setupWizardManager, MurderPlugin.getInstance());
        pluginManager.registerEvents(scoreboardManager, MurderPlugin.getInstance());
        pluginManager.registerEvents(secretIdentityManager, MurderPlugin.getInstance());
    }

}
