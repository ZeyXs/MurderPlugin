package fr.zeyx.murder.manager;

import fr.zeyx.murder.MurderPlugin;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.trait.Equipment;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class CorpseManager {

    private static final String CORPSE_HIDDEN_TEAM = "murder_corpse_hide";

    private final MurderPlugin plugin;
    private final NPCRegistry registry;
    private final Set<NPC> corpses;
    private final Map<UUID, CorpseIdentity> corpseIdentities;
    private final Map<UUID, BukkitTask> corpseStabilizationTasks;

    public CorpseManager(MurderPlugin plugin) {
        this.plugin = plugin;
        if (!CitizensAPI.hasImplementation()) {
            this.registry = null;
            this.corpses = Collections.synchronizedSet(new HashSet<>());
            this.corpseIdentities = new ConcurrentHashMap<>();
            this.corpseStabilizationTasks = new ConcurrentHashMap<>();
            plugin.getLogger().warning("Citizens is not available. Corpse NPCs are disabled.");
            return;
        }
        this.registry = CitizensAPI.getTemporaryNPCRegistry();
        this.corpses = Collections.synchronizedSet(new HashSet<>());
        this.corpseIdentities = new ConcurrentHashMap<>();
        this.corpseStabilizationTasks = new ConcurrentHashMap<>();
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
        spawnCorpse(source, location, chestplate, null, null, source == null ? null : source.getPlayerProfile());
    }

    public void spawnCorpse(Player source, Location location, ItemStack chestplate, String identityName, ChatColor identityColor) {
        spawnCorpse(source, location, chestplate, identityName, identityColor, source == null ? null : source.getPlayerProfile());
    }

    public void spawnCorpse(Player source,
                            Location location,
                            ItemStack chestplate,
                            String identityName,
                            ChatColor identityColor,
                            PlayerProfile identityProfile) {
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
        corpseIdentities.put(
                npc.getUniqueId(),
                new CorpseIdentity(
                        npc.getUniqueId(),
                        source.getUniqueId(),
                        identityName == null || identityName.isBlank() ? source.getName() : identityName,
                        identityColor == null ? ChatColor.WHITE : identityColor,
                        cloneProfile(identityProfile)
                )
        );
        applyCorpsePose(npc);
    }

    public int clearCorpses() {
        synchronized (corpses) {
            int count = corpses.size();
            for (NPC npc : corpses) {
                try {
                    removeCorpseHiddenEntry(npc.getName());
                    corpseIdentities.remove(npc.getUniqueId());
                    stopCorpseStabilization(npc.getUniqueId());
                    npc.destroy();
                } catch (Exception ignored) {
                    npc.despawn();
                }
            }
            corpses.clear();
            corpseIdentities.clear();
            for (BukkitTask task : corpseStabilizationTasks.values()) {
                task.cancel();
            }
            corpseStabilizationTasks.clear();
            return count;
        }
    }

    public int getCorpseCount() {
        return corpses.size();
    }

    public CorpseIdentity findNearestCorpseIdentity(Location location, double radius) {
        if (location == null || location.getWorld() == null || radius <= 0.0D) {
            return null;
        }
        double radiusSquared = radius * radius;
        CorpseIdentity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        synchronized (corpses) {
            for (NPC corpse : corpses) {
                if (corpse == null || !corpse.isSpawned()) {
                    continue;
                }
                Entity entity = corpse.getEntity();
                if (entity == null || entity.getWorld() == null || !entity.getWorld().equals(location.getWorld())) {
                    continue;
                }
                double distanceSquared = entity.getLocation().distanceSquared(location);
                if (distanceSquared > radiusSquared || distanceSquared >= nearestDistance) {
                    continue;
                }
                CorpseIdentity identity = corpseIdentities.get(corpse.getUniqueId());
                if (identity == null) {
                    continue;
                }
                nearest = identity;
                nearestDistance = distanceSquared;
            }
        }
        return nearest;
    }

    public boolean setCorpseIdentity(UUID corpseId, String identityName, ChatColor identityColor) {
        return setCorpseIdentity(corpseId, identityName, identityColor, null);
    }

    public boolean setCorpseIdentity(UUID corpseId, String identityName, ChatColor identityColor, PlayerProfile identityProfile) {
        if (corpseId == null || identityName == null || identityName.isBlank()) {
            return false;
        }
        CorpseIdentity current = corpseIdentities.get(corpseId);
        if (current == null) {
            return false;
        }
        PlayerProfile updatedIdentityProfile = cloneProfile(identityProfile);
        if (updatedIdentityProfile == null) {
            updatedIdentityProfile = current.getIdentityProfile();
        }
        corpseIdentities.put(
                corpseId,
                new CorpseIdentity(
                        corpseId,
                        current.getSourcePlayerId(),
                        identityName,
                        identityColor == null ? ChatColor.WHITE : identityColor,
                        updatedIdentityProfile
                )
        );
        NPC corpseNpc = findCorpseById(corpseId);
        if (corpseNpc != null) {
            Location previousLocation = corpseNpc.getEntity() == null ? null : corpseNpc.getEntity().getLocation().clone();
            applyCorpseIdentitySkin(corpseNpc, identityName);
            applyCorpseIdentityChestplate(corpseNpc, identityColor == null ? ChatColor.WHITE : identityColor);
            if (previousLocation != null) {
                startCorpseStabilization(corpseNpc, previousLocation);
            }
        }
        return true;
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

    private NPC findCorpseById(UUID corpseId) {
        if (corpseId == null) {
            return null;
        }
        synchronized (corpses) {
            for (NPC corpse : corpses) {
                if (corpse != null && corpseId.equals(corpse.getUniqueId())) {
                    return corpse;
                }
            }
        }
        return null;
    }

    private PlayerProfile cloneProfile(PlayerProfile profile) {
        return profile == null ? null : profile.clone();
    }

    @SuppressWarnings("unchecked")
    private void applyCorpseIdentitySkin(NPC npc, String identityName) {
        if (npc == null || identityName == null || identityName.isBlank()) {
            return;
        }
        try {
            Class<?> rawSkinTraitClass = Class.forName("net.citizensnpcs.trait.SkinTrait");
            if (!Trait.class.isAssignableFrom(rawSkinTraitClass)) {
                return;
            }
            Class<? extends Trait> skinTraitClass = (Class<? extends Trait>) rawSkinTraitClass;
            Trait skinTrait = npc.getOrAddTrait(skinTraitClass);

            invokeIfPresent(skinTrait, "setFetchDefaultSkin", new Class<?>[]{boolean.class}, new Object[]{false});
            invokeIfPresent(skinTrait, "setShouldUpdateSkins", new Class<?>[]{boolean.class}, new Object[]{false});
            if (invokeIfPresent(skinTrait, "setSkinName", new Class<?>[]{String.class, boolean.class}, new Object[]{identityName, true})) {
                return;
            }
            invokeIfPresent(skinTrait, "setSkinName", new Class<?>[]{String.class}, new Object[]{identityName});
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Failed to update corpse skin: " + throwable.getMessage());
        }
    }

    private void applyCorpseIdentityChestplate(NPC npc, ChatColor identityColor) {
        if (npc == null) {
            return;
        }
        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
        if (chestplate.getItemMeta() instanceof LeatherArmorMeta chestplateMeta) {
            chestplateMeta.setColor(resolveLeatherColor(identityColor));
            chestplateMeta.setUnbreakable(true);
            chestplate.setItemMeta(chestplateMeta);
        }
        npc.getOrAddTrait(Equipment.class).set(Equipment.EquipmentSlot.CHESTPLATE, chestplate.clone());
        Entity entity = npc.getEntity();
        if (entity instanceof LivingEntity livingEntity && livingEntity.getEquipment() != null) {
            livingEntity.getEquipment().setChestplate(chestplate.clone());
        }
    }

    private Color resolveLeatherColor(ChatColor color) {
        if (color == null) {
            return Color.fromRGB(0xFFFF55);
        }
        return switch (color) {
            case DARK_BLUE -> Color.fromRGB(0x0000AA);
            case DARK_GREEN -> Color.fromRGB(0x00AA00);
            case DARK_AQUA -> Color.fromRGB(0x00AAAA);
            case DARK_RED -> Color.fromRGB(0xAA0000);
            case DARK_PURPLE -> Color.fromRGB(0xAA00AA);
            case GOLD -> Color.fromRGB(0xFFAA00);
            case GRAY -> Color.fromRGB(0xAAAAAA);
            case DARK_GRAY -> Color.fromRGB(0x555555);
            case BLUE -> Color.fromRGB(0x5555FF);
            case GREEN -> Color.fromRGB(0x55FF55);
            case AQUA -> Color.fromRGB(0x55FFFF);
            case RED -> Color.fromRGB(0xFF5555);
            case LIGHT_PURPLE -> Color.fromRGB(0xFF55FF);
            case YELLOW -> Color.fromRGB(0xFFFF55);
            default -> Color.fromRGB(0xFFFF55);
        };
    }

    private void restoreCorpseState(NPC npc, Location location) {
        if (npc == null || location == null || !npc.isSpawned()) {
            return;
        }
        Entity entity = npc.getEntity();
        if (entity == null) {
            return;
        }
        entity.teleport(location);
        configureCorpseEntity(entity);
    }

    private void startCorpseStabilization(NPC npc, Location location) {
        if (npc == null || location == null) {
            return;
        }
        UUID corpseId = npc.getUniqueId();
        stopCorpseStabilization(corpseId);
        restoreCorpseState(npc, location);

        BukkitTask task = new BukkitRunnable() {
            private int livedTicks = 0;

            @Override
            public void run() {
                if (!npc.isSpawned()) {
                    stopCorpseStabilization(corpseId);
                    return;
                }
                restoreCorpseState(npc, location);
                livedTicks += 5;
                if (livedTicks >= 20 * 15) {
                    stopCorpseStabilization(corpseId);
                }
            }
        }.runTaskTimer(plugin, 1L, 5L);
        corpseStabilizationTasks.put(corpseId, task);
    }

    private void stopCorpseStabilization(UUID corpseId) {
        if (corpseId == null) {
            return;
        }
        BukkitTask task = corpseStabilizationTasks.remove(corpseId);
        if (task != null) {
            task.cancel();
        }
    }

    public static final class CorpseIdentity {
        private final UUID corpseId;
        private final UUID sourcePlayerId;
        private final String identityName;
        private final ChatColor identityColor;
        private final PlayerProfile identityProfile;

        public CorpseIdentity(UUID corpseId,
                              UUID sourcePlayerId,
                              String identityName,
                              ChatColor identityColor,
                              PlayerProfile identityProfile) {
            this.corpseId = corpseId;
            this.sourcePlayerId = sourcePlayerId;
            this.identityName = identityName;
            this.identityColor = identityColor;
            this.identityProfile = identityProfile == null ? null : identityProfile.clone();
        }

        public UUID getCorpseId() {
            return corpseId;
        }

        public UUID getSourcePlayerId() {
            return sourcePlayerId;
        }

        public String getIdentityName() {
            return identityName;
        }

        public ChatColor getIdentityColor() {
            return identityColor;
        }

        public PlayerProfile getIdentityProfile() {
            return identityProfile == null ? null : identityProfile.clone();
        }
    }
}
