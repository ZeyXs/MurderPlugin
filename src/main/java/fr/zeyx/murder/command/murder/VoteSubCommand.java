package fr.zeyx.murder.command.murder;

import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.command.PlayerSubCommand;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.manager.MapVoteSession;
import fr.zeyx.murder.util.ChatUtil;
import org.bukkit.entity.Player;

public class VoteSubCommand implements PlayerSubCommand {

    private final GameManager gameManager;

    public VoteSubCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public void execute(Player player, String[] args) {
        if (gameManager.getArenaManager().getCurrentArena(player).isEmpty()) {
            player.sendMessage(ChatUtil.prefixed("&7You are not in an arena."));
            return;
        }

        MapVoteSession voteSession = gameManager.getArenaManager().getVoteSession();
        if (voteSession == null) {
            player.sendMessage(ChatUtil.prefixed("&7Voting is not available right now."));
            return;
        }

        if (args.length == 0) {
            voteSession.sendVotePrompt(player);
            return;
        }

        if (voteSession.isLocked()) {
            player.sendMessage(ChatUtil.prefixed("&7Voting is locked."));
            return;
        }

        String arenaName = String.join("_", args);
        Arena arena = voteSession.findCandidate(arenaName);
        if (arena == null) {
            player.sendMessage(ChatUtil.prefixed("&7That map is not in the current vote."));
            return;
        }

        Arena previous = voteSession.getVote(player.getUniqueId());
        if (arena.equals(previous)) {
            player.sendMessage(ChatUtil.prefixed("&7You already voted for &a" + arena.getDisplayName() + "&7."));
            return;
        }

        voteSession.setVote(player.getUniqueId(), arena);
        player.sendMessage(ChatUtil.color("&7You voted for &a" + arena.getDisplayName()));
    }

    @Override
    public String getName() {
        return "vote";
    }
}
