package fr.zeyx.murder.game.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class NametagService {

    private static final String HIDDEN_NAMETAG_TEAM = "murder_hide";

    private NametagService() {
    }

    public static void hide(Player player) {
        if (player == null || Bukkit.getScoreboardManager() == null) {
            return;
        }
        Team team = getOrCreateHiddenTeam();
        for (String entry : collectEntries(player)) {
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
        }
    }

    public static void show(Player player) {
        if (player == null || Bukkit.getScoreboardManager() == null) {
            return;
        }
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(HIDDEN_NAMETAG_TEAM);
        if (team == null) {
            return;
        }
        for (String entry : collectEntries(player)) {
            if (team.hasEntry(entry)) {
                team.removeEntry(entry);
            }
        }
    }

    private static Team getOrCreateHiddenTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(HIDDEN_NAMETAG_TEAM);
        if (team == null) {
            team = scoreboard.registerNewTeam(HIDDEN_NAMETAG_TEAM);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        return team;
    }

    private static List<String> collectEntries(Player player) {
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        if (player.getName() != null && !player.getName().isBlank()) {
            entries.add(player.getName());
        }
        if (player.getPlayerProfile() != null) {
            String profileName = player.getPlayerProfile().getName();
            if (profileName != null && !profileName.isBlank()) {
                entries.add(profileName);
            }
        }
        return new ArrayList<>(entries);
    }
}
