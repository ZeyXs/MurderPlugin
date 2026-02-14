package fr.zeyx.murder.game.feature;

import com.destroystokyo.paper.profile.PlayerProfile;
import fr.zeyx.murder.game.QuickChatMenu;
import fr.zeyx.murder.manager.CorpseManager;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import fr.zeyx.murder.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class SwitchIdentityFeature {

    private static final String MURDERER_SWITCH_IDENTITY_DISABLED_NAME = "&7&lSwitch Identity&r &7• Right Click";
    private static final String MURDERER_SWITCH_IDENTITY_ENABLED_NAME = "&d&lSwitch Identity&r &7• Right Click";
    private static final String MURDERER_SWITCH_IDENTITY_DISABLED_LEGACY = ChatColor.translateAlternateColorCodes('&', MURDERER_SWITCH_IDENTITY_DISABLED_NAME);
    private static final String MURDERER_SWITCH_IDENTITY_ENABLED_LEGACY = ChatColor.translateAlternateColorCodes('&', MURDERER_SWITCH_IDENTITY_ENABLED_NAME);
    private static final String NO_DEAD_BODY_NEARBY_MESSAGE = "&cNo dead body nearby!";
    private static final int MURDERER_SWITCH_IDENTITY_COST = 3;
    private static final int MURDERER_SWITCH_IDENTITY_SLOT = 4;
    private static final double MURDERER_SWITCH_IDENTITY_RADIUS = 1.75D;

    private final GameManager gameManager;
    private final EmeraldFeature emeraldFeature;

    public SwitchIdentityFeature(GameManager gameManager, EmeraldFeature emeraldFeature) {
        this.gameManager = gameManager;
        this.emeraldFeature = emeraldFeature;
    }

    public boolean isSwitchIdentityItem(String legacyName) {
        return MURDERER_SWITCH_IDENTITY_DISABLED_LEGACY.equals(legacyName)
                || MURDERER_SWITCH_IDENTITY_ENABLED_LEGACY.equals(legacyName);
    }

    public void handleIdentitySwitch(Player murderer,
                                     UUID murdererId,
                                     List<UUID> alivePlayers,
                                     List<UUID> roundParticipants,
                                     Function<UUID, String> identityDisplayResolver,
                                     Consumer<UUID> onMurdererIdentityChanged) {
        if (murderer == null || murdererId == null) {
            return;
        }
        if (!murdererId.equals(murderer.getUniqueId()) || !alivePlayers.contains(murdererId)) {
            return;
        }
        CorpseManager.CorpseIdentity corpseIdentity = gameManager.getCorpseManager()
                .findNearestCorpseIdentity(murderer.getLocation(), MURDERER_SWITCH_IDENTITY_RADIUS);
        if (corpseIdentity == null
                || !roundParticipants.contains(corpseIdentity.getSourcePlayerId())
                || corpseIdentity.getIdentityName() == null
                || corpseIdentity.getIdentityName().isBlank()) {
            setMurdererSwitchIdentityItem(murderer, false);
            sendNoDeadBodyNearbyMessage(murderer);
            return;
        }
        int missingEmeralds = emeraldFeature == null
                ? 0
                : emeraldFeature.getMissingEmeralds(murdererId, MURDERER_SWITCH_IDENTITY_COST);
        if (missingEmeralds > 0) {
            sendNeedEmeraldsMessage(murderer, MURDERER_SWITCH_IDENTITY_COST);
            setMurdererSwitchIdentityItem(murderer, false);
            return;
        }
        if (emeraldFeature != null && !emeraldFeature.trySpendEmeralds(murderer, MURDERER_SWITCH_IDENTITY_COST)) {
            sendNeedEmeraldsMessage(murderer, MURDERER_SWITCH_IDENTITY_COST);
            setMurdererSwitchIdentityItem(murderer, false);
            return;
        }

        String oldMurdererIdentity = gameManager.getSecretIdentityManager().getCurrentIdentityName(murdererId);
        if (oldMurdererIdentity == null || oldMurdererIdentity.isBlank()) {
            oldMurdererIdentity = murderer.getName();
        }
        ChatColor oldMurdererColor = gameManager.getSecretIdentityManager().getCurrentIdentityColor(murdererId);
        PlayerProfile oldMurdererProfile = resolveIdentityProfile(murdererId, murderer);

        gameManager.getSecretIdentityManager().cacheIdentityProfile(
                corpseIdentity.getIdentityName(),
                corpseIdentity.getIdentityProfile()
        );
        boolean switched = gameManager.getSecretIdentityManager().applySpecificIdentityFromCache(
                murderer,
                corpseIdentity.getIdentityName(),
                corpseIdentity.getIdentityColor()
        );
        if (!switched) {
            return;
        }

        gameManager.getCorpseManager().setCorpseIdentity(
                corpseIdentity.getCorpseId(),
                oldMurdererIdentity,
                oldMurdererColor,
                oldMurdererProfile
        );
        onMurdererIdentityChanged.accept(murdererId);

        String newIdentityDisplay = identityDisplayResolver.apply(murdererId);
        if (newIdentityDisplay == null || newIdentityDisplay.isBlank()) {
            newIdentityDisplay = "&f" + murderer.getName();
        }
        refreshIdentityChestplate(murderer);
        gameManager.getScoreboardManager().showGameBoard(murderer, "&4Murderer", newIdentityDisplay);
        murderer.sendMessage(ChatUtil.component("&aYou switched identities! You are now: " + newIdentityDisplay));
        boolean stillHasEnoughEmeralds = emeraldFeature == null
                || emeraldFeature.getMissingEmeralds(murdererId, MURDERER_SWITCH_IDENTITY_COST) == 0;
        setMurdererSwitchIdentityItem(murderer, stillHasEnoughEmeralds);
    }

    public void updateSwitchIdentityItem(UUID murdererId, List<UUID> alivePlayers, List<UUID> roundParticipants) {
        if (murdererId == null || !alivePlayers.contains(murdererId)) {
            return;
        }
        Player murderer = Bukkit.getPlayer(murdererId);
        if (murderer == null || !murderer.isOnline()) {
            return;
        }
        CorpseManager.CorpseIdentity corpseIdentity = gameManager.getCorpseManager()
                .findNearestCorpseIdentity(murderer.getLocation(), MURDERER_SWITCH_IDENTITY_RADIUS);
        boolean nearEligibleCorpse = corpseIdentity != null && roundParticipants.contains(corpseIdentity.getSourcePlayerId());
        boolean hasEnoughEmeralds = emeraldFeature == null
                || emeraldFeature.getMissingEmeralds(murdererId, MURDERER_SWITCH_IDENTITY_COST) == 0;
        boolean canActivate = nearEligibleCorpse && hasEnoughEmeralds;
        setMurdererSwitchIdentityItem(murderer, canActivate);
    }

    private void setMurdererSwitchIdentityItem(Player murderer, boolean active) {
        if (murderer == null) {
            return;
        }
        if (isQuickChatMenuOpen(murderer)) {
            return;
        }
        ItemStack current = murderer.getInventory().getItem(MURDERER_SWITCH_IDENTITY_SLOT);
        Material expectedMaterial = active ? Material.PINK_DYE : Material.GRAY_DYE;
        String expectedLegacyName = active ? MURDERER_SWITCH_IDENTITY_ENABLED_LEGACY : MURDERER_SWITCH_IDENTITY_DISABLED_LEGACY;
        if (current != null && current.getType() == expectedMaterial && current.hasItemMeta()) {
            Component currentName = current.getItemMeta().displayName();
            if (currentName != null) {
                String legacy = org.bukkit.ChatColor.stripColor(LegacyComponentSerializer.legacySection().serialize(currentName));
                String expected = org.bukkit.ChatColor.stripColor(expectedLegacyName);
                if (legacy != null && legacy.equals(expected)) {
                    return;
                }
            }
        }
        murderer.getInventory().setItem(
                MURDERER_SWITCH_IDENTITY_SLOT,
                new ItemBuilder(expectedMaterial)
                        .setName(ChatUtil.itemComponent(active ? MURDERER_SWITCH_IDENTITY_ENABLED_NAME : MURDERER_SWITCH_IDENTITY_DISABLED_NAME))
                        .toItemStack()
        );
    }

    private boolean isQuickChatMenuOpen(Player player) {
        if (player == null) {
            return false;
        }
        ItemStack slotEight = player.getInventory().getItem(8);
        if (slotEight == null || !slotEight.hasItemMeta()) {
            return false;
        }
        Component itemName = slotEight.getItemMeta().displayName();
        if (itemName == null) {
            return false;
        }
        String legacyName = LegacyComponentSerializer.legacySection().serialize(itemName);
        return QuickChatMenu.CLOSE_NAME.equals(legacyName);
    }

    private void refreshIdentityChestplate(Player player) {
        if (player == null) {
            return;
        }
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate == null || chestplate.getType() != Material.LEATHER_CHESTPLATE) {
            return;
        }
        if (chestplate.getItemMeta() instanceof LeatherArmorMeta chestplateMeta) {
            chestplateMeta.setColor(gameManager.getSecretIdentityManager().getCurrentIdentityLeatherColor(player.getUniqueId()));
            chestplate.setItemMeta(chestplateMeta);
            player.getInventory().setChestplate(chestplate);
        }
    }

    private PlayerProfile resolveIdentityProfile(UUID playerId, Player fallbackPlayer) {
        PlayerProfile identityProfile = gameManager.getSecretIdentityManager().getCurrentIdentityProfile(playerId);
        if (identityProfile != null) {
            return identityProfile;
        }
        if (fallbackPlayer == null || fallbackPlayer.getPlayerProfile() == null) {
            return null;
        }
        return fallbackPlayer.getPlayerProfile().clone();
    }

    private void sendNoDeadBodyNearbyMessage(Player player) {
        if (player == null) {
            return;
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', NO_DEAD_BODY_NEARBY_MESSAGE));
    }

    private void sendNeedEmeraldsMessage(Player player, int requiredCost) {
        if (player == null) {
            return;
        }
        int required = Math.max(1, requiredCost);
        player.sendMessage(ChatUtil.component("&cYou need " + required + " emeralds to do that!"));
    }
}
