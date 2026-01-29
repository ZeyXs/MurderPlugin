package fr.zeyx.murder.util;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class SnapshotCodec {

    private static final int SNAPSHOT_VERSION = 2;

    private SnapshotCodec() {
    }

    public static String encode(ItemStack[] contents,
                                ItemStack[] armor,
                                GameMode gameMode,
                                Location location,
                                double health,
                                int foodLevel,
                                float saturation,
                                float exhaustion,
                                float exp,
                                int level,
                                int totalExperience,
                                PotionEffect[] potionEffects) {
        if (contents == null || armor == null || gameMode == null || location == null) {
            throw new IllegalArgumentException("Snapshot data cannot be null.");
        }

        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(byteStream)) {
            dataOutput.writeInt(SNAPSHOT_VERSION);
            dataOutput.writeObject(contents);
            dataOutput.writeObject(armor);
            dataOutput.writeUTF(gameMode.name());
            dataOutput.writeUTF(location.getWorld() == null ? "" : location.getWorld().getName());
            dataOutput.writeDouble(location.getX());
            dataOutput.writeDouble(location.getY());
            dataOutput.writeDouble(location.getZ());
            dataOutput.writeFloat(location.getYaw());
            dataOutput.writeFloat(location.getPitch());
            dataOutput.writeDouble(health);
            dataOutput.writeInt(foodLevel);
            dataOutput.writeFloat(saturation);
            dataOutput.writeFloat(exhaustion);
            dataOutput.writeFloat(exp);
            dataOutput.writeInt(level);
            dataOutput.writeInt(totalExperience);
            dataOutput.writeObject(potionEffects == null ? new PotionEffect[0] : potionEffects);
            dataOutput.flush();
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode snapshot data.", exception);
        }
    }

    public static DecodedSnapshot decode(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        byte[] raw = Base64.getDecoder().decode(data);
        DecodedSnapshot decoded = tryDecodeV2(raw);
        if (decoded != null) {
            return decoded;
        }
        DecodedSnapshot legacyDecoded = tryDecodeV1(raw);
        if (legacyDecoded != null) {
            return legacyDecoded;
        }
        throw new IllegalStateException("Failed to decode snapshot data.");
    }

    private static DecodedSnapshot tryDecodeV2(byte[] raw) {
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(raw);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(byteStream)) {
            int version = dataInput.readInt();
            if (version != SNAPSHOT_VERSION) {
                return null;
            }

            ItemStack[] contents = (ItemStack[]) dataInput.readObject();
            ItemStack[] armor = (ItemStack[]) dataInput.readObject();
            GameMode gameMode = GameMode.valueOf(dataInput.readUTF());
            String worldName = dataInput.readUTF();
            Location location = new Location(
                    worldName.isEmpty() ? null : Bukkit.getWorld(worldName),
                    dataInput.readDouble(),
                    dataInput.readDouble(),
                    dataInput.readDouble(),
                    dataInput.readFloat(),
                    dataInput.readFloat()
            );
            double health = dataInput.readDouble();
            int foodLevel = dataInput.readInt();
            float saturation = dataInput.readFloat();
            float exhaustion = dataInput.readFloat();
            float exp = dataInput.readFloat();
            int level = dataInput.readInt();
            int totalExperience = dataInput.readInt();
            PotionEffect[] potionEffects = (PotionEffect[]) dataInput.readObject();
            return new DecodedSnapshot(
                    contents,
                    armor,
                    gameMode,
                    location,
                    health,
                    foodLevel,
                    saturation,
                    exhaustion,
                    exp,
                    level,
                    totalExperience,
                    potionEffects
            );
        } catch (IOException | ClassNotFoundException exception) {
            return null;
        }
    }

    private static DecodedSnapshot tryDecodeV1(byte[] raw) {
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(raw);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(byteStream)) {
            ItemStack[] contents = (ItemStack[]) dataInput.readObject();
            ItemStack[] armor = (ItemStack[]) dataInput.readObject();
            GameMode gameMode = GameMode.valueOf(dataInput.readUTF());
            String worldName = dataInput.readUTF();
            Location location = new Location(
                    worldName.isEmpty() ? null : Bukkit.getWorld(worldName),
                    dataInput.readDouble(),
                    dataInput.readDouble(),
                    dataInput.readDouble(),
                    dataInput.readFloat(),
                    dataInput.readFloat()
            );
            return new DecodedSnapshot(contents, armor, gameMode, location, 20.0, 20, 5.0f, 0.0f, 0.0f, 0, 0, new PotionEffect[0]);
        } catch (IOException | ClassNotFoundException exception) {
            return null;
        }
    }

    public static final class DecodedSnapshot {
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final GameMode gameMode;
        private final Location location;
        private final double health;
        private final int foodLevel;
        private final float saturation;
        private final float exhaustion;
        private final float exp;
        private final int level;
        private final int totalExperience;
        private final PotionEffect[] potionEffects;

        private DecodedSnapshot(
                ItemStack[] contents,
                ItemStack[] armor,
                GameMode gameMode,
                Location location,
                double health,
                int foodLevel,
                float saturation,
                float exhaustion,
                float exp,
                int level,
                int totalExperience,
                PotionEffect[] potionEffects
        ) {
            this.contents = contents;
            this.armor = armor;
            this.gameMode = gameMode;
            this.location = location;
            this.health = health;
            this.foodLevel = foodLevel;
            this.saturation = saturation;
            this.exhaustion = exhaustion;
            this.exp = exp;
            this.level = level;
            this.totalExperience = totalExperience;
            this.potionEffects = potionEffects;
        }

        public ItemStack[] getContents() {
            return contents;
        }

        public ItemStack[] getArmor() {
            return armor;
        }

        public GameMode getGameMode() {
            return gameMode;
        }

        public Location getLocation() {
            return location;
        }

        public double getHealth() {
            return health;
        }

        public int getFoodLevel() {
            return foodLevel;
        }

        public float getSaturation() {
            return saturation;
        }

        public float getExhaustion() {
            return exhaustion;
        }

        public float getExp() {
            return exp;
        }

        public int getLevel() {
            return level;
        }

        public int getTotalExperience() {
            return totalExperience;
        }

        public PotionEffect[] getPotionEffects() {
            return potionEffects;
        }
    }
}
