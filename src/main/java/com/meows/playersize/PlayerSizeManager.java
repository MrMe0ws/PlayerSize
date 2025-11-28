package com.meows.playersize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerSizeManager {

    private final JavaPlugin plugin;
    private final Gson gson;
    private Map<UUID, Double> playerSizes;
    private Map<UUID, String> playerNames;
    private File dataFile;

    public PlayerSizeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.playerSizes = new HashMap<>();
        this.playerNames = new HashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "player_sizes.json");
    }

    public void loadPlayerSizes() {
        if (!dataFile.exists()) {
            return;
        }

        try {
            String json = new String(Files.readAllBytes(dataFile.toPath()));

            // Пытаемся загрузить новую структуру (с объектами)
            try {
                Type type = new TypeToken<Map<String, PlayerSizeEntry>>() {
                }.getType();
                Map<String, PlayerSizeEntry> entryMap = gson.fromJson(json, type);

                if (entryMap != null && !entryMap.isEmpty()) {
                    playerSizes = new HashMap<>();
                    playerNames = new HashMap<>();

                    for (Map.Entry<String, PlayerSizeEntry> entry : entryMap.entrySet()) {
                        try {
                            UUID uuid = UUID.fromString(entry.getKey());
                            PlayerSizeEntry sizeEntry = entry.getValue();

                            if (sizeEntry != null) {
                                if (sizeEntry.size != null) {
                                    playerSizes.put(uuid, sizeEntry.size);
                                }

                                if (sizeEntry.name != null && !sizeEntry.name.isEmpty()) {
                                    playerNames.put(uuid, sizeEntry.name);
                                }
                            }
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Некорректный UUID в данных: " + entry.getKey());
                        }
                    }
                    plugin.getLogger().info("Загружено размеров игроков: " + playerSizes.size());
                    return;
                }
            } catch (Exception e) {
                // Если не получилось загрузить новую структуру, пробуем старую
            }

            // Загрузка старого формата (просто Map<String, Double>)
            Type type = new TypeToken<Map<String, Double>>() {
            }.getType();
            Map<String, Double> stringMap = gson.fromJson(json, type);

            if (stringMap != null) {
                playerSizes = new HashMap<>();
                playerNames = new HashMap<>();
                for (Map.Entry<String, Double> entry : stringMap.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        playerSizes.put(uuid, entry.getValue());
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Некорректный UUID в данных: " + entry.getKey());
                    }
                }
                plugin.getLogger().info("Загружено размеров игроков (старый формат): " + playerSizes.size());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Ошибка при загрузке данных игроков: " + e.getMessage());
        }
    }

    public void savePlayerSizes() {
        try {
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }

            // Конвертируем в новую структуру с никами
            Map<String, PlayerSizeEntry> entryMap = new HashMap<>();
            for (Map.Entry<UUID, Double> entry : playerSizes.entrySet()) {
                UUID uuid = entry.getKey();
                Double size = entry.getValue();
                String name = playerNames.get(uuid);

                PlayerSizeEntry sizeEntry = new PlayerSizeEntry();
                sizeEntry.size = size;
                sizeEntry.name = name != null ? name : "Unknown";

                entryMap.put(uuid.toString(), sizeEntry);
            }

            String json = gson.toJson(entryMap);
            Files.write(dataFile.toPath(), json.getBytes());

            plugin.getLogger().info("Данные игроков сохранены: " + playerSizes.size() + " записей");
        } catch (IOException e) {
            plugin.getLogger().severe("Ошибка при сохранении данных игроков: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Double getPlayerSize(UUID uuid) {
        return playerSizes.get(uuid);
    }

    public void setPlayerSize(UUID uuid, double size) {
        playerSizes.put(uuid, size);
    }

    public String getPlayerName(UUID uuid) {
        return playerNames.get(uuid);
    }

    public void setPlayerName(UUID uuid, String name) {
        playerNames.put(uuid, name);
    }

    public Map<UUID, Double> getAllPlayerSizes() {
        return new HashMap<>(playerSizes);
    }

    public Map<UUID, String> getAllPlayerNames() {
        return new HashMap<>(playerNames);
    }

    public void applySize(Player player, double size) {
        try {
            AttributeInstance scaleAttribute = player.getAttribute(Attribute.GENERIC_SCALE);
            if (scaleAttribute != null) {
                scaleAttribute.setBaseValue(size);
                plugin.getLogger().info("Размер игрока " + player.getName() + " установлен на " + size);
            } else {
                plugin.getLogger().warning("Не удалось получить атрибут SCALE для игрока " + player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger()
                    .severe("Ошибка при установке размера для игрока " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void applyHealth(Player player, double size) {
        ConfigManager config = ((PlayerSizePlugin) plugin).getConfigManager();

        if (!config.isReduceHealthForSmallPlayers()) {
            return;
        }

        try {
            AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealthAttribute == null) {
                plugin.getLogger().warning("Не удалось получить атрибут MAX_HEALTH для игрока " + player.getName());
                return;
            }

            // Если размер игрока <= порога, уменьшаем здоровье
            if (size <= config.getSmallSizeThreshold()) {
                // Стандартное здоровье = 20 HP (10 сердец)
                // Отнимаем указанное количество сердец
                double newMaxHealth = 20.0 - (config.getHealthReduction() * 2.0); // 1 сердце = 2 HP

                maxHealthAttribute.setBaseValue(newMaxHealth);

                // Устанавливаем текущее здоровье, если оно больше нового максимума
                if (player.getHealth() > newMaxHealth) {
                    player.setHealth(newMaxHealth);
                }

                plugin.getLogger().info("Здоровье игрока " + player.getName() + " уменьшено до " + newMaxHealth
                        + " HP (размер: " + size + " <= " + config.getSmallSizeThreshold() + ")");
            } else {
                // Восстанавливаем стандартное здоровье, если размер больше порога
                maxHealthAttribute.setBaseValue(20.0);
                if (player.getHealth() > 20.0) {
                    player.setHealth(20.0);
                }
            }
        } catch (Exception e) {
            plugin.getLogger()
                    .severe("Ошибка при установке здоровья для игрока " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Класс для хранения данных игрока в JSON
    private static class PlayerSizeEntry {
        public Double size;
        public String name;
    }
}
