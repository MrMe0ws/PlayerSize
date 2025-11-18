package com.meows.playersize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class PlayerSizePlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private Config config;
    private Map<UUID, Double> playerSizes; // UUID -> размер
    private Map<UUID, String> playerNames; // UUID -> ник (для удобства редактирования)
    private File dataFile;
    private Gson gson;
    private Random random;

    @Override
    public void onEnable() {
        // Инициализация
        gson = new GsonBuilder().setPrettyPrinting().create();
        random = new Random();
        playerSizes = new HashMap<>();
        playerNames = new HashMap<>();

        // Загрузка конфигурации
        loadConfig();

        // Загрузка данных игроков
        dataFile = new File(getDataFolder(), "player_sizes.json");
        loadPlayerSizes();

        // Регистрация событий
        getServer().getPluginManager().registerEvents(this, this);

        // Регистрация команды
        getCommand("playersize").setExecutor(this);
        getCommand("playersize").setTabCompleter(this);

        getLogger().info("Плагин PlayerSize успешно загружен!");
    }

    @Override
    public void onDisable() {
        // Сохранение данных при выключении
        savePlayerSizes();
        getLogger().info("Плагин PlayerSize выключен!");
    }

    private void loadConfig() {
        // Создаем папку если её нет
        getDataFolder().mkdirs();

        // Загружаем или создаем конфиг
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            // Копируем дефолтный конфиг
            saveResource("config.yml", false);
        }

        // Загружаем YAML конфигурацию
        FileConfiguration yamlConfig = YamlConfiguration.loadConfiguration(configFile);

        // Инициализируем конфиг
        config = new Config();

        // Загружаем значения из YAML (getDouble/getBoolean вернет значение по
        // умолчанию, если ключ отсутствует)
        config.minSize = yamlConfig.getDouble("sizes.min-size", 0.75);
        config.maxSize = yamlConfig.getDouble("sizes.max-size", 0.9);
        config.defaultSize = yamlConfig.getDouble("sizes.default-size", 0.83);
        config.useRandomSize = yamlConfig.getBoolean("sizes.use-random-size", true);

        // Валидация конфига (разрешаем размеры от 0.1 до 2.0, включая 1.0 для
        // нормального размера)
        if (config.minSize < 0.1 || config.maxSize < 0.1 || config.defaultSize < 0.1) {
            getLogger().warning(
                    "Некорректные значения размера в конфиге (минимум 0.1)! Используются значения по умолчанию.");
            config = new Config();
        }

        if (config.minSize > 2.0 || config.maxSize > 2.0 || config.defaultSize > 2.0) {
            getLogger().warning(
                    "Некорректные значения размера в конфиге (максимум 2.0)! Используются значения по умолчанию.");
            config = new Config();
        }

        if (config.minSize > config.maxSize) {
            getLogger().warning("min-size больше max-size! Меняю местами.");
            double temp = config.minSize;
            config.minSize = config.maxSize;
            config.maxSize = temp;
        }

        // Проверяем, что defaultSize находится в допустимом диапазоне
        if (config.defaultSize < config.minSize || config.defaultSize > config.maxSize) {
            getLogger()
                    .warning("default-size находится вне диапазона min-size..max-size! Устанавливаю среднее значение.");
            config.defaultSize = (config.minSize + config.maxSize) / 2.0;
        }

        getLogger().info("Конфигурация загружена: minSize=" + config.minSize + ", maxSize=" + config.maxSize
                + ", defaultSize=" + config.defaultSize + ", useRandomSize=" + config.useRandomSize);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Проверяем, есть ли уже сохраненный размер для игрока
        Double savedSize = playerSizes.get(uuid);

        if (savedSize == null) {
            // Генерируем новый размер
            if (config.useRandomSize) {
                double newSize = config.minSize + (config.maxSize - config.minSize) * random.nextDouble();
                // Округляем до 2 знаков после запятой
                savedSize = Math.round(newSize * 100.0) / 100.0;
            } else {
                savedSize = config.defaultSize;
            }

            // Сохраняем размер и ник
            playerSizes.put(uuid, savedSize);
            playerNames.put(uuid, player.getName());
            savePlayerSizes();

            getLogger().info("Новый размер для игрока " + player.getName() + ": " + savedSize);
        }

        // Обновляем ник игрока (на случай если он изменился)
        playerNames.put(uuid, player.getName());

        // Создаем финальную переменную для использования во внутреннем классе
        final Double finalSize = savedSize;

        // Применяем размер игроку (с небольшой задержкой для надежности)
        new BukkitRunnable() {
            @Override
            public void run() {
                applySize(player, finalSize);
            }
        }.runTaskLater(this, 5L); // Задержка 5 тиков (0.25 секунды)
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        final Double savedSize = playerSizes.get(uuid);
        if (savedSize != null) {
            // Применяем размер после респавна
            new BukkitRunnable() {
                @Override
                public void run() {
                    applySize(player, savedSize);
                }
            }.runTaskLater(this, 5L);
        }
    }

    private void applySize(Player player, double size) {
        try {
            AttributeInstance scaleAttribute = player.getAttribute(Attribute.GENERIC_SCALE);
            if (scaleAttribute != null) {
                scaleAttribute.setBaseValue(size);
                getLogger().info("Размер игрока " + player.getName() + " установлен на " + size);
            } else {
                getLogger().warning("Не удалось получить атрибут SCALE для игрока " + player.getName());
            }
        } catch (Exception e) {
            getLogger().severe("Ошибка при установке размера для игрока " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadPlayerSizes() {
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

                            // Поддерживаем новый формат (объект с size и name)
                            if (sizeEntry != null) {
                                if (sizeEntry.size != null) {
                                    playerSizes.put(uuid, sizeEntry.size);
                                }

                                if (sizeEntry.name != null && !sizeEntry.name.isEmpty()) {
                                    playerNames.put(uuid, sizeEntry.name);
                                }
                            }
                        } catch (IllegalArgumentException e) {
                            getLogger().warning("Некорректный UUID в данных: " + entry.getKey());
                        }
                    }
                    getLogger().info("Загружено размеров игроков: " + playerSizes.size());
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
                        getLogger().warning("Некорректный UUID в данных: " + entry.getKey());
                    }
                }
                getLogger().info("Загружено размеров игроков (старый формат): " + playerSizes.size());
            }
        } catch (IOException e) {
            getLogger().warning("Ошибка при загрузке данных игроков: " + e.getMessage());
        }
    }

    private void savePlayerSizes() {
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

            getLogger().info("Данные игроков сохранены: " + playerSizes.size() + " записей");
        } catch (IOException e) {
            getLogger().severe("Ошибка при сохранении данных игроков: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                return handleReload(sender);
            case "set":
                return handleSet(sender, args);
            case "reset":
                return handleReset(sender, args);
            case "check":
                return handleCheck(sender, args);
            case "list":
                return handleList(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6========== [PlayerSize] ==========");
        sender.sendMessage("§e/playersize reload §7- Перезагрузить конфиг (только админы)");
        sender.sendMessage("§e/playersize set <игрок> <размер> §7- Установить размер (только админы)");
        sender.sendMessage("§e/playersize reset <игрок> §7- Сбросить размер (только админы)");
        sender.sendMessage("§e/playersize check <игрок> §7- Показать размер игрока");
        sender.sendMessage("§e/playersize list §7- Список всех игроков с размерами");
        sender.sendMessage("§6================================");
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("playersize.admin") && !sender.isOp()) {
            sender.sendMessage("§cУ вас нет прав для использования этой команды!");
            return true;
        }

        try {
            loadConfig();
            sender.sendMessage("§a[PlayerSize] Конфигурация успешно перезагружена!");
            getLogger().info("Конфигурация перезагружена администратором: " + sender.getName());
        } catch (Exception e) {
            sender.sendMessage("§c[PlayerSize] Ошибка при перезагрузке конфигурации: " + e.getMessage());
            getLogger().severe("Ошибка при перезагрузке конфигурации: " + e.getMessage());
            e.printStackTrace();
        }
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playersize.admin") && !sender.isOp()) {
            sender.sendMessage("§cУ вас нет прав для использования этой команды!");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§c[PlayerSize] Использование: §e/playersize set <игрок> <размер>");
            sender.sendMessage("§7Размер должен быть от 0.1 до 2.0 (1.0 = нормальный размер)");
            return true;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            // Пытаемся найти по UUID или нику из сохраненных данных
            UUID targetUuid = findPlayerUUID(targetName);
            if (targetUuid == null) {
                sender.sendMessage("§c[PlayerSize] Игрок §e" + targetName + " §cне найден!");
                return true;
            }
            // Игрок оффлайн, но есть в базе
            return setOfflinePlayerSize(sender, targetUuid, targetName, args[2]);
        }

        try {
            double size = Double.parseDouble(args[2]);

            // Валидация размера
            if (size < 0.1 || size > 2.0) {
                sender.sendMessage("§c[PlayerSize] Размер должен быть от 0.1 до 2.0!");
                return true;
            }

            UUID uuid = target.getUniqueId();
            playerSizes.put(uuid, size);
            playerNames.put(uuid, target.getName());
            savePlayerSizes();

            // Применяем размер
            applySize(target, size);

            sender.sendMessage("§a[PlayerSize] Размер игрока §e" + target.getName() + " §aустановлен на §e" + size);
            target.sendMessage(
                    "§a[PlayerSize] Ваш размер изменен на §e" + size + " §aадминистратором §e" + sender.getName());
            getLogger().info("Размер игрока " + target.getName() + " установлен на " + size + " администратором "
                    + sender.getName());

        } catch (NumberFormatException e) {
            sender.sendMessage("§c[PlayerSize] Некорректное значение размера: §e" + args[2]);
        }
        return true;
    }

    private boolean setOfflinePlayerSize(CommandSender sender, UUID uuid, String playerName, String sizeStr) {
        try {
            double size = Double.parseDouble(sizeStr);

            if (size < 0.1 || size > 2.0) {
                sender.sendMessage("§c[PlayerSize] Размер должен быть от 0.1 до 2.0!");
                return true;
            }

            playerSizes.put(uuid, size);
            playerNames.put(uuid, playerName);
            savePlayerSizes();

            sender.sendMessage("§a[PlayerSize] Размер игрока §e" + playerName + " §aустановлен на §e" + size);
            sender.sendMessage("§7Размер будет применен при следующем входе игрока на сервер.");
            getLogger().info("Размер оффлайн игрока " + playerName + " установлен на " + size + " администратором "
                    + sender.getName());

        } catch (NumberFormatException e) {
            sender.sendMessage("§c[PlayerSize] Некорректное значение размера: §e" + sizeStr);
        }
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playersize.admin") && !sender.isOp()) {
            sender.sendMessage("§cУ вас нет прав для использования этой команды!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§c[PlayerSize] Использование: §e/playersize reset <игрок>");
            return true;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            UUID targetUuid = findPlayerUUID(targetName);
            if (targetUuid == null) {
                sender.sendMessage("§c[PlayerSize] Игрок §e" + targetName + " §cне найден!");
                return true;
            }
            return resetOfflinePlayerSize(sender, targetUuid, targetName);
        }

        UUID uuid = target.getUniqueId();

        // Генерируем новый размер или используем дефолтный
        double newSize;
        if (config.useRandomSize) {
            newSize = config.minSize + (config.maxSize - config.minSize) * random.nextDouble();
            newSize = Math.round(newSize * 100.0) / 100.0;
        } else {
            newSize = config.defaultSize;
        }

        playerSizes.put(uuid, newSize);
        playerNames.put(uuid, target.getName());
        savePlayerSizes();

        // Применяем новый размер
        applySize(target, newSize);

        sender.sendMessage(
                "§a[PlayerSize] Размер игрока §e" + target.getName() + " §aсброшен и установлен на §e" + newSize);
        target.sendMessage("§a[PlayerSize] Ваш размер был сброшен и установлен на §e" + newSize);
        getLogger().info("Размер игрока " + target.getName() + " сброшен и установлен на " + newSize
                + " администратором " + sender.getName());

        return true;
    }

    private boolean resetOfflinePlayerSize(CommandSender sender, UUID uuid, String playerName) {
        double newSize;
        if (config.useRandomSize) {
            newSize = config.minSize + (config.maxSize - config.minSize) * random.nextDouble();
            newSize = Math.round(newSize * 100.0) / 100.0;
        } else {
            newSize = config.defaultSize;
        }

        playerSizes.put(uuid, newSize);
        playerNames.put(uuid, playerName);
        savePlayerSizes();

        sender.sendMessage("§a[PlayerSize] Размер игрока §e" + playerName + " §aсброшен и установлен на §e" + newSize);
        sender.sendMessage("§7Размер будет применен при следующем входе игрока на сервер.");
        getLogger().info("Размер оффлайн игрока " + playerName + " сброшен и установлен на " + newSize
                + " администратором " + sender.getName());

        return true;
    }

    private boolean handleCheck(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playersize.check")) {
            sender.sendMessage("§cУ вас нет прав для использования этой команды!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§c[PlayerSize] Использование: §e/playersize check <игрок>");
            return true;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);

        if (target != null) {
            UUID uuid = target.getUniqueId();
            Double size = playerSizes.get(uuid);

            if (size != null) {
                sender.sendMessage("§6[PlayerSize] §7Игрок: §e" + target.getName());
                sender.sendMessage("§6[PlayerSize] §7Размер: §e" + size);
                sender.sendMessage("§6[PlayerSize] §7Рост: §e~" + String.format("%.2f", size * 1.8) + " блока");
            } else {
                sender.sendMessage("§c[PlayerSize] У игрока §e" + target.getName() + " §cеще не установлен размер.");
            }
        } else {
            UUID targetUuid = findPlayerUUID(targetName);
            if (targetUuid == null) {
                sender.sendMessage("§c[PlayerSize] Игрок §e" + targetName + " §cне найден!");
                return true;
            }

            Double size = playerSizes.get(targetUuid);
            String name = playerNames.get(targetUuid);

            if (size != null) {
                sender.sendMessage("§6[PlayerSize] §7Игрок: §e" + (name != null ? name : targetName));
                sender.sendMessage("§6[PlayerSize] §7Размер: §e" + size);
                sender.sendMessage("§6[PlayerSize] §7Рост: §e~" + String.format("%.2f", size * 1.8) + " блока");
                sender.sendMessage("§7(Игрок оффлайн)");
            } else {
                sender.sendMessage("§c[PlayerSize] У игрока §e" + targetName + " §cеще не установлен размер.");
            }
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("playersize.list")) {
            sender.sendMessage("§cУ вас нет прав для использования этой команды!");
            return true;
        }

        if (playerSizes.isEmpty()) {
            sender.sendMessage("§c[PlayerSize] Нет сохраненных размеров игроков.");
            return true;
        }

        sender.sendMessage("§6========== [PlayerSize] Список игроков ==========");
        int count = 0;
        for (Map.Entry<UUID, Double> entry : playerSizes.entrySet()) {
            UUID uuid = entry.getKey();
            Double size = entry.getValue();
            String name = playerNames.get(uuid);

            // Проверяем, онлайн ли игрок
            Player onlinePlayer = Bukkit.getPlayer(uuid);
            String status = onlinePlayer != null ? "§a[Онлайн]" : "§7[Оффлайн]";

            sender.sendMessage(
                    "§e" + (name != null ? name : uuid.toString()) + " §7- Размер: §e" + size + " §7" + status);
            count++;

            // Ограничиваем вывод до 20 записей за раз
            if (count >= 20) {
                sender.sendMessage("§7... и еще " + (playerSizes.size() - count) + " игроков");
                break;
            }
        }
        sender.sendMessage("§6===============================================");
        sender.sendMessage("§7Всего игроков: §e" + playerSizes.size());

        return true;
    }

    private UUID findPlayerUUID(String name) {
        // Сначала ищем по нику в сохраненных данных
        for (Map.Entry<UUID, String> entry : playerNames.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }

        // Пытаемся найти онлайн игрока
        Player player = Bukkit.getPlayer(name);
        if (player != null) {
            return player.getUniqueId();
        }

        return null;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<>();

        if (args.length == 1) {
            // Подкоманды
            completions.add("reload");
            completions.add("set");
            completions.add("reset");
            completions.add("check");
            completions.add("list");
        } else if (args.length == 2) {
            // Имена игроков для set, reset, check
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("set") || subCommand.equals("reset") || subCommand.equals("check")) {
                // Онлайн игроки
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
                // Оффлайн игроки из базы
                for (String name : playerNames.values()) {
                    if (name.toLowerCase().startsWith(args[1].toLowerCase()) && !completions.contains(name)) {
                        completions.add(name);
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            // Подсказки для размера
            completions.add("0.5");
            completions.add("0.75");
            completions.add("0.83");
            completions.add("0.9");
            completions.add("1.0");
        }

        return completions;
    }

    // Класс для хранения данных игрока в JSON
    private static class PlayerSizeEntry {
        public Double size; // Размер игрока (может быть от 0.1 до 2.0, включая 1.0 для нормального размера)
        public String name; // Ник игрока (для удобства редактирования вручную)
    }

    // Класс для конфигурации
    public static class Config {
        // Минимальный размер (1.0 = нормальный размер игрока ~1.8 блока)
        // 0.75 = ~1.35 блока высоты (низкий гном)
        public double minSize = 0.75;

        // Максимальный размер
        // 0.9 = ~1.62 блока высоты (высокий гном)
        public double maxSize = 0.9;

        // Фиксированный размер, если useRandomSize = false
        // 0.83 = ~1.5 блока высоты (средний рост гнома)
        public double defaultSize = 0.83;

        // true = случайный размер от minSize до maxSize (для реализма)
        // false = фиксированный defaultSize (все одинакового роста)
        public boolean useRandomSize = true;
    }
}
