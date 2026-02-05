package fr.zeyx.murder.manager.config;

import fr.zeyx.murder.MurderPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class BooksConfig {

    private final YamlConfiguration booksConfiguration;
    private final File booksFile;

    public BooksConfig() {
        this.booksFile = new File(MurderPlugin.getInstance().getDataFolder(), "books.yml");
        this.booksConfiguration = new YamlConfiguration();

        if (!booksFile.exists()) {
            MurderPlugin.getInstance().saveResource("books.yml", false);
        }
        try {
            this.booksConfiguration.load(this.booksFile);
        } catch (IOException | InvalidConfigurationException exception) {
            saveBooksConfig();
        }
    }

    public ConfigurationSection getBookSection(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return booksConfiguration.getConfigurationSection(key);
    }

    private void saveBooksConfig() {
        try {
            booksConfiguration.save(booksFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
