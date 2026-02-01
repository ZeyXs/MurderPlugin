package fr.zeyx.murder.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public final class MojangUtil {

    private static final int MOJANG_CONNECT_TIMEOUT_MS = 5000;
    private static final int MOJANG_READ_TIMEOUT_MS = 5000;
    private static final Map<String, MojangTextures> CACHE = new ConcurrentHashMap<>();

    private MojangUtil() {
    }

    public static CompletableFuture<MojangTextures> resolveTextures(String username) {
        if (username == null || username.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        String key = username.toLowerCase(Locale.ROOT);
        MojangTextures cached = CACHE.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                String uuid = fetchMojangUuid(username);
                if (uuid == null) {
                    return null;
                }
                MojangTextures textures = fetchMojangTextures(uuid);
                if (textures != null && textures.hasValue()) {
                    CACHE.put(key, textures);
                }
                return textures;
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    private static String fetchMojangUuid(String username) throws IOException {
        String url = "https://api.mojang.com/users/profiles/minecraft/" + username;
        JsonObject response = readJson(url);
        if (response == null || !response.has("id")) {
            return null;
        }
        return response.get("id").getAsString();
    }

    private static MojangTextures fetchMojangTextures(String uuid) throws IOException {
        String url = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false";
        JsonObject response = readJson(url);
        if (response == null) {
            return null;
        }
        JsonArray properties = response.getAsJsonArray("properties");
        if (properties == null) {
            return null;
        }
        for (int i = 0; i < properties.size(); i++) {
            JsonObject property = properties.get(i).getAsJsonObject();
            if (!property.has("name") || !"textures".equals(property.get("name").getAsString())) {
                continue;
            }
            if (!property.has("value")) {
                continue;
            }
            String value = property.get("value").getAsString();
            String signature = property.has("signature") ? property.get("signature").getAsString() : null;
            return new MojangTextures(value, signature);
        }
        return null;
    }

    private static JsonObject readJson(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(MOJANG_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(MOJANG_READ_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "MurderPlugin");
        int code = connection.getResponseCode();
        if (code != 200) {
            return null;
        }
        try (InputStream stream = connection.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            String payload = builder.toString().trim();
            if (payload.isEmpty()) {
                return null;
            }
            return JsonParser.parseString(payload).getAsJsonObject();
        }
    }

    public static final class MojangTextures {
        private final String value;
        private final String signature;

        private MojangTextures(String value, String signature) {
            this.value = value;
            this.signature = signature;
        }

        public String getValue() {
            return value;
        }

        public String getSignature() {
            return signature;
        }

        public boolean hasValue() {
            return value != null && !value.isBlank();
        }
    }
}
