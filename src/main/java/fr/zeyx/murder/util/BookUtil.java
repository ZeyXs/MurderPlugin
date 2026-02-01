package fr.zeyx.murder.util;

import fr.zeyx.murder.manager.ConfigurationManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

public final class BookUtil {

    private BookUtil() {
    }

    public static ItemStack buildBook(ConfigurationManager configurationManager, String key, Component displayName) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) {
            return book;
        }

        if (displayName != null) {
            meta.displayName(displayName);
        }

        if (configurationManager == null) {
            book.setItemMeta(meta);
            return book;
        }

        org.bukkit.configuration.ConfigurationSection section = configurationManager.getBookSection(key);
        if (section == null) {
            book.setItemMeta(meta);
            return book;
        }

        String title = section.getString("title");
        if (title != null && !title.isEmpty()) {
            meta.title(ChatUtil.component(title));
        }

        String author = section.getString("author");
        if (author != null && !author.isEmpty()) {
            meta.author(ChatUtil.component(author));
        }

        List<String> rawPages = section.getStringList("pages");
        if (!rawPages.isEmpty()) {
            List<Component> pages = new ArrayList<>();
            for (String rawPage : rawPages) {
                pages.add(ChatUtil.component(rawPage == null ? "" : rawPage));
            }
            meta.pages(pages);
        }

        book.setItemMeta(meta);
        return book;
    }
}
