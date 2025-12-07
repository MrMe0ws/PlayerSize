package com.meows.playersize;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class PlayerSizePlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private ConfigManager configManager;
    private PlayerSizeManager playerSizeManager;
    private SizePotionManager potionManager;
    private CraftManager craftManager;
    private Random random;

    @Override
    public void onEnable() {
        // Инициализация
        random = new Random();

        // Инициализация менеджеров
        configManager = new ConfigManager(this);
        playerSizeManager = new PlayerSizeManager(this);
        potionManager = new SizePotionManager(this);
        craftManager = new CraftManager(this);

        // Загрузка данных игроков
        playerSizeManager.loadPlayerSizes();

        // Регистрация событий
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(potionManager, this);
        getServer().getPluginManager().registerEvents(craftManager, this);

        // Регистрация команды
        getCommand("playersize").setExecutor(this);
        getCommand("playersize").setTabCompleter(this);

        getLogger().info("Плагин PlayerSize успешно загружен!");
    }

    @Override
    public void onDisable() {
        // Сохранение данных при выключении
        if (playerSizeManager != null) {
            playerSizeManager.savePlayerSizes();
        }
        getLogger().info("Плагин PlayerSize выключен!");
    }

    // Getters для доступа к менеджерам
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerSizeManager getPlayerSizeManager() {
        return playerSizeManager;
    }

    public SizePotionManager getPotionManager() {
        return potionManager;
    }

    public Random getRandom() {
        return random;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Проверяем, есть ли уже сохраненный размер для игрока
        Double savedSize = playerSizeManager.getPlayerSize(uuid);

        if (savedSize == null) {
            // Генерируем новый размер
            if (configManager.isUseRandomSize()) {
                double newSize = configManager.getMinSize()
                        + (configManager.getMaxSize() - configManager.getMinSize()) * random.nextDouble();
                // Округляем до 2 знаков после запятой
                savedSize = Math.round(newSize * 100.0) / 100.0;
            } else {
                savedSize = configManager.getDefaultSize();
            }

            // Сохраняем размер и ник
            playerSizeManager.setPlayerSize(uuid, savedSize);
            playerSizeManager.setPlayerName(uuid, player.getName());
            playerSizeManager.savePlayerSizes();

            getLogger().info("Новый размер для игрока " + player.getName() + ": " + savedSize);
        }

        // Обновляем ник игрока (на случай если он изменился)
        playerSizeManager.setPlayerName(uuid, player.getName());

        // Создаем финальную переменную для использования во внутреннем классе
        final Double finalSize = savedSize;

        // Применяем размер игроку (с небольшой задержкой для надежности)
        new BukkitRunnable() {
            @Override
            public void run() {
                playerSizeManager.applySize(player, finalSize);
                playerSizeManager.applyHealth(player, finalSize);
            }
        }.runTaskLater(this, 5L); // Задержка 5 тиков (0.25 секунды)
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        final Double savedSize = playerSizeManager.getPlayerSize(uuid);
        if (savedSize != null) {
            // Применяем размер после респавна
            new BukkitRunnable() {
                @Override
                public void run() {
                    playerSizeManager.applySize(player, savedSize);
                    playerSizeManager.applyHealth(player, savedSize);
                }
            }.runTaskLater(this, 5L);
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
                return handleList(sender, args);
            case "give":
                return handleGive(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6========== [PlayerSize] ==========");
        sender.sendMessage("§e/playersize reload §7- Перезагрузить конфиг (только админы)");
        sender.sendMessage("§e/playersize set <игрок> <размер> §7- Установить размер (только админы)");
        sender.sendMessage("§e/playersize reset <игрок|all> §7- Сбросить размер (только админы)");
        sender.sendMessage("§e/playersize check <игрок> §7- Показать размер игрока");
        sender.sendMessage("§e/playersize list [страница] §7- Список игроков по росту");
        sender.sendMessage("§e/playersize give potion <количество> §7- Выдать зелье роста (только админы)");
        sender.sendMessage("§6================================");
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("playersize.admin") && !sender.isOp()) {
            sender.sendMessage("§cУ вас нет прав для использования этой команды!");
            return true;
        }

        try {
            configManager.loadConfig();
            craftManager.loadRecipe(); // Перезагружаем рецепт зелья
            // Применяем здоровье всем онлайн игрокам после перезагрузки конфига
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                Double size = playerSizeManager.getPlayerSize(onlinePlayer.getUniqueId());
                if (size != null) {
                    playerSizeManager.applyHealth(onlinePlayer, size);
                }
            }
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

            // Валидация размера (максимум в Minecraft = 5.0)
            if (size < 0.1 || size > 5.0) {
                sender.sendMessage("§c[PlayerSize] Размер должен быть от 0.1 до 5.0!");
                return true;
            }

            UUID uuid = target.getUniqueId();
            playerSizeManager.setPlayerSize(uuid, size);
            playerSizeManager.setPlayerName(uuid, target.getName());
            playerSizeManager.savePlayerSizes();

            // Применяем размер и здоровье
            playerSizeManager.applySize(target, size);
            playerSizeManager.applyHealth(target, size);

            // Визуальные эффекты
            potionManager.spawnPotionEffects(target);

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

            if (size < 0.1 || size > 5.0) {
                sender.sendMessage("§c[PlayerSize] Размер должен быть от 0.1 до 5.0!");
                return true;
            }

            playerSizeManager.setPlayerSize(uuid, size);
            playerSizeManager.setPlayerName(uuid, playerName);
            playerSizeManager.savePlayerSizes();

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
            sender.sendMessage("§c[PlayerSize] Использование: §e/playersize reset <игрок|all>");
            return true;
        }

        String targetName = args[1];

        // Обработка команды reset all
        if (targetName.equalsIgnoreCase("all")) {
            return resetAllPlayers(sender);
        }

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
        if (configManager.isUseRandomSize()) {
            newSize = configManager.getMinSize()
                    + (configManager.getMaxSize() - configManager.getMinSize()) * random.nextDouble();
            newSize = Math.round(newSize * 100.0) / 100.0;
        } else {
            newSize = configManager.getDefaultSize();
        }

        playerSizeManager.setPlayerSize(uuid, newSize);
        playerSizeManager.setPlayerName(uuid, target.getName());
        playerSizeManager.savePlayerSizes();

        // Применяем новый размер и здоровье
        playerSizeManager.applySize(target, newSize);
        playerSizeManager.applyHealth(target, newSize);

        // Визуальные эффекты
        potionManager.spawnPotionEffects(target);

        sender.sendMessage(
                "§a[PlayerSize] Размер игрока §e" + target.getName() + " §aсброшен и установлен на §e" + newSize);
        target.sendMessage("§a[PlayerSize] Ваш размер был сброшен и установлен на §e" + newSize);
        getLogger().info("Размер игрока " + target.getName() + " сброшен и установлен на " + newSize
                + " администратором " + sender.getName());

        return true;
    }

    private boolean resetAllPlayers(CommandSender sender) {
        Map<UUID, Double> allSizes = playerSizeManager.getAllPlayerSizes();
        if (allSizes.isEmpty()) {
            sender.sendMessage("§c[PlayerSize] Нет сохраненных размеров игроков для сброса.");
            return true;
        }

        int onlineCount = 0;

        // Создаем копию списка UUID для безопасной итерации
        java.util.List<UUID> uuids = new java.util.ArrayList<>(allSizes.keySet());

        for (UUID uuid : uuids) {
            // Генерируем новый размер или используем дефолтный
            double newSize;
            if (configManager.isUseRandomSize()) {
                newSize = configManager.getMinSize()
                        + (configManager.getMaxSize() - configManager.getMinSize()) * random.nextDouble();
                newSize = Math.round(newSize * 100.0) / 100.0;
            } else {
                newSize = configManager.getDefaultSize();
            }

            playerSizeManager.setPlayerSize(uuid, newSize);

            // Обновляем ник, если игрок онлайн
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                playerSizeManager.setPlayerName(uuid, player.getName());
                onlineCount++;

                // Применяем новый размер и здоровье онлайн игрокам
                playerSizeManager.applySize(player, newSize);
                playerSizeManager.applyHealth(player, newSize);
            }
        }

        // Сохраняем изменения
        playerSizeManager.savePlayerSizes();

        sender.sendMessage("§a[PlayerSize] Размеры всех игроков сброшены!");
        sender.sendMessage("§7Всего игроков: §e" + allSizes.size() + " §7(Онлайн: §e" + onlineCount + "§7)");

        // Уведомляем онлайн игроков
        for (UUID uuid : uuids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Double newSize = playerSizeManager.getPlayerSize(uuid);
                if (newSize != null) {
                    player.sendMessage("§a[PlayerSize] Ваш размер был сброшен и установлен на §e" + newSize);
                }
            }
        }

        getLogger().info(
                "Размеры всех игроков сброшены администратором " + sender.getName() + ". Всего: " + allSizes.size());

        return true;
    }

    private boolean resetOfflinePlayerSize(CommandSender sender, UUID uuid, String playerName) {
        double newSize;
        if (configManager.isUseRandomSize()) {
            newSize = configManager.getMinSize()
                    + (configManager.getMaxSize() - configManager.getMinSize()) * random.nextDouble();
            newSize = Math.round(newSize * 100.0) / 100.0;
        } else {
            newSize = configManager.getDefaultSize();
        }

        playerSizeManager.setPlayerSize(uuid, newSize);
        playerSizeManager.setPlayerName(uuid, playerName);
        playerSizeManager.savePlayerSizes();

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
            Double size = playerSizeManager.getPlayerSize(uuid);

            if (size != null) {
                sender.sendMessage("§6[PlayerSize] §7Игрок: §e" + target.getName());
                sender.sendMessage("§6[PlayerSize] §7Рост: §e" + String.format("%.2f", size * 1.8) + " блока");
                sender.sendMessage("§6[PlayerSize] §7Размер: §e" + size);
            } else {
                sender.sendMessage("§c[PlayerSize] У игрока §e" + target.getName() + " §cеще не установлен размер.");
            }
        } else {
            UUID targetUuid = findPlayerUUID(targetName);
            if (targetUuid == null) {
                sender.sendMessage("§c[PlayerSize] Игрок §e" + targetName + " §cне найден!");
                return true;
            }

            Double size = playerSizeManager.getPlayerSize(targetUuid);
            String name = playerSizeManager.getPlayerName(targetUuid);

            if (size != null) {
                sender.sendMessage("§6[PlayerSize] §7Игрок: §e" + (name != null ? name : targetName));
                sender.sendMessage("§6[PlayerSize] §7Рост: §e" + String.format("%.2f", size * 1.8) + " блока");
                sender.sendMessage("§6[PlayerSize] §7Размер: §e" + size);
            } else {
                sender.sendMessage("§c[PlayerSize] У игрока §e" + targetName + " §cеще не установлен размер.");
            }
        }
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playersize.list")) {
            sender.sendMessage("§cУ вас нет прав для использования этой команды!");
            return true;
        }

        Map<UUID, Double> allSizes = playerSizeManager.getAllPlayerSizes();
        if (allSizes.isEmpty()) {
            sender.sendMessage("§c[PlayerSize] Нет сохраненных размеров игроков.");
            return true;
        }

        // Определяем номер страницы
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
                if (page < 1) {
                    page = 1;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§c[PlayerSize] Некорректный номер страницы: §e" + args[1]);
                return true;
            }
        }

        // Создаем список записей с ростом в блоках и сортируем от высокого к низкому
        java.util.List<PlayerListEntry> entries = new java.util.ArrayList<>();
        Map<UUID, String> allNames = playerSizeManager.getAllPlayerNames();
        for (Map.Entry<UUID, Double> entry : allSizes.entrySet()) {
            UUID uuid = entry.getKey();
            Double size = entry.getValue();
            String name = allNames.get(uuid);
            double height = size * 1.8; // Рост в блоках

            Player onlinePlayer = Bukkit.getPlayer(uuid);
            boolean isOnline = onlinePlayer != null;

            entries.add(new PlayerListEntry(
                    name != null ? name : uuid.toString(),
                    size,
                    height,
                    isOnline));
        }

        // Сортируем от высокого к низкому
        entries.sort((a, b) -> Double.compare(b.height, a.height));

        // Настройки пагинации
        int itemsPerPage = 15;
        int totalPages = (int) Math.ceil((double) entries.size() / itemsPerPage);

        if (page > totalPages) {
            sender.sendMessage(
                    "§c[PlayerSize] Страница §e" + page + " §cне существует. Всего страниц: §e" + totalPages);
            return true;
        }

        // Вычисляем диапазон для текущей страницы
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, entries.size());

        // Выводим заголовок
        sender.sendMessage("§6========== [PlayerSize] Список игроков ==========");
        sender.sendMessage(
                "§7Страница §e" + page + " §7из §e" + totalPages + " §7(Всего игроков: §e" + entries.size() + "§7)");
        sender.sendMessage("");

        // Выводим записи текущей страницы
        for (int i = startIndex; i < endIndex; i++) {
            PlayerListEntry entry = entries.get(i);
            String heightStr = String.format("%.2f", entry.height);
            sender.sendMessage("§e" + entry.name + " §7- Рост: §e" + heightStr + " блока");
        }

        sender.sendMessage("");
        sender.sendMessage("§6===============================================");

        // Подсказка о навигации
        if (totalPages > 1) {
            sender.sendMessage("§7Используйте §e/playersize list <номер> §7для перехода на другую страницу");
        }

        return true;
    }

    // Вспомогательный класс для сортировки списка
    private static class PlayerListEntry {
        String name;
        double size;
        double height;
        boolean isOnline;

        PlayerListEntry(String name, double size, double height, boolean isOnline) {
            this.name = name;
            this.size = size;
            this.height = height;
            this.isOnline = isOnline;
        }
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playersize.admin") && !sender.isOp()) {
            sender.sendMessage("§cУ вас нет прав для использования этой команды!");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§c[PlayerSize] Использование: §e/playersize give potion <количество>");
            return true;
        }

        if (!args[1].equalsIgnoreCase("potion")) {
            sender.sendMessage("§c[PlayerSize] Неизвестный предмет: §e" + args[1]);
            sender.sendMessage("§7Доступные предметы: §epotion");
            return true;
        }

        // Определяем целевого игрока
        Player targetPlayer = null;
        int amountIndex = 2;

        // Проверяем, указан ли игрок (args[2] может быть именем игрока или количеством)
        if (args.length >= 4) {
            // Формат: /playersize give potion <игрок> <количество>
            String targetName = args[2];
            targetPlayer = Bukkit.getPlayer(targetName);
            if (targetPlayer == null) {
                sender.sendMessage("§c[PlayerSize] Игрок §e" + targetName + " §cне найден или не в сети!");
                return true;
            }
            amountIndex = 3;
        } else if (sender instanceof Player) {
            // Если игрок не указан, выдаем тому, кто выполняет команду
            targetPlayer = (Player) sender;
        } else {
            // Консоль должна указать игрока
            sender.sendMessage("§c[PlayerSize] Использование: §e/playersize give potion <игрок> <количество>");
            return true;
        }

        // Парсим количество
        int amount;
        try {
            amount = Integer.parseInt(args[amountIndex]);
            if (amount < 1) {
                sender.sendMessage("§c[PlayerSize] Количество должно быть больше 0!");
                return true;
            }
            if (amount > 64) {
                sender.sendMessage("§c[PlayerSize] Максимальное количество: 64!");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§c[PlayerSize] Некорректное количество: §e" + args[amountIndex]);
            return true;
        }

        // Проверяем, включено ли зелье в конфиге
        if (!configManager.isPotionEnabled()) {
            sender.sendMessage("§c[PlayerSize] Зелье роста отключено в конфигурации!");
            return true;
        }

        // Создаем зелья и выдаем их
        for (int i = 0; i < amount; i++) {
            ItemStack potion = potionManager.createSizePotion();
            if (targetPlayer.getInventory().firstEmpty() == -1) {
                // Инвентарь полон, выкидываем на землю
                targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), potion);
            } else {
                targetPlayer.getInventory().addItem(potion);
            }
        }

        // Сообщения
        if (sender.equals(targetPlayer)) {
            sender.sendMessage("§a[PlayerSize] Вам выдано §e" + amount + " §aзелий роста!");
        } else {
            sender.sendMessage("§a[PlayerSize] Игроку §e" + targetPlayer.getName() + " §aвыдано §e" + amount
                    + " §aзелий роста!");
            targetPlayer.sendMessage("§a[PlayerSize] Вам выдано §e" + amount + " §aзелий роста от §e"
                    + sender.getName() + "§a!");
        }

        getLogger().info("Игроку " + targetPlayer.getName() + " выдано " + amount + " зелий роста администратором "
                + sender.getName());

        return true;
    }

    private UUID findPlayerUUID(String name) {
        // Сначала ищем по нику в сохраненных данных
        Map<UUID, String> allNames = playerSizeManager.getAllPlayerNames();
        for (Map.Entry<UUID, String> entry : allNames.entrySet()) {
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
            completions.add("give");
        } else if (args.length == 2) {
            // Имена игроков для set, reset, check, give
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("set") || subCommand.equals("reset") || subCommand.equals("check")) {
                // Онлайн игроки
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
                // Оффлайн игроки из базы
                Map<UUID, String> allNames = playerSizeManager.getAllPlayerNames();
                for (String name : allNames.values()) {
                    if (name.toLowerCase().startsWith(args[1].toLowerCase()) && !completions.contains(name)) {
                        completions.add(name);
                    }
                }
            } else if (subCommand.equals("give")) {
                // Для команды give предлагаем "potion"
                if ("potion".startsWith(args[1].toLowerCase())) {
                    completions.add("potion");
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("set")) {
                // Подсказки для размера
                completions.add("0.5");
                completions.add("0.75");
                completions.add("0.83");
                completions.add("0.9");
                completions.add("1.0");
            } else if (args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("potion")) {
                // Для команды give potion предлагаем имена игроков или количество
                // Если отправитель - игрок, предлагаем количество, иначе имена игроков
                if (sender instanceof Player) {
                    completions.add("1");
                    completions.add("5");
                    completions.add("10");
                    completions.add("16");
                    completions.add("32");
                    completions.add("64");
                } else {
                    // Консоль должна указать игрока
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                            completions.add(player.getName());
                        }
                    }
                }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("potion")) {
            // Количество для команды give potion <игрок> <количество>
            completions.add("1");
            completions.add("5");
            completions.add("10");
            completions.add("16");
            completions.add("32");
            completions.add("64");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            // Подсказки для номеров страниц
            Map<UUID, Double> allSizes = playerSizeManager.getAllPlayerSizes();
            int totalPages = (int) Math.ceil((double) allSizes.size() / 15.0);
            for (int i = 1; i <= Math.min(totalPages, 10); i++) {
                completions.add(String.valueOf(i));
            }
        }

        return completions;
    }

}
