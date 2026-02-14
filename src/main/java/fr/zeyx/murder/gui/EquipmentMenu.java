package fr.zeyx.murder.gui;

import dev.triumphteam.gui.guis.Gui;
import fr.zeyx.murder.util.TextUtil;
import fr.zeyx.murder.util.MenuUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class EquipmentMenu {

    private static final List<Component> RANDOM_LORE = Arrays.asList(
            TextUtil.itemComponent("&7Left click to equip random items"),
            TextUtil.itemComponent("&8Right click to &aenable")
    );

    private final Gui gui;

    public EquipmentMenu() {
        this.gui = Gui.gui()
                .title(TextUtil.component("&2Equipment"))
                .rows(6)
                .create();
        this.gui.setDefaultClickAction(event -> event.setCancelled(true));

        buildItems();
    }

    public void open(Player player) {
        gui.open(player);
    }

    private void buildItems() {
        gui.setItem(11, MenuUtil.buildItem(Material.GLASS_PANE, TextUtil.itemComponent("&bRemove Helmet"), null, false));
        gui.setItem(29, MenuUtil.buildItem(Material.GLASS_PANE, TextUtil.itemComponent("&bRemove Leggings"), null, false));
        gui.setItem(38, MenuUtil.buildItem(Material.GLASS_PANE, TextUtil.itemComponent("&bRemove Boots"), null, false));

        gui.setItem(12, MenuUtil.buildColoredLeather(Material.LEATHER_HELMET, TextUtil.itemComponent("&bSelect Helmet"), Color.fromRGB(0x8b8b8b), true));
        gui.setItem(21, MenuUtil.buildItem(Material.LEATHER_CHESTPLATE, TextUtil.itemComponent("&aLeather Chestplate"), null, true));
        gui.setItem(23, MenuUtil.buildItem(Material.ENDER_CHEST, TextUtil.itemComponent("&bSelect Hat"), null, false));
        gui.setItem(30, MenuUtil.buildColoredLeather(Material.LEATHER_LEGGINGS, TextUtil.itemComponent("&bSelect Leggings"), Color.fromRGB(0x8b8b8b), true));
        gui.setItem(32, MenuUtil.buildItem(Material.WOODEN_SWORD, TextUtil.itemComponent("&bSelect Weapon"), null, true));
        gui.setItem(33, MenuUtil.buildItem(Material.COMMAND_BLOCK, TextUtil.itemComponent("&bRandom Equipment"), RANDOM_LORE, false));
        gui.setItem(39, MenuUtil.buildColoredLeather(Material.LEATHER_BOOTS, TextUtil.itemComponent("&bSelect Boots"), Color.fromRGB(0x8b8b8b), true));
    }
}
