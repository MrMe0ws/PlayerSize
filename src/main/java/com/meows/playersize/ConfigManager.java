package com.meows.playersize;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    // Настройки размеров
    private double minSize = 0.75;
    private double maxSize = 0.9;
    private double defaultSize = 0.83;
    private boolean useRandomSize = true;

    // Настройки здоровья
    private boolean reduceHealthForSmallPlayers = true;
    private double smallSizeThreshold = 0.666;
    private int healthReduction = 2;

    // Настройки зелья
    private boolean potionEnabled = true;
    private boolean potionApplyOnMobs = true;
    private boolean potionDebugCrafting = false;
    private List<String> potionRecipe;
    private String potionName = "§6§lЗелье Изменения Роста";
    private List<String> potionLore;
    private int potionColorRed = 255;
    private int potionColorGreen = 165;
    private int potionColorBlue = 0;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.getDataFolder().mkdirs();
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Загружаем настройки размеров
        minSize = config.getDouble("sizes.min-size", 0.75);
        maxSize = config.getDouble("sizes.max-size", 0.9);
        defaultSize = config.getDouble("sizes.default-size", 0.83);
        useRandomSize = config.getBoolean("sizes.use-random-size", true);

        // Загружаем настройки здоровья
        reduceHealthForSmallPlayers = config.getBoolean("health.reduce-health-for-small-players", true);
        smallSizeThreshold = config.getDouble("health.small-size-threshold", 0.666);
        healthReduction = config.getInt("health.health-reduction", 2);

        // Загружаем настройки зелья
        potionEnabled = config.getBoolean("potion.enabled", true);
        potionApplyOnMobs = config.getBoolean("potion.apply-on-mobs", true);
        potionDebugCrafting = config.getBoolean("potion.debug-crafting", false);
        potionRecipe = config.getStringList("potion.recipe");
        if (potionRecipe.isEmpty()) {
            // Дефолтный рецепт
            potionRecipe = List.of(
                    "NETHER_STAR", "BROWN_MUSHROOM", "POTION",
                    "ENDER_EYE", "DRAGON_BREATH", "PHANTOM_MEMBRANE",
                    "SPIDER_EYE", "DIAMOND", "ENCHANTED_GOLDEN_APPLE");
        }
        potionName = config.getString("potion.name", "§6§lЗелье Изменения Роста");
        potionLore = config.getStringList("potion.lore");
        if (potionLore.isEmpty()) {
            potionLore = List.of(
                    "§7Используйте это зелье, чтобы",
                    "§7случайно изменить свой рост!",
                    "",
                    "§eВаш размер будет перегенерирован");
        }
        potionColorRed = config.getInt("potion.color.red", 255);
        potionColorGreen = config.getInt("potion.color.green", 165);
        potionColorBlue = config.getInt("potion.color.blue", 0);

        // Валидация
        validateConfig();
    }

    private void validateConfig() {
        if (minSize < 0.1 || maxSize < 0.1 || defaultSize < 0.1) {
            plugin.getLogger().warning(
                    "Некорректные значения размера в конфиге (минимум 0.1)! Используются значения по умолчанию.");
            minSize = 0.75;
            maxSize = 0.9;
            defaultSize = 0.83;
        }

        if (minSize > 5.0 || maxSize > 5.0 || defaultSize > 5.0) {
            plugin.getLogger().warning(
                    "Некорректные значения размера в конфиге (максимум 5.0)! Используются значения по умолчанию.");
            minSize = 0.75;
            maxSize = 0.9;
            defaultSize = 0.83;
        }

        if (minSize > maxSize) {
            plugin.getLogger().warning("min-size больше max-size! Меняю местами.");
            double temp = minSize;
            minSize = maxSize;
            maxSize = temp;
        }

        if (defaultSize < minSize || defaultSize > maxSize) {
            plugin.getLogger()
                    .warning("default-size находится вне диапазона min-size..max-size! Устанавливаю среднее значение.");
            defaultSize = (minSize + maxSize) / 2.0;
        }
    }

    // Getters для размеров
    public double getMinSize() {
        return minSize;
    }

    public double getMaxSize() {
        return maxSize;
    }

    public double getDefaultSize() {
        return defaultSize;
    }

    public boolean isUseRandomSize() {
        return useRandomSize;
    }

    // Getters для здоровья
    public boolean isReduceHealthForSmallPlayers() {
        return reduceHealthForSmallPlayers;
    }

    public double getSmallSizeThreshold() {
        return smallSizeThreshold;
    }

    public int getHealthReduction() {
        return healthReduction;
    }

    // Getters для зелья
    public boolean isPotionEnabled() {
        return potionEnabled;
    }

    public boolean isPotionApplyOnMobs() {
        return potionApplyOnMobs;
    }

    public boolean isPotionDebugCrafting() {
        return potionDebugCrafting;
    }

    public List<String> getPotionRecipe() {
        return potionRecipe;
    }

    public String getPotionName() {
        return potionName;
    }

    public List<String> getPotionLore() {
        return potionLore;
    }

    public int getPotionColorRed() {
        return potionColorRed;
    }

    public int getPotionColorGreen() {
        return potionColorGreen;
    }

    public int getPotionColorBlue() {
        return potionColorBlue;
    }
}
