package fr.zeyx.murder.manager.config;

import fr.zeyx.murder.MurderPlugin;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SecretIdentitiesConfig {

    private final YamlConfiguration secretIdentitiesConfiguration;
    private final File secretIdentitiesFile;

    public SecretIdentitiesConfig() {
        this.secretIdentitiesFile = new File(MurderPlugin.getInstance().getDataFolder(), "secret-identities.yml");
        this.secretIdentitiesConfiguration = new YamlConfiguration();

        if (!secretIdentitiesFile.exists()) {
            MurderPlugin.getInstance().saveResource("secret-identities.yml", false);
        }
        try {
            this.secretIdentitiesConfiguration.load(this.secretIdentitiesFile);
        } catch (IOException | InvalidConfigurationException exception) {
            saveSecretIdentitiesConfig();
        }
    }

    public List<String> getSecretIdentityNames() {
        List<String> names = secretIdentitiesConfiguration.getStringList("usernames");
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> cleaned = new ArrayList<>();
        for (String name : names) {
            if (name == null) {
                continue;
            }
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                cleaned.add(trimmed);
            }
        }
        return cleaned;
    }

    private void saveSecretIdentitiesConfig() {
        try {
            secretIdentitiesConfiguration.save(secretIdentitiesFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
