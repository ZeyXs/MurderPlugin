package fr.zeyx.murder.command.murder;


import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.command.SubCommand;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.manager.MapVoteSession;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MurderTabCompletion implements TabCompleter {

    private final GameManager gameManager;
    private final List<String> subCommandNames;

    public MurderTabCompletion(GameManager gameManager, MurderBaseCommand baseCommand) {
        this.gameManager = gameManager;
        this.subCommandNames = baseCommand.getSubCommandList()
                .stream()
                .map(SubCommand::getName)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {

        if (args == null || args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterByPrefix(subCommandNames, args[0]);
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "arena" -> {
                    return filterByPrefix(Arrays.asList("list", "create", "edit", "remove"), args[1]);
                }
                case "debug" -> {
                    return filterByPrefix(List.of("start", "identity", "identityreset", "resetidentity", "corpse", "corpseclear", "clearcorpse"), args[1]);
                }
                case "lobby" -> {
                    return filterByPrefix(List.of("set"), args[1]);
                }
                case "vote" -> {
                    MapVoteSession voteSession = gameManager.getArenaManager().getVoteSession();
                    List<String> candidates = voteSession == null ? getArenaNames() :
                            voteSession.getCandidates().stream().map(Arena::getName).collect(Collectors.toList());
                    return filterByPrefix(candidates, args[1]);
                }
                default -> {
                    return Collections.emptyList();
                }
            }
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("start")) {
                return filterByPrefix(getArenaNames(), args[2]);
            }
            if (args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("identity")) {
                return filterByPrefix(List.of("reset"), args[2]);
            }
            if (args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("corpse")) {
                return filterByPrefix(List.of("clear"), args[2]);
            }
            if (!args[0].equalsIgnoreCase("arena")) return Collections.emptyList();
            if (args[1].equalsIgnoreCase("edit") || args[1].equalsIgnoreCase("remove")) {
                return filterByPrefix(getArenaNames(), args[2]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> getArenaNames() {
        return gameManager.getArenaManager().getArenas().stream()
                .map(Arena::getName)
                .collect(Collectors.toList());
    }

    private List<String> filterByPrefix(List<String> candidates, String prefix) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        if (prefix == null || prefix.isEmpty()) {
            return candidates;
        }
        String lowerPrefix = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate != null && candidate.toLowerCase().startsWith(lowerPrefix)) {
                result.add(candidate);
            }
        }
        return result;
    }

}
