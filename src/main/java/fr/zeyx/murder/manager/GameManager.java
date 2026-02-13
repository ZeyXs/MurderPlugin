package fr.zeyx.murder.manager;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.setup.SetupWizardManager;
import fr.zeyx.murder.game.GameSession;
import fr.zeyx.murder.game.service.ArenaTabListService;
import fr.zeyx.murder.game.service.NametagService;
import fr.zeyx.murder.game.service.PlayerCollisionService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.List;

public class GameManager {

    private final ConfigurationManager configurationManager;
    private final ArenaManager arenaManager;
    private final SetupWizardManager setupWizardManager;
    private final ScoreboardManager scoreboardManager;
    private final SecretIdentityManager secretIdentityManager;
    private final ArenaTabListService arenaTabListService;
    private final CorpseManager corpseManager;
    private final GunManager gunManager;

    public GameManager() {
        this.configurationManager = new ConfigurationManager();
        this.arenaManager = new ArenaManager(configurationManager.loadArenas());
        this.setupWizardManager = new SetupWizardManager(this);
        this.scoreboardManager = new ScoreboardManager();
        this.secretIdentityManager = new SecretIdentityManager(configurationManager, arenaManager);
        this.arenaTabListService = new ArenaTabListService(arenaManager, secretIdentityManager);
        this.corpseManager = new CorpseManager(MurderPlugin.getInstance());
        this.gunManager = new GunManager();
        registerListeners();
        arenaTabListService.start();
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

    public ArenaTabListService getArenaTabListService() {
        return arenaTabListService;
    }

    public CorpseManager getCorpseManager() {
        return corpseManager;
    }

    public GunManager getGunManager() {
        return gunManager;
    }

    public void shutdown() {
        arenaTabListService.shutdown();
        setupWizardManager.shutdown();
        resetAllArenasForShutdown();
        arenaManager.resetVoteSession();
        corpseManager.clearCorpses();
        restoreGlobalPlayerState();
        NametagService.clearAll();
        PlayerCollisionService.clearAll();
    }

    private void resetAllArenasForShutdown() {
        for (Arena arena : new ArrayList<>(arenaManager.getArenas())) {
            if (arena == null) {
                continue;
            }
            arena.reset(this);
            arena.getActivePlayers().clear();
        }
    }

    private void restoreGlobalPlayerState() {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player player : onlinePlayers) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            secretIdentityManager.resetIdentity(player);
            GameSession.showNametag(player);
            PlayerCollisionService.restore(player);
            scoreboardManager.clear(player);
            player.setCustomChatCompletions(List.of());
        }
    }

    private void registerListeners() {
        PluginManager pluginManager = MurderPlugin.getInstance().getServer().getPluginManager();
        pluginManager.registerEvents(setupWizardManager, MurderPlugin.getInstance());
        pluginManager.registerEvents(scoreboardManager, MurderPlugin.getInstance());
        pluginManager.registerEvents(secretIdentityManager, MurderPlugin.getInstance());
        pluginManager.registerEvents(arenaTabListService, MurderPlugin.getInstance());
    }

}
