package fr.zeyx.murder.command.murder;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.command.CommandArgs;
import fr.zeyx.murder.command.CommandResult;
import fr.zeyx.murder.command.PlayerSubCommand;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.arena.vote.MapVoteSession;
import fr.zeyx.murder.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
public class VoteSubCommand implements PlayerSubCommand {

    private final GameManager gameManager;

    public VoteSubCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public CommandResult execute(Player player, String[] args) {
        if (gameManager.getArenaManager().getCurrentArena(player).isEmpty()) {
            player.sendMessage(ChatUtil.prefixed("&7You are not in an arena."));
            return CommandResult.FAILURE;
        }

        MapVoteSession voteSession = gameManager.getArenaManager().getVoteSession();
        if (voteSession == null) {
            player.sendMessage(ChatUtil.prefixed("&7Voting is not available right now."));
            return CommandResult.FAILURE;
        }

        if (args.length == 0) {
            voteSession.sendVotePrompt(player);
            return CommandResult.SUCCESS;
        }

        if (voteSession.isLocked()) {
            player.sendMessage(ChatUtil.prefixed("&7Voting is locked."));
            return CommandResult.FAILURE;
        }

        String arenaName = CommandArgs.joinArgs(args, 0);
        Arena arena = voteSession.findCandidate(arenaName);
        if (arena == null) {
            player.sendMessage(ChatUtil.prefixed("&7That map is not in the current vote."));
            return CommandResult.FAILURE;
        }

        Arena previous = voteSession.getVote(player.getUniqueId());
        if (arena.equals(previous)) {
            player.sendMessage(ChatUtil.prefixed("&7You already voted for &a" + arena.getDisplayName() + "&7."));
            return CommandResult.FAILURE;
        }

        voteSession.setVote(player.getUniqueId(), arena);
        player.sendMessage(ChatUtil.color("&7You voted for &a" + arena.getDisplayName()));
        return CommandResult.SUCCESS;
    }

    @Override
    public String getName() {
        return "vote";
    }

    @Override
    public String getUsage() {
        return "/murder vote [arena]";
    }

    @Override
    public String getDescription() {
        return "Vote for the next map.";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args == null || args.length != 1) {
            return List.of();
        }
        MapVoteSession voteSession = gameManager.getArenaManager().getVoteSession();
        List<String> candidates = voteSession == null
                ? gameManager.getArenaManager().getArenas().stream().map(Arena::getName).toList()
                : voteSession.getCandidates().stream().map(Arena::getName).toList();
        return CommandArgs.filterByPrefix(candidates, args[0]);
    }
}
