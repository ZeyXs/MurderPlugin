package fr.zeyx.murder.game.feature;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.game.QuickChatMenu;
import fr.zeyx.murder.game.Role;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.game.service.NametagService;
import fr.zeyx.murder.util.TextUtil;
import fr.zeyx.murder.util.ItemBuilder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;

public class LoadoutFeature {

    private static final int MURDERER_FOOD_LEVEL = 8;
    private static final int NON_MURDERER_FOOD_LEVEL = 6;

    private static final String MURDERER_KNIFE_NAME = "&7&oKnife";
    private static final String MURDERER_BUY_KNIFE_NAME = "&7&lBuy Knife&r &7• Right Click";
    private static final String MURDERER_SWITCH_IDENTITY_NAME = "&7&lSwitch Identity&r &7• Right Click";
    private final GameManager gameManager;
    private final GunFeature gunFeature;

    public LoadoutFeature(GameManager gameManager, GunFeature gunFeature) {
        this.gameManager = gameManager;
        this.gunFeature = gunFeature;
    }

    public int getLockedFoodLevel(Role role) {
        if (role == null) {
            return -1;
        }
        return role == Role.MURDERER ? MURDERER_FOOD_LEVEL : NON_MURDERER_FOOD_LEVEL;
    }

    public void enforceHungerLock(Player player, Role role) {
        if (player == null || role == null) {
            return;
        }
        int foodLevel = getLockedFoodLevel(role);
        if (foodLevel < 0) {
            return;
        }
        player.setFoodLevel(foodLevel);
        player.setSaturation(0f);
        player.setExhaustion(0f);
        player.sendHealthUpdate(player.getHealth(), foodLevel, 0f);
    }

    public void preparePlayerForRound(Player player, Role role) {
        if (player == null || role == null) {
            return;
        }

        player.getInventory().clear();
        player.getInventory().setHeldItemSlot(8);
        player.setLevel(0);
        player.setExp(1.0f);
        enforceHungerLock(player, role);

        if (role == Role.MURDERER) {
            ItemStack knife = new ItemBuilder(Material.WOODEN_SWORD).setName(TextUtil.itemComponent(MURDERER_KNIFE_NAME, true)).toItemStack();
            applyInstantAttackSpeed(knife);
            player.getInventory().setItem(0, knife);
            player.getInventory().setItem(3, new ItemBuilder(Material.GRAY_DYE).setName(TextUtil.itemComponent(MURDERER_BUY_KNIFE_NAME)).toItemStack());
            player.getInventory().setItem(4, new ItemBuilder(Material.GRAY_DYE).setName(TextUtil.itemComponent(MURDERER_SWITCH_IDENTITY_NAME)).toItemStack());
        } else if (role == Role.DETECTIVE) {
            ItemStack gun = gunFeature.createGunItem();
            player.getInventory().setItem(0, gun);
        }
        player.getInventory().setItem(8, QuickChatMenu.buildChatBook());
        applyIdentityChestplate(player);

        String roleLine = switch (role) {
            case MURDERER -> "&4Murderer";
            case DETECTIVE -> "&1Detective";
            case BYSTANDER -> "&bBystander";
        };

        String identityName = gameManager.getSecretIdentityManager().getCurrentIdentityDisplayName(player.getUniqueId());
        if (identityName == null || identityName.isBlank()) {
            identityName = gameManager.getSecretIdentityManager().getColoredName(player);
        }
        if (identityName == null || identityName.isBlank()) {
            identityName = "&f" + player.getName();
        }

        NametagService.hide(player);
        gameManager.getScoreboardManager().showGameBoard(player, roleLine, identityName);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 5, 0, false, false, false));
        player.sendMessage(TextUtil.component("&7Your secret identity is: " + identityName));
        showRoleTitle(player, role);
    }

    private void applyIdentityChestplate(Player player) {
        Color identityColor = gameManager.getSecretIdentityManager().getCurrentIdentityLeatherColor(player.getUniqueId());
        if (identityColor == null) {
            return;
        }
        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
        if (chestplate.getItemMeta() instanceof LeatherArmorMeta chestplateMeta) {
            chestplateMeta.setColor(identityColor);
            chestplateMeta.setUnbreakable(true);
            chestplateMeta.itemName(TextUtil.itemComponent("&aLeather Chestplate"));
            chestplateMeta.addItemFlags(ItemFlag.HIDE_DYE, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            chestplate.setItemMeta(chestplateMeta);
        }
        player.getInventory().setChestplate(chestplate);
    }

    private void showRoleTitle(Player player, Role role) {
        Title title = switch (role) {
            case MURDERER -> Title.title(
                    TextUtil.component("&c&lMurderer      "),
                    TextUtil.component("      &4Don't get caught"),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            );
            case DETECTIVE -> Title.title(
                    TextUtil.component("&3&lBystander      "),
                    TextUtil.component("       &dWith a secret weapon"),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            );
            case BYSTANDER -> Title.title(
                    TextUtil.component("&3&lBystander       "),
                    TextUtil.component("      &3Kill the murderer"),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            );
        };
        player.showTitle(title);
        Bukkit.getScheduler().runTaskLater(MurderPlugin.getInstance(), task -> {
            if (!player.isOnline()) {
                return;
            }
            player.playSound(player.getLocation(), Sound.ENTITY_GHAST_HURT, 1.0f, 1.0f);
        }, 10L);
    }

    private void applyInstantAttackSpeed(ItemStack item) {
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(MurderPlugin.getInstance(), "instant_attack_speed");
        meta.addAttributeModifier(
                Attribute.ATTACK_SPEED,
                new AttributeModifier(
                        key,
                        1000.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.HAND
                )
        );
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
    }
}
