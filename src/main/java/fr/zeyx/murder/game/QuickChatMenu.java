package fr.zeyx.murder.game;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import fr.zeyx.murder.util.ItemBuilder;

public final class QuickChatMenu {

    public static final String CHAT_BOOK_NAME = "§6§lSend Chat Message§r §7• Right Click";
    public static final String RED_DYE_NAME = "§6Help me, I am in trouble! ";
    public static final String ORANGE_DYE_NAME = "§6I am present!";
    public static final String YELLOW_DYE_NAME = "§6Who is still alive?";
    public static final String LIME_DYE_NAME = "§6I am next to X";
    public static final String GREEN_DYE_NAME = "§6I am next to the Murderer!";
    public static final String LAPIS_DYE_NAME = "§6I have a gun!";
    public static final String CYAN_DYE_NAME = "§6Please do not shoot me, I am Innocent!";
    public static final String LIGHT_BLUE_DYE_NAME = "§6Get back or I'll shoot you!";
    public static final String CLOSE_NAME = "§6Close Menu";

    private QuickChatMenu() {
    }

    public static ItemStack buildChatBook() {
        return new ItemBuilder(Material.BOOK).setName(CHAT_BOOK_NAME).toItemStack();
    }

    public static ItemStack[] buildMenuHotbar() {
        return new ItemStack[] {
                new ItemBuilder(Material.RED_DYE).setName(RED_DYE_NAME).toItemStack(),
                new ItemBuilder(Material.ORANGE_DYE).setName(ORANGE_DYE_NAME).toItemStack(),
                new ItemBuilder(Material.YELLOW_DYE).setName(YELLOW_DYE_NAME).toItemStack(),
                new ItemBuilder(Material.LIME_DYE).setName(LIME_DYE_NAME).toItemStack(),
                new ItemBuilder(Material.GREEN_DYE).setName(GREEN_DYE_NAME).toItemStack(),
                new ItemBuilder(Material.BLUE_DYE).setName(LAPIS_DYE_NAME).toItemStack(),
                new ItemBuilder(Material.CYAN_DYE).setName(CYAN_DYE_NAME).toItemStack(),
                new ItemBuilder(Material.LIGHT_BLUE_DYE).setName(LIGHT_BLUE_DYE_NAME).toItemStack(),
                new ItemBuilder(Material.REDSTONE).setName(CLOSE_NAME).toItemStack()
        };
    }

    public static String resolveMessage(String displayName) {
        if (displayName == null) {
            return null;
        }
        String normalized = displayName.trim();
        if (normalized.equals(RED_DYE_NAME.trim())) {
            return RED_DYE_NAME.trim();
        }
        if (normalized.equals(ORANGE_DYE_NAME.trim())) {
            return ORANGE_DYE_NAME;
        }
        if (normalized.equals(YELLOW_DYE_NAME.trim())) {
            return YELLOW_DYE_NAME;
        }
        if (normalized.equals(LIME_DYE_NAME.trim())) {
            return LIME_DYE_NAME;
        }
        if (normalized.equals(GREEN_DYE_NAME.trim())) {
            return GREEN_DYE_NAME;
        }
        if (normalized.equals(LAPIS_DYE_NAME.trim())) {
            return LAPIS_DYE_NAME;
        }
        if (normalized.equals(CYAN_DYE_NAME.trim())) {
            return CYAN_DYE_NAME;
        }
        if (normalized.equals(LIGHT_BLUE_DYE_NAME.trim())) {
            return LIGHT_BLUE_DYE_NAME;
        }
        return null;
    }
}
