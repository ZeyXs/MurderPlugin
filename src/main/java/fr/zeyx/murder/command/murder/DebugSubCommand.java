package fr.zeyx.murder.command.murder;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.arena.state.ActiveArenaState;
import fr.zeyx.murder.arena.state.InitArenaState;
import fr.zeyx.murder.arena.state.StartingArenaState;
import fr.zeyx.murder.arena.state.WaitingArenaState;
import fr.zeyx.murder.command.CommandArgs;
import fr.zeyx.murder.command.CommandResult;
import fr.zeyx.murder.command.PlayerSubCommand;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class DebugSubCommand implements PlayerSubCommand {

    private final GameManager gameManager;

    public DebugSubCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public CommandResult execute(Player player, String[] args) {
        if (args.length == 0) {
            return CommandResult.INVALID_USAGE;
        }

        if (args[0].equalsIgnoreCase("identity")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
                boolean reset = gameManager.getSecretIdentityManager().resetIdentity(player);
                if (!reset) {
                    player.sendMessage(ChatUtil.prefixed("&cYou don't have a secret identity to reset."));
                    return CommandResult.FAILURE;
                }
                player.sendMessage(ChatUtil.prefixed("&aYour identity has been reset."));
                return CommandResult.SUCCESS;
            }
            if (gameManager.getConfigurationManager().getSecretIdentityNames().isEmpty()) {
                player.sendMessage(ChatUtil.prefixed("&cNo secret identities configured."));
                return CommandResult.FAILURE;
            }
            String username = gameManager.getSecretIdentityManager().applyRandomIdentity(player);
            if (username == null) {
                player.sendMessage(ChatUtil.prefixed("&cNo alternative identity available."));
                return CommandResult.FAILURE;
            }
            player.sendMessage(ChatUtil.prefixed("&7Your identity is now &a" + username));
            return CommandResult.SUCCESS;
        }

        if (args[0].equalsIgnoreCase("identityreset") || args[0].equalsIgnoreCase("resetidentity")) {
            boolean reset = gameManager.getSecretIdentityManager().resetIdentity(player);
            if (!reset) {
                player.sendMessage(ChatUtil.prefixed("&cYou don't have a secret identity to reset."));
                return CommandResult.FAILURE;
            }
            player.sendMessage(ChatUtil.prefixed("&aYour identity has been reset."));
            return CommandResult.SUCCESS;
        }

        if (args[0].equalsIgnoreCase("corpse")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
                int cleared = gameManager.getCorpseManager().clearCorpses();
                player.sendMessage(ChatUtil.prefixed("&eDebug: cleared &6" + cleared + "&e corpses."));
                return CommandResult.SUCCESS;
            }
            gameManager.getCorpseManager().spawnCorpse(player);
            player.sendMessage(ChatUtil.prefixed("&eDebug: corpse spawned."));
            return CommandResult.SUCCESS;
        }

        if (args[0].equalsIgnoreCase("corpseclear") || args[0].equalsIgnoreCase("clearcorpse")) {
            int cleared = gameManager.getCorpseManager().clearCorpses();
            player.sendMessage(ChatUtil.prefixed("&eDebug: cleared &6" + cleared + "&e corpses."));
            return CommandResult.SUCCESS;
        }

        if (!args[0].equalsIgnoreCase("force-start")) {
            return CommandResult.INVALID_USAGE;
        }

        Optional<Arena> targetArena = resolveTargetArena(player, args);
        if (targetArena.isEmpty()) {
            return CommandResult.FAILURE;
        }

        Arena arena = targetArena.get();
        if (arena.getArenaState() instanceof ActiveArenaState) {
            player.sendMessage(ChatUtil.prefixed("&cThe game is already running."));
            return CommandResult.FAILURE;
        }
        if (arena.getArenaState() instanceof StartingArenaState) {
            player.sendMessage(ChatUtil.prefixed("&cThe game is already starting."));
            return CommandResult.FAILURE;
        }
        if (arena.getArenaState() instanceof InitArenaState) {
            player.sendMessage(ChatUtil.prefixed("&cThis arena isn't ready yet."));
            return CommandResult.FAILURE;
        }
        if (!(arena.getArenaState() instanceof WaitingArenaState)) {
            player.sendMessage(ChatUtil.prefixed("&cThis arena can't be started right now."));
            return CommandResult.FAILURE;
        }

        arena.sendArenaMessage("&eDebug: game force-started.");
        arena.setArenaSate(new StartingArenaState(gameManager, arena, true));
        return CommandResult.SUCCESS;
    }

    @Override
    public String getName() {
        return "debug";
    }

    @Override
    public String getUsage() {
        return "/murder debug <force-start|identity|identityreset|corpse|corpseclear> [arena]";
    }

    @Override
    public String getDescription() {
        return "Admin debug commands.";
    }

    @Override
    public String getPermission() {
        return "murder.admin";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args == null) {
            return List.of();
        }
        if (args.length == 1) {
            return CommandArgs.filterByPrefix(
                    List.of("force-start", "identity", "identityreset", "resetidentity", "corpse", "corpseclear", "clearcorpse"),
                    args[0]
            );
        }
        if (args.length == 2) {
            if ("identity".equalsIgnoreCase(args[0])) {
                return CommandArgs.filterByPrefix(List.of("reset"), args[1]);
            }
            if ("corpse".equalsIgnoreCase(args[0])) {
                return CommandArgs.filterByPrefix(List.of("clear"), args[1]);
            }
            if ("force-start".equalsIgnoreCase(args[0])) {
                return CommandArgs.filterByPrefix(
                    gameManager.getArenaManager().getArenas().stream().map(Arena::getName).toList(),
                    args[1]
                );
            }
        }
        return List.of();
    }

    private Optional<Arena> resolveTargetArena(Player player, String[] args) {
        if (args.length >= 2) {
            String arenaName = CommandArgs.joinArgs(args, 1);
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
}
