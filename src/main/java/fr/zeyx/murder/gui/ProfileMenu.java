package fr.zeyx.murder.gui;

import dev.triumphteam.gui.guis.Gui;
import fr.zeyx.murder.util.ChatUtil;
import fr.zeyx.murder.util.MenuUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class ProfileMenu {

    private static final List<Component> RANK_LORE = Arrays.asList(
            ChatUtil.itemComponent("&7You are rank: &aCoal"),
            ChatUtil.itemComponent("&8Progress to next tier: &c0.0%")
    );

    private static final List<Component> MURDERER_LORE = Arrays.asList(
            ChatUtil.itemComponent("&7Kills: &a0"),
            ChatUtil.itemComponent("&8Deaths: &20"),
            ChatUtil.itemComponent("&7Wins: &a0"),
            ChatUtil.itemComponent("&8Win/Death Ratio: &21")
    );

    private static final List<Component> BYSTANDER_LORE = Arrays.asList(
            ChatUtil.itemComponent("&7Murderer Kills: &a0"),
            ChatUtil.itemComponent("&8Wins: &20"),
            ChatUtil.itemComponent("&7Deaths: &a0"),
            ChatUtil.itemComponent("&8Kill/Death Ratio: &21"),
            ChatUtil.itemComponent("&7Innocents Shot: &a0")
    );

    private static final List<Component> GENERAL_LORE = Arrays.asList(
            ChatUtil.itemComponent("&7Games Played: &20"),
            ChatUtil.itemComponent("&8Total Wins: &a0"),
            ChatUtil.itemComponent("&7Weapons Traded: &20"),
            ChatUtil.itemComponent("&8Emeralds Collected: &a0"),
            ChatUtil.itemComponent("&7Karma: &a1000"),
            ChatUtil.itemComponent("&8Win/Loss Ratio: &21")
    );

    private static final List<Component> NAME_LORE = Arrays.asList(
            ChatUtil.itemComponent("&7Purchases: &a0"),
            ChatUtil.itemComponent("&8Times used: &a0")
    );

    private final Gui gui;

    public ProfileMenu() {
        this.gui = Gui.gui()
                .title(ChatUtil.component("&cProfile"))
                .rows(5)
                .create();
        this.gui.setDefaultClickAction(event -> event.setCancelled(true));

        buildItems();
    }

    public void open(Player player) {
        gui.open(player);
    }

    private void buildItems() {
        gui.setItem(10, MenuUtil.buildItem(Material.COAL_BLOCK, ChatUtil.itemComponent("&9&oRank", true), RANK_LORE, false));
        gui.setItem(11, MenuUtil.buildItem(Material.NETHER_STAR, ChatUtil.itemComponent("&c???"), null, false));
        gui.setItem(13, MenuUtil.buildItem(Material.GOLDEN_SWORD, ChatUtil.itemComponent("&c&oMurderer Stats", true), MURDERER_LORE, true));
        gui.setItem(14, MenuUtil.buildItem(Material.IRON_HOE, ChatUtil.itemComponent("&9&oBystander Stats", true), BYSTANDER_LORE, true));
        gui.setItem(15, MenuUtil.buildItem(Material.DIAMOND, ChatUtil.itemComponent("&3&oGeneral Stats", true), GENERAL_LORE, false));
        gui.setItem(16, MenuUtil.buildItem(Material.EMERALD, ChatUtil.itemComponent("&9Your Name in Murder", true), NAME_LORE, false));
        gui.setItem(31, MenuUtil.buildItem(Material.WOODEN_SWORD, ChatUtil.itemComponent("&c???"), null, true));
    }
}
