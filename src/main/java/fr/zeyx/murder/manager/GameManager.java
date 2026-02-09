package fr.zeyx.murder.manager;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.setup.SetupWizardManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.UUID;

public class GameManager {

    private final ConfigurationManager configurationManager;
    private final ArenaManager arenaManager;
    private final SetupWizardManager setupWizardManager;
    private final ScoreboardManager scoreboardManager;
    private final SecretIdentityManager secretIdentityManager;
    private final CorpseManager corpseManager;
    private final GunManager gunManager;

    public GameManager() {
        this.configurationManager = new ConfigurationManager();
        this.arenaManager = new ArenaManager(configurationManager.loadArenas());
        this.setupWizardManager = new SetupWizardManager(this);
        this.scoreboardManager = new ScoreboardManager();
        this.secretIdentityManager = new SecretIdentityManager(configurationManager, arenaManager);
        this.corpseManager = new CorpseManager(MurderPlugin.getInstance());
        this.gunManager = new GunManager(this);
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

    public GunManager getGunManager() {
        return gunManager;
    }

    public void shutdown() {
        for (Arena arena : new ArrayList<>(arenaManager.getArenas())) {
            if (arena == null) {
                continue;
            }
            for (UUID playerId : new ArrayList<>(arena.getActivePlayers())) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    arena.removePlayer(player, this);
                    continue;
                }
                // Player already offline, remove stale arena entry.
                arena.getActivePlayers().remove(playerId);
            }
        }
        corpseManager.clearCorpses();
        gunManager.shutdown();
    }

    private void registerListeners() {
        PluginManager pluginManager = MurderPlugin.getInstance().getServer().getPluginManager();
        pluginManager.registerEvents(setupWizardManager, MurderPlugin.getInstance());
        pluginManager.registerEvents(scoreboardManager, MurderPlugin.getInstance());
        pluginManager.registerEvents(secretIdentityManager, MurderPlugin.getInstance());
        pluginManager.registerEvents(gunManager, MurderPlugin.getInstance());
    }

}
