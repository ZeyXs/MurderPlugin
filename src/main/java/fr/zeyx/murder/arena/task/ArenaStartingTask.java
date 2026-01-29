package fr.zeyx.murder.arena.task;

import fr.zeyx.murder.arena.Arena;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class ArenaStartingTask extends BukkitRunnable {

    private final Arena arena;
    private final Runnable onStart;
    private int timeUntilStart;

    public ArenaStartingTask(Arena arena, Runnable onStart, int timeUntilStart) {
        this.arena = arena;
        this.onStart = onStart;
        this.timeUntilStart = timeUntilStart;
    }

    @Override
    public void run() {
        if (timeUntilStart <= 0) {
            cancel();
            onStart.run();
            return;
        }

        if (timeUntilStart == 10 || timeUntilStart <= 5) {
            arena.sendArenaMessage("&8[&a!&8] &7Game starting in " + ChatColor.of("#f58311") + timeUntilStart + (timeUntilStart == 1 ? "&7 second!" : "&7 seconds!"));
            for (UUID playerId : arena.getActivePlayers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) continue;
                switch (timeUntilStart) {
                    case 10 -> {
                        player.sendTitle(ChatColor.of("#fccc72") + "⑩", "", 0, 25, 0);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, .8f);
                    }
                    case 5 -> {
                        player.sendTitle(ChatColor.of("#fcc04e") + "⑤", "", 0, 25, 0);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, 1f);
                    }
                    case 4 -> {
                        player.sendTitle(ChatColor.of("#faba41") + "④", "", 0, 25, 0);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, 1.2f);

                    }
                    case 3 -> {
                        player.sendTitle(ChatColor.of("#f7b331") + "③", "", 0, 25, 0);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, 1.4f);
                    }
                    case 2 -> {
                        player.sendTitle(ChatColor.of("#faae1e") + "②", "", 0, 25, 0);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, 1.6f);
                    }
                    case 1 -> {
                        player.sendTitle(ChatColor.of("#ffa905") + "①", "", 0, 25, 0);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50, 1.8f);
                    }
                }
            }
        }
        timeUntilStart--;
    }

}
