package fr.zeyx.murder.command.murder;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.state.ActiveArenaState;
import fr.zeyx.murder.arena.state.InitArenaState;
import fr.zeyx.murder.arena.state.StartingArenaState;
import fr.zeyx.murder.arena.state.WaitingArenaState;
import fr.zeyx.murder.command.PlayerSubCommand;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import org.bukkit.entity.Player;

import java.util.Optional;

public class DebugSubCommand implements PlayerSubCommand {

    private final GameManager gameManager;

    public DebugSubCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public void execute(Player player, String[] args) {
        if (!player.hasPermission("murder.admin")) {
            player.sendMessage(ChatUtil.prefixed("&cYou don't have permission to use this command."));
            return;
        }

        if (args.length == 0) {
            player.sendMessage(ChatUtil.prefixed("&cUsage: /murder debug <start|identity|identityreset|corpse|corpseclear> [arena]"));
            return;
        }

        if (args[0].equalsIgnoreCase("identity")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
                gameManager.getSecretIdentityManager().resetIdentity(player);
                return;
            }
            gameManager.getSecretIdentityManager().applyRandomIdentity(player);
            return;
        }

        if (args[0].equalsIgnoreCase("identityreset") || args[0].equalsIgnoreCase("resetidentity")) {
            gameManager.getSecretIdentityManager().resetIdentity(player);
            return;
        }

        if (args[0].equalsIgnoreCase("corpse")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
                int cleared = gameManager.getCorpseManager().clearCorpses();
                player.sendMessage(ChatUtil.prefixed("&eDebug: cleared &6" + cleared + "&e corpses."));
                return;
            }
            gameManager.getCorpseManager().spawnCorpse(player);
            player.sendMessage(ChatUtil.prefixed("&eDebug: corpse spawned."));
            return;
        }

        if (args[0].equalsIgnoreCase("corpseclear") || args[0].equalsIgnoreCase("clearcorpse")) {
            int cleared = gameManager.getCorpseManager().clearCorpses();
            player.sendMessage(ChatUtil.prefixed("&eDebug: cleared &6" + cleared + "&e corpses."));
            return;
        }

        if (!args[0].equalsIgnoreCase("force-start")) {
            player.sendMessage(ChatUtil.prefixed("&cUsage: /murder debug <start|identity|identityreset|corpse|corpseclear> [arena]"));
            return;
        }

        Optional<Arena> targetArena = resolveTargetArena(player, args);
        if (targetArena.isEmpty()) {
            return;
        }

        Arena arena = targetArena.get();
        if (arena.getArenaState() instanceof ActiveArenaState) {
            player.sendMessage(ChatUtil.prefixed("&cThe game is already running."));
            return;
        }
        if (arena.getArenaState() instanceof StartingArenaState) {
            player.sendMessage(ChatUtil.prefixed("&cThe game is already starting."));
            return;
        }
        if (arena.getArenaState() instanceof InitArenaState) {
            player.sendMessage(ChatUtil.prefixed("&cThis arena isn't ready yet."));
            return;
        }
        if (!(arena.getArenaState() instanceof WaitingArenaState)) {
            player.sendMessage(ChatUtil.prefixed("&cThis arena can't be started right now."));
            return;
        }

        arena.sendArenaMessage("&eDebug: game force-started.");
        arena.setArenaSate(new StartingArenaState(gameManager, arena, true));
    }

    @Override
    public String getName() {
        return "debug";
    }

    private Optional<Arena> resolveTargetArena(Player player, String[] args) {
        if (args.length >= 2) {
            String arenaName = joinArgs(args, 1);
            Optional<Arena> arena = gameManager.getArenaManager().findArena(arenaName);
            if (arena.isEmpty()) {
                player.sendMessage(ChatUtil.prefixed("&cNo arena by that name exists."));
            }
            return arena;
        }

        Optional<Arena> currentArena = gameManager.getArenaManager().getCurrentArena(player);
        if (currentArena.isEmpty()) {
            player.sendMessage(ChatUtil.prefixed("&cYou are not in an arena."));
        }
        return currentArena;
    }

    private String joinArgs(String[] args, int startIndex) {
        StringBuilder name = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            name.append(args[i]);
            if (i < args.length - 1) {
                name.append("_");
            }
        }
        return name.toString();
    }
}
