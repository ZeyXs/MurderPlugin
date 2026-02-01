package fr.zeyx.murder.util;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.List;
import java.util.function.Consumer;

public final class MenuUtil {

    private MenuUtil() {
    }

    public static GuiItem buildItem(Material material, Component name, List<Component> lore, boolean hideAttributes) {
        return buildItem(material, name, lore, hideAttributes, null);
    }

    public static GuiItem buildItem(
            Material material,
            Component name,
            List<Component> lore,
            boolean hideAttributes,
            Consumer<InventoryClickEvent> onClick
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.displayName(name);
            }
            if (lore != null) {
                meta.lore(lore);
            }
            if (hideAttributes) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }
            item.setItemMeta(meta);
        }
        return ItemBuilder.from(item).asGuiItem(event -> {
            event.setCancelled(true);
            if (onClick != null) {
                onClick.accept(event);
            }
        });
    }

    public static GuiItem buildColoredLeather(Material material, Component name, Color color, boolean hideAttributes) {
        return buildColoredLeather(material, name, color, hideAttributes, null);
    }

    public static GuiItem buildColoredLeather(
            Material material,
            Component name,
            Color color,
            boolean hideAttributes,
            Consumer<InventoryClickEvent> onClick
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(color);
            leatherMeta.displayName(name);
            if (hideAttributes) {
                leatherMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }
            item.setItemMeta(leatherMeta);
        }
        return ItemBuilder.from(item).asGuiItem(event -> {
            event.setCancelled(true);
            if (onClick != null) {
                onClick.accept(event);
            }
        });
    }
}
