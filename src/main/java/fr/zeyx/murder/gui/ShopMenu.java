package fr.zeyx.murder.gui;

import dev.triumphteam.gui.guis.Gui;
import fr.zeyx.murder.util.TextUtil;
import fr.zeyx.murder.util.MenuUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class ShopMenu {

    private static final List<Component> ARMOR_LORE = Arrays.asList(
            TextUtil.itemComponent("&7Cost: &65000"),
            Component.empty().decoration(TextDecoration.ITALIC, false),
            TextUtil.itemComponent("&cYou need &65000 &cmore coins")
    );

    private static final List<Component> WEAPON_LORE = Arrays.asList(
            TextUtil.itemComponent("&7Cost: &615000"),
            Component.empty().decoration(TextDecoration.ITALIC, false),
            TextUtil.itemComponent("&cYou need &615000 &cmore coins")
    );

    private static final List<Component> UPGRADE_LORE = Arrays.asList(
            TextUtil.itemComponent("&7Purchase this for a &a2% &7chance"),
            TextUtil.itemComponent("&7of receiving double emeralds!"),
            Component.empty().decoration(TextDecoration.ITALIC, false),
            TextUtil.itemComponent("&7Cost: &e8000"),
            Component.empty().decoration(TextDecoration.ITALIC, false),
            TextUtil.itemComponent("&cYou need &68000 &cmore coins")
    );

    private static final List<Component> GUN_UPGRADE_LORE = Arrays.asList(
            TextUtil.itemComponent("&7You have a &a2% &7chance"),
            TextUtil.itemComponent("&7to reload twice as fast!"),
            Component.empty().decoration(TextDecoration.ITALIC, false),
            TextUtil.itemComponent("&7Cost: &e8000"),
            Component.empty().decoration(TextDecoration.ITALIC, false),
            TextUtil.itemComponent("&cYou need &68000 &cmore coins")
    );

    private static final List<Component> KNIFE_UPGRADE_LORE = Arrays.asList(
            TextUtil.itemComponent("&7Purchase this for a &a2% chance"),
            TextUtil.itemComponent("&7to pick up your knife twice as fast!"),
            Component.empty().decoration(TextDecoration.ITALIC, false),
            TextUtil.itemComponent("&7Cost: &e8000"),
            Component.empty().decoration(TextDecoration.ITALIC, false),
            TextUtil.itemComponent("&cYou need &68000 &cmore coins")
    );

    private final Gui gui;
    private final Gui upgradesGui;

    public ShopMenu() {
        this.gui = Gui.gui()
                .title(TextUtil.component("&9Shop"))
                .rows(5)
                .create();
        this.gui.setDefaultClickAction(event -> event.setCancelled(true));

        this.upgradesGui = Gui.gui()
                .title(TextUtil.component("&9Shop"))
                .rows(5)
                .create();
        this.upgradesGui.setDefaultClickAction(event -> event.setCancelled(true));

        buildItems();
        buildUpgrades();
    }

    public void open(Player player) {
        gui.open(player);
    }

    private void buildItems() {
        gui.setItem(11, MenuUtil.buildItem(Material.ENDER_CHEST, TextUtil.itemComponent("&9Head Cosmetics"), null, false));
        gui.setItem(12, MenuUtil.buildItem(Material.REDSTONE, TextUtil.itemComponent("&9Purchase Upgrades"), null, false, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                upgradesGui.open(player);
            }
        }));
        gui.setItem(15, MenuUtil.buildItem(Material.EMERALD, TextUtil.itemComponent("&6Coins"),
                List.of(TextUtil.itemComponent("&7You have &a0 &7Coins")), false));

        gui.setItem(29, MenuUtil.buildItem(Material.LEATHER_HELMET, TextUtil.itemComponent("&9Leather Helmet"), ARMOR_LORE, true));
        gui.setItem(30, MenuUtil.buildItem(Material.LEATHER_LEGGINGS, TextUtil.itemComponent("&9Leather Leggings"), ARMOR_LORE, true));
        gui.setItem(31, MenuUtil.buildItem(Material.LEATHER_BOOTS, TextUtil.itemComponent("&9Leather Boots"), ARMOR_LORE, true));
        gui.setItem(33, MenuUtil.buildItem(Material.STONE_SWORD, TextUtil.itemComponent("&9Stone Weapon"), WEAPON_LORE, true));
    }

    private void buildUpgrades() {
        upgradesGui.setItem(9, MenuUtil.buildItem(Material.ARROW, TextUtil.itemComponent("&cBack"),
                List.of(TextUtil.itemComponent("&7Go back to the previous page")), false, event -> {
                    if (event.getWhoClicked() instanceof Player player) {
                        gui.open(player);
                    }
                }));

        upgradesGui.setItem(11, MenuUtil.buildItem(Material.REDSTONE, TextUtil.itemComponent("&9Emerald Upgrade I"), UPGRADE_LORE, false));
        upgradesGui.setItem(12, MenuUtil.buildItem(Material.WOODEN_HOE, TextUtil.itemComponent("&9Gun Upgrade I"), GUN_UPGRADE_LORE, true));
        upgradesGui.setItem(13, MenuUtil.buildItem(Material.WOODEN_HOE, TextUtil.itemComponent("&9Knife Upgrade I"), KNIFE_UPGRADE_LORE, true));
        upgradesGui.setItem(40, MenuUtil.buildItem(Material.EMERALD, TextUtil.itemComponent("&cCoins"),
                List.of(TextUtil.itemComponent("&7You have &a0 &7Coins")), false));
    }
}
