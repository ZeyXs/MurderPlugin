package fr.zeyx.murder.manager;

import fr.zeyx.murder.MurderPlugin;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class CorpseManager {

    private final MurderPlugin plugin;
    private final NPCRegistry registry;
    private final Set<NPC> corpses;

    public CorpseManager(MurderPlugin plugin) {
        this.plugin = plugin;
        if (!CitizensAPI.hasImplementation()) {
            this.registry = null;
            this.corpses = Collections.synchronizedSet(new HashSet<>());
            plugin.getLogger().warning("Citizens is not available. Corpse NPCs are disabled.");
            return;
        }
        this.registry = CitizensAPI.getTemporaryNPCRegistry();
        this.corpses = Collections.synchronizedSet(new HashSet<>());
    }

    public void spawnCorpse(Player source) {
        if (source == null) {
            return;
        }
        spawnCorpse(source, source.getLocation());
    }

    public void spawnCorpse(Player source, Location location) {
        if (source == null || location == null) {
            return;
        }
        Location spawnLocation = location.clone().add(-1.0, 0.0, 0.0);
        spawnLocation.setYaw(180.0f);
        spawnLocation.setPitch(0.0f);

        World world = spawnLocation.getWorld();
        if (world == null || registry == null) {
            return;
        }

        String npcName = buildNpcName(source);
        NPC npc = registry.createNPC(EntityType.PLAYER, npcName);
        npc.data().set(NPC.Metadata.NAMEPLATE_VISIBLE, false);
        npc.data().set(NPC.Metadata.REMOVE_FROM_TABLIST, true);
        npc.data().set(NPC.Metadata.SHOULD_SAVE, false);
        npc.data().set(NPC.Metadata.SILENT, true);
        npc.data().set(NPC.Metadata.SWIM, true);
        npc.data().set(NPC.Metadata.USING_HELD_ITEM, false);
        npc.data().set(NPC.Metadata.USING_OFFHAND_ITEM, false);
        npc.data().set(NPC.Metadata.RESET_YAW_ON_SPAWN, false);
        npc.data().set(NPC.Metadata.RESET_PITCH_ON_TICK, false);
        npc.setProtected(true);
        npc.setUseMinecraftAI(false);

        boolean spawned = npc.spawn(spawnLocation, this::configureCorpseEntity);
        if (!spawned) {
            plugin.getLogger().warning("Failed to spawn corpse NPC.");
            registry.deregister(npc);
            return;
        }

        corpses.add(npc);
        applyCorpsePose(npc);
    }

    public int clearCorpses() {
        synchronized (corpses) {
            int count = corpses.size();
            for (NPC npc : corpses) {
                try {
                    npc.destroy();
                } catch (Exception ignored) {
                    npc.despawn();
                }
            }
            corpses.clear();
            return count;
        }
    }

    public int getCorpseCount() {
        return corpses.size();
    }

    private void applyCorpsePose(NPC npc) {
        Entity entity = npc.getEntity();
        if (entity == null) {
            return;
        }
        configureCorpseEntity(entity);
    }

    private void configureCorpseEntity(Entity entity) {
        entity.setSilent(true);
        entity.setInvulnerable(true);
        entity.setGravity(false);
        entity.setPose(Pose.SLEEPING, true);
    }

    private String buildNpcName(Player source) {
        String base = source.getName();
        if (base == null || base.isBlank()) {
            return "Corpse";
        }
        String name = "Corpse" + base;
        if (name.length() > 16) {
            return name.substring(0, 16);
        }
        return name;
    }
}
