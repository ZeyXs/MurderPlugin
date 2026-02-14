package fr.zeyx.murder.manager;

import fr.zeyx.murder.MurderPlugin;
import fr.zeyx.murder.util.ChatUtil;
import fr.zeyx.murder.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class GunManager {

    public static final String DETECTIVE_GUN_NAME = "&7&oGun";

    private final NamespacedKey gunItemKey;
    private final NamespacedKey gunProjectileKey;

    public GunManager() {
        this.gunItemKey = new NamespacedKey(MurderPlugin.getInstance(), "gun_item");
        this.gunProjectileKey = new NamespacedKey(MurderPlugin.getInstance(), "gun_projectile");
    }

    public ItemStack createGunItem() {
        return createGunItemWithName(ChatUtil.itemComponent(DETECTIVE_GUN_NAME, true));
    }

    public ItemStack createGunItemVersion(int version) {
        int safeVersion = Math.max(1, version);
        if (safeVersion <= 1) {
            return createGunItem();
        }
        return createGunItemWithName(ChatUtil.itemComponent("&9Gun v" + safeVersion + ".0"));
    }

    public boolean isGunItem(ItemStack item) {
        if (item == null || item.getType() != Material.WOODEN_HOE || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Byte marker = meta.getPersistentDataContainer().get(gunItemKey, PersistentDataType.BYTE);
        if (marker != null && marker == (byte) 1) {
            return true;
        }
        return ChatUtil.itemComponent(DETECTIVE_GUN_NAME, true).equals(meta.displayName());
    }

    public void markGunProjectile(Projectile projectile) {
        if (projectile == null) {
            return;
        }
        projectile.getPersistentDataContainer().set(gunProjectileKey, PersistentDataType.BYTE, (byte) 1);
    }

    public boolean isGunProjectile(Projectile projectile) {
        if (projectile == null) {
            return false;
        }
        Byte marker = projectile.getPersistentDataContainer().get(gunProjectileKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void markItemAsGun(ItemMeta meta) {
        meta.getPersistentDataContainer().set(gunItemKey, PersistentDataType.BYTE, (byte) 1);
    }

    private ItemStack createGunItemWithName(net.kyori.adventure.text.Component displayName) {
        ItemStack gun = new ItemBuilder(Material.WOODEN_HOE).setName(displayName).toItemStack();
        ItemMeta gunMeta = gun.getItemMeta();
        if (gunMeta == null) {
            return gun;
        }
        markItemAsGun(gunMeta);
        applyInstantAttackSpeed(gunMeta);
        gun.setItemMeta(gunMeta);
        return gun;
    }

    private void applyInstantAttackSpeed(ItemMeta meta) {
        NamespacedKey key = new NamespacedKey(MurderPlugin.getInstance(), "gun_instant_attack_speed");
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
    }
}
