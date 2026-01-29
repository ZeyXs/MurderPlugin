package fr.zeyx.murder.arena;

import fr.zeyx.murder.MurderPlugin;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

public abstract class ArenaState implements Listener {

    public void onEnable() {
        MurderPlugin.getInstance().getServer().getPluginManager().registerEvents(this, MurderPlugin.getInstance());
    }

    public void onDisable() {
        HandlerList.unregisterAll(this);
    }

}
