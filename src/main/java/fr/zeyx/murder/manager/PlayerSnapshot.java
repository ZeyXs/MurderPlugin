package fr.zeyx.murder.manager;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public class PlayerSnapshot {

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

    public PlayerSnapshot(
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
