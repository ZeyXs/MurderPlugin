package fr.zeyx.murder.game.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class PlayerCollisionService {

    private static final String NO_COLLISION_TEAM = "murder_nocollide";

    private PlayerCollisionService() {
    }

    public static void disableForArena(Player player) {
        if (player == null || Bukkit.getScoreboardManager() == null) {
            return;
        }
        // Keep projectile/entity hitboxes fully active; collision suppression is handled via team rule.
        player.setCollidable(true);
        Team team = getOrCreateNoCollisionTeam();
        for (String entry : collectEntries(player)) {
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
        }
    }

    public static void restore(Player player) {
        if (player == null || Bukkit.getScoreboardManager() == null) {
            return;
        }
        player.setCollidable(true);
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(NO_COLLISION_TEAM);
        if (team == null) {
            return;
        }
        for (String entry : collectEntries(player)) {
            if (team.hasEntry(entry)) {
                team.removeEntry(entry);
            }
        }
    }

    public static void clearAll() {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(NO_COLLISION_TEAM);
        if (team != null) {
            team.unregister();
        }
    }

    private static Team getOrCreateNoCollisionTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(NO_COLLISION_TEAM);
        if (team == null) {
            team = scoreboard.registerNewTeam(NO_COLLISION_TEAM);
        }
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
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
