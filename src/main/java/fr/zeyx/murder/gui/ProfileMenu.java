package fr.zeyx.murder.gui;

import dev.triumphteam.gui.guis.Gui;
import fr.zeyx.murder.util.ChatUtil;
import fr.zeyx.murder.util.MenuUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
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
            ChatUtil.itemComponent("&7Used chance: &20.0%"),
            ChatUtil.itemComponent("&8Times used: &a0")
    );

    private static final List<Component> COINS_LORE = Arrays.asList(
            ChatUtil.itemComponent("&7You have &60 &7achievement"),
            ChatUtil.itemComponent("&7points from &omurder&r&7!")
    );

    private static final List<Component> EMPTY_LORE = List.of();

    private final Gui gui;
    private final Gui achievementsGui;

    public ProfileMenu() {
        this.gui = Gui.gui()
                .title(ChatUtil.component("&cProfile"))
                .rows(5)
                .create();
        this.gui.setDefaultClickAction(event -> event.setCancelled(true));

        this.achievementsGui = Gui.gui()
                .title(ChatUtil.component("&cProfile"))
                .rows(5)
                .create();
        this.achievementsGui.setDefaultClickAction(event -> event.setCancelled(true));

        buildItems();
        buildAchievements();
    }

    public void open(Player player) {
        gui.open(player);
    }

    private void buildItems() {
        gui.setItem(10, MenuUtil.buildItem(Material.COAL_BLOCK, ChatUtil.itemComponent("&9&oRank", true), RANK_LORE, false));
        gui.setItem(11, MenuUtil.buildItem(Material.NETHER_STAR, ChatUtil.itemComponent("&5View Achievements"), null, false, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                achievementsGui.open(player);
            }
        }));
        gui.setItem(13, MenuUtil.buildItem(Material.GOLDEN_SWORD, ChatUtil.itemComponent("&c&oMurderer Stats", true), MURDERER_LORE, true));
        gui.setItem(14, MenuUtil.buildItem(Material.IRON_HOE, ChatUtil.itemComponent("&9&oBystander Stats", true), BYSTANDER_LORE, true));
        gui.setItem(15, MenuUtil.buildItem(Material.DIAMOND, ChatUtil.itemComponent("&3&oGeneral Stats", true), GENERAL_LORE, false));
        gui.setItem(16, MenuUtil.buildItem(Material.EMERALD, ChatUtil.itemComponent("&9Your Name in Murder", true), NAME_LORE, false));
        gui.setItem(31, MenuUtil.buildItem(Material.WOODEN_SWORD, ChatUtil.itemComponent("&c???"), null, true));
    }

    private void buildAchievements() {
        achievementsGui.setItem(9, MenuUtil.buildItem(Material.ARROW, ChatUtil.itemComponent("&cBack"),
                List.of(ChatUtil.itemComponent("&7Go back to the previous page")), false, event -> {
                    if (event.getWhoClicked() instanceof Player player) {
                        gui.open(player);
                    }
                }));

        achievementsGui.setItem(1, MenuUtil.buildItem(Material.DIAMOND_HOE, ChatUtil.itemComponent("&9Shoot the Murderer V"),
                achievementLore("&7You have killed the\n&7murderer 250 times!\n\n&60 &7Achievement Points"), true));
        achievementsGui.setItem(2, MenuUtil.buildItem(Material.DIAMOND_HOE, ChatUtil.itemComponent("&9Win As The Murderer V"),
                achievementLore("&7You have won as the\n&7murderer 100 times!\n\n&60 &7Achievement Points"), true));
        achievementsGui.setItem(3, MenuUtil.buildItem(Material.EMERALD, ChatUtil.itemComponent("&9Picking Up The Pieces V"),
                achievementLore("&7You have picked up\n&72000 emerald!\n\n&60 &7Achievement Points"), false));
        achievementsGui.setItem(4, MenuUtil.buildItem(Material.EMERALD_BLOCK, ChatUtil.itemComponent("&9Trading With the Villagers V"),
                achievementLore("&7You traded emeralds for\n&7200 weapons!\n\n&60 &7Achievement Points"), false));
        achievementsGui.setItem(5, MenuUtil.buildItem(Material.ENCHANTED_BOOK, ChatUtil.itemComponent("&9Saint"),
                achievementLore("&7You traded emeralds for\n&7200 weapons!"), false));
        achievementsGui.setItem(6, MenuUtil.buildItem(Material.INK_SAC, ChatUtil.itemComponent("&9Kill Frenzy"),
                achievementLore("&7Secret Achievement!\n\n&630 &7Achievement Points"), false));
        achievementsGui.setItem(7, MenuUtil.buildItem(Material.FEATHER, ChatUtil.itemComponent("&9Parkour Over Murder"),
                achievementLore("&7You completed the lobby\n&7parkour!\n\n&610 &7Achievement Points"), false));
        achievementsGui.setItem(10, MenuUtil.buildItem(Material.NETHER_STAR, ChatUtil.itemComponent("&9Being Watched"),
                achievementLore("&7You played a game with\n&7a staff member\n\n&610 &7Achievement Points"), false));
        achievementsGui.setItem(11, MenuUtil.buildItem(Material.INK_SAC, ChatUtil.itemComponent("&9Slow But Effective"),
                achievementLore("&7Secret Achievement\n\n&625 &7Achievement Points"), false));
        achievementsGui.setItem(12, MenuUtil.buildItem(Material.ARROW, ChatUtil.itemComponent("&9Long Shot"),
                achievementLore("&7You killed someone from\n&7over 25 blocks away\n\n&650 &7Achievement Points"), false));
        achievementsGui.setItem(13, MenuUtil.buildItem(Material.BOW, ChatUtil.itemComponent("&9Not so Personal"),
                achievementLore("&7You threw your knife and\n&7killed someone\n\n&610 &7Achievement Points"), false));
        achievementsGui.setItem(14, MenuUtil.buildItem(Material.INK_SAC, ChatUtil.itemComponent("&9Clean Hands"),
                achievementLore("&7Win as the murderer\n&7without stabbing anyone\n\n&650 &7Achievement Points"), false));
        achievementsGui.setItem(15, MenuUtil.buildItem(Material.ANVIL, ChatUtil.itemComponent("&9Personality Issues"),
                achievementLore("&7You changed identity 3 times in\n&7a single game as the murderer\n\n&60 &7Achievement Points"), false));
        achievementsGui.setItem(16, MenuUtil.buildItem(Material.DIAMOND_PICKAXE, ChatUtil.itemComponent("&9Best Gun"),
                achievementLore("&7You upgraded your gun twice\n&7in a single game\n\n&60 &7Achievement Points"), true));
        achievementsGui.setItem(19, MenuUtil.buildItem(Material.DIAMOND_AXE, null, EMPTY_LORE, true));
        achievementsGui.setItem(20, MenuUtil.buildItem(Material.TNT, ChatUtil.itemComponent("&9Hat Owner V"),
                achievementLore("&7You won 50 hats!\n\n&60 &7Achievement Points"), false));
        achievementsGui.setItem(21, MenuUtil.buildItem(Material.INK_SAC, ChatUtil.itemComponent("&9Kill Yourself"),
                achievementLore("&7Kill someone disguised as you\n&7without losing any karma!\n\n&610 &7Achievement Points"), false));
        achievementsGui.setItem(22, MenuUtil.buildItem(Material.INK_SAC, ChatUtil.itemComponent("&9Not so Secret Identity"),
                achievementLore("&7Be disguised as yourself\n\n&610 &7Achievement Points"), false));
        achievementsGui.setItem(23, MenuUtil.buildItem(Material.CLOCK, ChatUtil.itemComponent("&9Cutting It Close"),
                achievementLore("&7You won the game under 15 seconds left!\n\n&650 &7Achievement Points"), false));
        achievementsGui.setItem(40, MenuUtil.buildItem(Material.EMERALD, ChatUtil.itemComponent("&cCoins"), COINS_LORE, false));
    }

    private List<Component> achievementLore(String text) {
        String[] lines = text == null ? new String[0] : text.split("\n", -1);
        List<Component> lore = new ArrayList<>(lines.length);
        for (String line : lines) {
            lore.add(ChatUtil.itemComponent(line));
        }
        return lore;
    }
}
