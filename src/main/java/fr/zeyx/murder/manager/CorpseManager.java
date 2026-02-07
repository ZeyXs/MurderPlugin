package fr.zeyx.murder.manager;

import fr.zeyx.murder.MurderPlugin;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.trait.Equipment;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.lang.reflect.Method;

public class CorpseManager {

    private static final String CORPSE_HIDDEN_TEAM = "murder_corpse_hide";

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
        spawnCorpse(source, source.getLocation(), source.getInventory().getChestplate());
    }

    public void spawnCorpse(Player source, Location location) {
        ItemStack chestplate = source == null ? null : source.getInventory().getChestplate();
        spawnCorpse(source, location, chestplate);
    }

    public void spawnCorpse(Player source, Location location, ItemStack chestplate) {
        if (source == null || location == null) {
            return;
        }
        Location spawnLocation = location.clone().add(-1.0, 0.0, 0.0);
        spawnLocation.setYaw(180.0f);
        spawnLocation.setPitch(0.0f);
        ItemStack corpseChestplate = sanitizeChestplate(chestplate);

        World world = spawnLocation.getWorld();
        if (world == null || registry == null) {
            return;
        }

        String npcName = buildNpcName(source);
        NPC npc = registry.createNPC(EntityType.PLAYER, npcName);
        npc.setAlwaysUseNameHologram(false);
        npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, false);
        npc.data().set(NPC.Metadata.NAMEPLATE_VISIBLE, false);
        npc.data().setPersistent(NPC.Metadata.ALWAYS_USE_NAME_HOLOGRAM, false);
        npc.data().set(NPC.Metadata.ALWAYS_USE_NAME_HOLOGRAM, false);
        npc.data().set(NPC.Metadata.REMOVE_FROM_PLAYERLIST, true);
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
        applyCorpseSkin(npc, source);

        boolean spawned = npc.spawn(spawnLocation, this::configureCorpseEntity);
        if (!spawned) {
            plugin.getLogger().warning("Failed to spawn corpse NPC.");
            registry.deregister(npc);
            return;
        }

        applyCorpseAppearance(npc, source, corpseChestplate);
        addCorpseHiddenEntry(npcName);
        corpses.add(npc);
        applyCorpsePose(npc);
    }

    public int clearCorpses() {
        synchronized (corpses) {
            int count = corpses.size();
            for (NPC npc : corpses) {
                try {
                    removeCorpseHiddenEntry(npc.getName());
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
        entity.setCustomNameVisible(false);
        entity.setPose(Pose.SLEEPING, true);
    }

    private void applyCorpseAppearance(NPC npc, Player source, ItemStack chestplate) {
        Entity entity = npc.getEntity();
        if (entity == null || source == null) {
            return;
        }

        if (chestplate == null) {
            return;
        }

        ItemStack copiedChestplate = chestplate.clone();
        npc.getOrAddTrait(Equipment.class).set(Equipment.EquipmentSlot.CHESTPLATE, copiedChestplate);
        if (entity instanceof LivingEntity livingEntity && livingEntity.getEquipment() != null) {
            livingEntity.getEquipment().setChestplate(copiedChestplate.clone());
        }
    }

    private String buildNpcName(Player source) {
        if (source == null || source.getUniqueId() == null) {
            return "corpse_npc";
        }
        String compactId = source.getUniqueId().toString().replace("-", "");
        int tailLength = Math.min(9, compactId.length());
        return "corpse_" + compactId.substring(0, tailLength);
    }

    private ItemStack sanitizeChestplate(ItemStack chestplate) {
        if (chestplate == null || chestplate.getType() != Material.LEATHER_CHESTPLATE) {
            return null;
        }
        return chestplate.clone();
    }

    private void addCorpseHiddenEntry(String entry) {
        if (entry == null || entry.isBlank() || Bukkit.getScoreboardManager() == null) {
            return;
        }
        Team team = getOrCreateCorpseHiddenTeam();
        if (team != null && !team.hasEntry(entry)) {
            team.addEntry(entry);
        }
    }

    private void removeCorpseHiddenEntry(String entry) {
        if (entry == null || entry.isBlank() || Bukkit.getScoreboardManager() == null) {
            return;
        }
        Team team = getOrCreateCorpseHiddenTeam();
        if (team != null && team.hasEntry(entry)) {
            team.removeEntry(entry);
        }
    }

    private Team getOrCreateCorpseHiddenTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(CORPSE_HIDDEN_TEAM);
        if (team == null) {
            team = scoreboard.registerNewTeam(CORPSE_HIDDEN_TEAM);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        return team;
    }

    @SuppressWarnings("unchecked")
    private void applyCorpseSkin(NPC npc, Player source) {
        if (npc == null || source == null) {
            return;
        }
        String fallbackSkinName = source.getPlayerProfile() != null && source.getPlayerProfile().getName() != null
                ? source.getPlayerProfile().getName()
                : source.getName();
        try {
            Class<?> rawSkinTraitClass = Class.forName("net.citizensnpcs.trait.SkinTrait");
            if (!Trait.class.isAssignableFrom(rawSkinTraitClass)) {
                return;
            }
            Class<? extends Trait> skinTraitClass = (Class<? extends Trait>) rawSkinTraitClass;
            Trait skinTrait = npc.getOrAddTrait(skinTraitClass);

            invokeIfPresent(skinTrait, "setFetchDefaultSkin", new Class<?>[]{boolean.class}, new Object[]{false});
            invokeIfPresent(skinTrait, "setShouldUpdateSkins", new Class<?>[]{boolean.class}, new Object[]{false});
            if (invokeIfPresent(skinTrait, "setSkinPersistent", new Class<?>[]{Player.class}, new Object[]{source})) {
                return;
            }
            if (invokeIfPresent(skinTrait, "setSkinName", new Class<?>[]{String.class, boolean.class}, new Object[]{fallbackSkinName, true})) {
                return;
            }
            invokeIfPresent(skinTrait, "setSkinName", new Class<?>[]{String.class}, new Object[]{fallbackSkinName});
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Failed to apply corpse skin: " + throwable.getMessage());
        }
    }

    private boolean invokeIfPresent(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) {
        if (target == null) {
            return false;
        }
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            method.invoke(target, args);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Failed to invoke " + methodName + " on corpse skin trait: " + throwable.getMessage());
            return false;
        }
    }
}
