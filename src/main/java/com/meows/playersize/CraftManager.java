package com.meows.playersize;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CraftManager implements Listener {

    private final PlayerSizePlugin plugin;
    private Map<Material, Integer> requiredItems;

    // Список всех грибов в Minecraft
    private static final Set<Material> MUSHROOM_MATERIALS = Set.of(
            Material.BROWN_MUSHROOM,
            Material.RED_MUSHROOM,
            Material.CRIMSON_FUNGUS,
            Material.WARPED_FUNGUS);

    public CraftManager(PlayerSizePlugin plugin) {
        this.plugin = plugin;
        loadRecipe();
    }

    public void loadRecipe() {
        requiredItems = new HashMap<>();
        List<String> recipeItems = plugin.getConfigManager().getPotionRecipe();

        for (String itemStr : recipeItems) {
            try {
                Material material = Material.valueOf(itemStr.toUpperCase());
                requiredItems.put(material, requiredItems.getOrDefault(material, 0) + 1);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Некорректный материал в рецепте зелья: " + itemStr);
            }
        }

        plugin.getLogger().info("Загружен рецепт зелья: " + requiredItems.size() + " предметов");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!plugin.getConfigManager().isPotionEnabled()) {
            return;
        }

        CraftingInventory inventory = event.getInventory();
        if (inventory == null) {
            return;
        }

        // Проверяем тип инвентаря
        // Если рецепт содержит 5 или больше предметов, разрешаем только верстак
        // (WORKBENCH)
        // Если рецепт содержит меньше 5 предметов, разрешаем и инвентарь 2x2 (CRAFTING)
        int totalRequiredItems = requiredItems.values().stream().mapToInt(Integer::intValue).sum();
        if (totalRequiredItems >= 5) {
            // Для рецептов из 5+ предметов нужен верстак
            if (inventory.getType() != InventoryType.WORKBENCH) {
                return;
            }
        } else {
            // Для рецептов из 4 и меньше предметов можно использовать и инвентарь 2x2
            if (inventory.getType() != InventoryType.WORKBENCH && inventory.getType() != InventoryType.CRAFTING) {
                return;
            }
        }

        Map<Material, Integer> craftingItems = new HashMap<>();
        int nonEmptySlots = 0;

        // Подсчитываем все предметы в верстаке
        for (ItemStack item : inventory.getMatrix()) {
            if (item != null && item.getType() != Material.AIR) {
                Material material = item.getType();
                // В верстаке считаем количество слотов, а не количество предметов в стаке
                craftingItems.put(material, craftingItems.getOrDefault(material, 0) + 1);
                nonEmptySlots++;
            }
        }

        // Проверяем, соответствует ли рецепт
        if (matchesRecipe(craftingItems, nonEmptySlots)) {
            // Заменяем результат на наше зелье
            ItemStack sizePotion = plugin.getPotionManager().createSizePotion();
            inventory.setResult(sizePotion);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!plugin.getConfigManager().isPotionEnabled()) {
            return;
        }

        // Проверяем тип инвентаря (аналогично onPrepareCraft)
        int totalRequiredItems = requiredItems.values().stream().mapToInt(Integer::intValue).sum();
        if (totalRequiredItems >= 5) {
            // Для рецептов из 5+ предметов нужен верстак
            if (event.getInventory().getType() != InventoryType.WORKBENCH) {
                return;
            }
        } else {
            // Для рецептов из 4 и меньше предметов можно использовать и инвентарь 2x2
            if (event.getInventory().getType() != InventoryType.WORKBENCH
                    && event.getInventory().getType() != InventoryType.CRAFTING) {
                return;
            }
        }

        if (!(event.getInventory() instanceof CraftingInventory)) {
            return;
        }

        CraftingInventory inventory = (CraftingInventory) event.getInventory();

        // Проверяем, что в результате наше зелье
        ItemStack result = inventory.getResult();
        if (result == null || !plugin.getPotionManager().isSizePotion(result)) {
            return;
        }

        // Проверяем, что кликнули по результату крафта (слот 0 в верстаке)
        // В верстаке результат находится в слоте 0
        if (event.getSlot() != 0) {
            return;
        }

        // Получаем текущий курсор игрока
        ItemStack currentCursor = event.getCursor();

        // Если курсор не пустой, не обрабатываем (стандартное поведение)
        if (currentCursor != null && currentCursor.getType() != Material.AIR) {
            return;
        }

        if (plugin.getConfigManager().isPotionDebugCrafting()) {
            plugin.getLogger().info("=== КРАФТ ЗЕЛЬЯ: Начало обработки ===");
            plugin.getLogger().info("Слот клика: " + event.getSlot());
            plugin.getLogger().info("Курсор: " + (currentCursor != null ? currentCursor.getType() : "null"));
        }

        // Отменяем стандартное удаление предметов
        event.setCancelled(true);

        // Даем зелье игроку
        event.getWhoClicked().setItemOnCursor(result.clone());

        // Сохраняем ссылку на инвентарь и копию матрицы для удаления предметов
        final CraftingInventory finalInventory = inventory;
        ItemStack[] matrix = inventory.getMatrix();
        ItemStack[] matrixCopy = new ItemStack[matrix.length];

        if (plugin.getConfigManager().isPotionDebugCrafting()) {
            plugin.getLogger().info("Матрица ДО удаления:");
            for (int i = 0; i < matrix.length; i++) {
                if (matrix[i] != null) {
                    matrixCopy[i] = matrix[i].clone();
                    plugin.getLogger().info("  Слот " + i + ": " + matrix[i].getType() + " x" + matrix[i].getAmount());
                } else {
                    plugin.getLogger().info("  Слот " + i + ": пусто");
                }
            }
        } else {
            // Все равно копируем матрицу для удаления предметов
            for (int i = 0; i < matrix.length; i++) {
                if (matrix[i] != null) {
                    matrixCopy[i] = matrix[i].clone();
                }
            }
        }

        // Удаляем предметы из крафт-сетки вручную с небольшой задержкой
        // Это нужно, чтобы избежать конфликта с обработкой клика Bukkit
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            boolean debugCrafting = plugin.getConfigManager().isPotionDebugCrafting();

            if (debugCrafting) {
                plugin.getLogger().info("=== КРАФТ ЗЕЛЬЯ: Начало удаления предметов ===");
            }

            // Создаем копию требуемых предметов для отслеживания, что нужно удалить
            Map<Material, Integer> itemsToRemove = new HashMap<>();
            for (Map.Entry<Material, Integer> entry : requiredItems.entrySet()) {
                itemsToRemove.put(entry.getKey(), entry.getValue());
            }

            if (debugCrafting) {
                plugin.getLogger().info("Требуется удалить:");
                for (Map.Entry<Material, Integer> entry : itemsToRemove.entrySet()) {
                    plugin.getLogger().info("  " + entry.getKey() + ": " + entry.getValue());
                }
            }

            // Используем сохраненную копию матрицы для определения, какие предметы нужно
            // удалить
            // Проходим по всем слотам крафт-сетки и удаляем по одному предмету каждого типа
            for (int i = 0; i < matrixCopy.length; i++) {
                ItemStack originalItem = matrixCopy[i];
                if (originalItem == null || originalItem.getType() == Material.AIR) {
                    continue;
                }

                Material itemType = originalItem.getType();
                boolean shouldRemove = false;
                Material requiredType = null;

                // Проверяем, нужно ли удалить этот предмет
                if (MUSHROOM_MATERIALS.contains(itemType)) {
                    // Это гриб - проверяем, требуется ли гриб в рецепте
                    for (Material mushroom : MUSHROOM_MATERIALS) {
                        if (itemsToRemove.containsKey(mushroom) && itemsToRemove.get(mushroom) > 0) {
                            shouldRemove = true;
                            requiredType = mushroom;
                            break;
                        }
                    }
                } else {
                    // Обычный предмет - проверяем точное совпадение
                    if (itemsToRemove.containsKey(itemType) && itemsToRemove.get(itemType) > 0) {
                        shouldRemove = true;
                        requiredType = itemType;
                    }
                }

                if (shouldRemove && requiredType != null) {
                    if (debugCrafting) {
                        plugin.getLogger()
                                .info("Обработка слота " + i + ": " + itemType + " x" + originalItem.getAmount());
                    }

                    // Уменьшаем счетчик требуемых предметов
                    int remaining = itemsToRemove.get(requiredType) - 1;
                    itemsToRemove.put(requiredType, remaining);

                    // Получаем текущий предмет из инвентаря напрямую через setItem/getItem
                    // Для верстака: слот 0 = результат, слоты 1-9 = матрица (i=0..8 -> слот i+1)
                    int inventorySlot = i + 1; // Индекс слота в инвентаре верстака
                    ItemStack currentItem = finalInventory.getItem(inventorySlot);

                    if (currentItem == null) {
                        if (debugCrafting) {
                            plugin.getLogger()
                                    .warning("  Слот инвентаря " + inventorySlot + " (матрица " + i + ") уже пуст!");
                        }
                        continue;
                    }

                    if (currentItem.getType() != itemType) {
                        if (debugCrafting) {
                            plugin.getLogger().warning("  Слот инвентаря " + inventorySlot + " (матрица " + i
                                    + ") изменился! Было: " + itemType + ", стало: " + currentItem.getType());
                        }
                        continue;
                    }

                    // Удаляем один предмет из стака
                    int currentAmount = currentItem.getAmount();
                    if (debugCrafting) {
                        plugin.getLogger().info("  Текущее количество: " + currentAmount);
                    }

                    if (currentAmount > 1) {
                        // Если в стаке больше 1 предмета, уменьшаем количество
                        ItemStack newItem = currentItem.clone();
                        newItem.setAmount(currentAmount - 1);
                        // Обновляем слот напрямую через setItem
                        finalInventory.setItem(inventorySlot, newItem);
                        if (debugCrafting) {
                            plugin.getLogger().info("  Установлено новое количество: " + (currentAmount - 1)
                                    + " в слот инвентаря " + inventorySlot);
                        }
                    } else {
                        // Если в стаке 1 предмет, удаляем весь стак
                        finalInventory.setItem(inventorySlot, null);
                        if (debugCrafting) {
                            plugin.getLogger().info("  Слот инвентаря " + inventorySlot + " очищен");
                        }
                    }
                } else if (debugCrafting) {
                    plugin.getLogger().info("Слот " + i + ": " + itemType + " - не требуется удаление");
                }
            }

            if (debugCrafting) {
                plugin.getLogger().info("Матрица ПОСЛЕ удаления:");
                ItemStack[] finalMatrix = finalInventory.getMatrix();
                for (int i = 0; i < finalMatrix.length; i++) {
                    if (finalMatrix[i] != null) {
                        plugin.getLogger().info("  Слот матрицы " + i + ": " + finalMatrix[i].getType() + " x"
                                + finalMatrix[i].getAmount());
                    } else {
                        plugin.getLogger().info("  Слот матрицы " + i + ": пусто");
                    }
                }
            }

            // Обновляем результат крафта после удаления предметов
            // Это нужно, чтобы можно было сразу крафтить еще одно зелье, если предметов
            // хватает
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Проверяем, соответствует ли рецепт после удаления предметов
                Map<Material, Integer> craftingItems = new HashMap<>();
                int nonEmptySlots = 0;

                for (ItemStack item : finalInventory.getMatrix()) {
                    if (item != null && item.getType() != Material.AIR) {
                        Material material = item.getType();
                        craftingItems.put(material, craftingItems.getOrDefault(material, 0) + 1);
                        nonEmptySlots++;
                    }
                }

                if (matchesRecipe(craftingItems, nonEmptySlots)) {
                    // Если рецепт все еще совпадает, устанавливаем результат
                    ItemStack sizePotion = plugin.getPotionManager().createSizePotion();
                    finalInventory.setResult(sizePotion);
                    if (plugin.getConfigManager().isPotionDebugCrafting()) {
                        plugin.getLogger().info("Рецепт все еще совпадает - можно крафтить еще одно зелье");
                    }
                } else {
                    // Если рецепт не совпадает, очищаем результат
                    finalInventory.setResult(null);
                    if (plugin.getConfigManager().isPotionDebugCrafting()) {
                        plugin.getLogger().info("Рецепт больше не совпадает - результат очищен");
                    }
                }
            });

            if (debugCrafting) {
                plugin.getLogger().info("=== КРАФТ ЗЕЛЬЯ: Конец обработки ===");
            }
        });
    }

    private boolean matchesRecipe(Map<Material, Integer> craftingItems, int nonEmptySlots) {
        // Проверяем, что все требуемые предметы присутствуют в нужном количестве
        for (Map.Entry<Material, Integer> entry : requiredItems.entrySet()) {
            Material requiredMaterial = entry.getKey();
            int requiredAmount = entry.getValue();

            // Если требуется гриб, проверяем любой гриб из списка
            if (MUSHROOM_MATERIALS.contains(requiredMaterial)) {
                int mushroomCount = 0;
                for (Material mushroom : MUSHROOM_MATERIALS) {
                    mushroomCount += craftingItems.getOrDefault(mushroom, 0);
                }
                if (mushroomCount < requiredAmount) {
                    return false;
                }
            } else {
                // Для остальных предметов проверяем точное совпадение
                int actualAmount = craftingItems.getOrDefault(requiredMaterial, 0);
                if (actualAmount < requiredAmount) {
                    return false;
                }
            }
        }

        int totalRequired = requiredItems.values().stream().mapToInt(Integer::intValue).sum();

        // Должно быть ровно столько предметов, сколько требуется
        return nonEmptySlots == totalRequired;
    }
}
