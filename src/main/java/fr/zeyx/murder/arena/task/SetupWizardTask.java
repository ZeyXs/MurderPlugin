package fr.zeyx.murder.arena.task;

import fr.zeyx.murder.arena.setup.SetupWizardManager;
import fr.zeyx.murder.util.TextUtil;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Set;
import java.util.UUID;

public class SetupWizardTask extends BukkitRunnable {

    private SetupWizardManager wizardManager;

    public SetupWizardTask(SetupWizardManager wizardManager) {
        this.wizardManager = wizardManager;
    }

    @Override
    public void run() {
        Set<UUID> uuids = wizardManager.getPlayersInWizard().keySet();

        if (uuids.size() == 0) {
            cancel();
            return;
        }

        for (UUID playerId : uuids) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) return;
            player.sendActionBar(TextUtil.component(ChatColor.of("#fc9003") + "⚠ ᴀʀᴇɴᴀ sᴇᴛᴜᴘ ᴍᴏᴅᴇ ⚠"));
        }
    }

}
