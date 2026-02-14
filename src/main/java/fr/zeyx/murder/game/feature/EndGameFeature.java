package fr.zeyx.murder.game.feature;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.arena.Arena;
import fr.zeyx.murder.game.Role;
import fr.zeyx.murder.game.service.NametagService;
import fr.zeyx.murder.game.service.PlayerCollisionService;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class EndGameFeature {

    private final Arena arena;
    private final GameManager gameManager;

    public EndGameFeature(Arena arena, GameManager gameManager) {
        this.arena = arena;
        this.gameManager = gameManager;
    }

    public void applyEndGameState(List<UUID> alivePlayers,
                                  Map<UUID, Role> roles,
                                  UUID murdererId,
                                  UUID murdererKillerId,
                                  boolean murdererWon,
                                  SpectatorFeature spectatorFeature) {
        UUID winnerId = resolveWinnerId(murdererWon, murdererId, murdererKillerId, alivePlayers);
        sendPersonalEndGameTitles(murdererWon, winnerId, murdererId, alivePlayers, roles);
        startWinnerFireworks(winnerId);
        Set<UUID> aliveAtEnd = new HashSet<>(alivePlayers);

        for (UUID playerId : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            player.setCustomChatCompletions(List.of());
            if (aliveAtEnd.contains(playerId)) {
                spectatorFeature.prepareForEndGame(player);
                clearInventoryKeepChestplate(player);
                player.setGameMode(GameMode.ADVENTURE);
                player.setAllowFlight(true);
            }
            NametagService.show(player);
            gameManager.getSecretIdentityManager().resetIdentity(player);
            PlayerCollisionService.disableForArena(player);
        }
    }

    private void clearInventoryKeepChestplate(Player player) {
        if (player == null) {
            return;
        }
        ItemStack chestplate = player.getInventory().getChestplate();
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
    }

    private UUID resolveWinnerId(boolean murdererWon, UUID murdererId, UUID murdererKillerId, List<UUID> alivePlayers) {
        if (murdererWon) {
            return murdererId;
        }
        if (murdererKillerId != null) {
            return murdererKillerId;
        }
        for (UUID playerId : alivePlayers) {
            if (!playerId.equals(murdererId)) {
                return playerId;
            }
        }
        return null;
    }

    private void sendPersonalEndGameTitles(boolean murdererWon,
                                           UUID winnerId,
                                           UUID murdererId,
                                           List<UUID> alivePlayers,
                                           Map<UUID, Role> roles) {
        for (UUID playerId : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            if (winnerId != null && winnerId.equals(playerId)) {
                showEndGameTitle(player, "&aYou won!");
                continue;
            }

            if (murdererWon) {
                if (!playerId.equals(murdererId)) {
                    showEndGameTitle(player, "&cYou lose!");
                }
                continue;
            }

            Role role = roles.get(playerId);
            boolean aliveInnocent = alivePlayers.contains(playerId)
                    && role != null
                    && role != Role.MURDERER;
            if (aliveInnocent) {
                showEndGameTitle(player, "&aYou survived!");
            }
        }
    }

    private void showEndGameTitle(Player player, String title) {
        if (player == null || title == null || title.isBlank()) {
            return;
        }
        player.showTitle(Title.title(
                TextUtil.component(title),
                Component.empty(),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(500))
        ));
    }

    private void startWinnerFireworks(UUID winnerId) {
        if (winnerId == null) {
            return;
        }
        new BukkitRunnable() {
            private int elapsedTicks = 0;

            @Override
            public void run() {
                Player winner = Bukkit.getPlayer(winnerId);
                if (winner == null || !winner.isOnline() || !arena.isPlaying(winner)) {
                    cancel();
                    return;
                }
                spawnWinnerFirework(winner);
                elapsedTicks += 10;
                if (elapsedTicks >= 20 * 5) {
                    cancel();
                }
            }
        }.runTaskTimer(MurderPlugin.getInstance(), 0L, 10L);
    }

    private void spawnWinnerFirework(Player winner) {
        if (winner == null || winner.getWorld() == null) {
            return;
        }
        Firework firework = winner.getWorld().spawn(winner.getLocation().clone().add(0.0D, 1.0D, 0.0D), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        Color primary = Color.fromRGB(
                ThreadLocalRandom.current().nextInt(64, 256),
                ThreadLocalRandom.current().nextInt(64, 256),
                ThreadLocalRandom.current().nextInt(64, 256)
        );
        Color fade = Color.fromRGB(
                ThreadLocalRandom.current().nextInt(64, 256),
                ThreadLocalRandom.current().nextInt(64, 256),
                ThreadLocalRandom.current().nextInt(64, 256)
        );
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL)
                .withColor(primary)
                .withFade(fade)
                .trail(true)
                .flicker(true)
                .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
        Bukkit.getScheduler().runTaskLater(MurderPlugin.getInstance(), firework::detonate, 20L);
    }
}
