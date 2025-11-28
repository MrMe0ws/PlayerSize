package com.meows.playersize;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;
import java.util.UUID;

public class SizePotionManager implements Listener {

    private final PlayerSizePlugin plugin;
    private final Random random;
    private NamespacedKey sizePotionKey;

    public SizePotionManager(PlayerSizePlugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
        this.sizePotionKey = new NamespacedKey(plugin, "size_potion");
    }

    public ItemStack createSizePotion() {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();

        if (meta != null) {
            ConfigManager config = plugin.getConfigManager();

            meta.setDisplayName(config.getPotionName());
            meta.setLore(config.getPotionLore());

            // Устанавливаем цвет зелья из конфига
            meta.setColor(Color.fromRGB(
                    config.getPotionColorRed(),
                    config.getPotionColorGreen(),
                    config.getPotionColorBlue()));

            // Добавляем кастомный эффект для идентификации
            meta.addCustomEffect(new PotionEffect(PotionEffectType.LUCK, 1, 0, false, false, false), false);

            // Добавляем кастомный ключ для идентификации
            meta.getPersistentDataContainer().set(sizePotionKey, org.bukkit.persistence.PersistentDataType.BOOLEAN,
                    true);

            potion.setItemMeta(meta);
        }

        return potion;
    }

    public boolean isSizePotion(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) {
            return false;
        }

        if (!(item.getItemMeta() instanceof PotionMeta)) {
            return false;
        }

        PotionMeta meta = (PotionMeta) item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(sizePotionKey,
                org.bukkit.persistence.PersistentDataType.BOOLEAN);
    }

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        if (!plugin.getConfigManager().isPotionEnabled()) {
            return;
        }

        ItemStack item = event.getItem();
        if (!isSizePotion(item)) {
            return;
        }

        Player player = event.getPlayer();

        // Применяем изменение размера с задержкой
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            applySizeChange(player);
        }, 1L);
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!plugin.getConfigManager().isPotionEnabled()) {
            return;
        }

        if (!plugin.getConfigManager().isPotionApplyOnMobs()) {
            return;
        }

        Player player = event.getPlayer();
        Entity clickedEntity = event.getRightClicked();

        // Проверяем, что это не игрок (для игроков используется питье)
        if (clickedEntity instanceof Player) {
            return;
        }

        // Проверяем, что это живое существо (моб)
        if (!(clickedEntity instanceof LivingEntity)) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isSizePotion(item)) {
            // Проверяем также предмет в левой руке
            item = player.getInventory().getItemInOffHand();
            if (!isSizePotion(item)) {
                return;
            }
        }

        // Отменяем стандартное взаимодействие
        event.setCancelled(true);

        LivingEntity mob = (LivingEntity) clickedEntity;

        // Применяем изменение размера мобу
        applySizeChangeToMob(mob, player);

        // Убираем одно зелье из инвентаря (только если игрок не в креативе)
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                if (player.getInventory().getItemInMainHand().equals(item)) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    player.getInventory().setItemInOffHand(null);
                }
            }
        }
    }

    private void applySizeChange(Player player) {
        // Генерируем новый размер
        double newSize;
        if (plugin.getConfigManager().isUseRandomSize()) {
            newSize = plugin.getConfigManager().getMinSize()
                    + (plugin.getConfigManager().getMaxSize() - plugin.getConfigManager().getMinSize())
                            * random.nextDouble();
            newSize = Math.round(newSize * 100.0) / 100.0;
        } else {
            newSize = plugin.getConfigManager().getDefaultSize();
        }

        UUID uuid = player.getUniqueId();
        plugin.getPlayerSizeManager().setPlayerSize(uuid, newSize);
        plugin.getPlayerSizeManager().setPlayerName(uuid, player.getName());
        plugin.getPlayerSizeManager().savePlayerSizes();

        // Применяем размер и здоровье
        plugin.getPlayerSizeManager().applySize(player, newSize);
        plugin.getPlayerSizeManager().applyHealth(player, newSize);

        // Визуальные эффекты
        spawnPotionEffects(player);

        // Сообщения
        player.sendMessage("§6§l[Зелье Изменения Роста]");
        player.sendMessage("§aВаш размер изменен на §e" + newSize);
        player.sendMessage("§7Рост: §e~" + String.format("%.2f", newSize * 1.8) + " блока");

        plugin.getLogger()
                .info("Игрок " + player.getName() + " использовал зелье изменения роста. Новый размер: " + newSize);
    }

    private void applySizeChangeToMob(LivingEntity mob, Player player) {
        // Генерируем новый размер
        double newSize;
        if (plugin.getConfigManager().isUseRandomSize()) {
            newSize = plugin.getConfigManager().getMinSize()
                    + (plugin.getConfigManager().getMaxSize() - plugin.getConfigManager().getMinSize())
                            * random.nextDouble();
            newSize = Math.round(newSize * 100.0) / 100.0;
        } else {
            newSize = plugin.getConfigManager().getDefaultSize();
        }

        // Применяем размер мобу
        try {
            AttributeInstance scaleAttribute = mob.getAttribute(Attribute.GENERIC_SCALE);
            if (scaleAttribute != null) {
                scaleAttribute.setBaseValue(newSize);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось применить размер мобу " + mob.getType() + ": " + e.getMessage());
        }

        // Визуальные эффекты
        spawnPotionEffects(mob);

        // Сообщения
        String mobName = mob.getType().name().toLowerCase().replace("_", " ");
        player.sendMessage("§6§l[Зелье Изменения Роста]");
        player.sendMessage("§aРазмер моба §e" + mobName + " §aизменен на §e" + newSize);
        player.sendMessage("§7Рост: §e~" + String.format("%.2f", newSize * 1.8) + " блока");

        plugin.getLogger().info(
                "Игрок " + player.getName() + " применил зелье на моба " + mobName + ". Новый размер: " + newSize);
    }

    public void spawnPotionEffects(Player player) {
        spawnPotionEffects((org.bukkit.entity.LivingEntity) player);
    }

    public void spawnPotionEffects(org.bukkit.entity.LivingEntity entity) {
        double height = entity.getHeight();

        // Частицы вокруг существа
        for (int i = 0; i < 30; i++) {
            double angle = 2 * Math.PI * i / 30.0;
            double x = Math.cos(angle) * 0.5;
            double z = Math.sin(angle) * 0.5;

            entity.getWorld().spawnParticle(
                    org.bukkit.Particle.DUST,
                    entity.getLocation().add(x, height / 2, z),
                    1,
                    new org.bukkit.Particle.DustOptions(
                            Color.fromRGB(255, 165, 0), // Оранжевый цвет
                            1.0f));
        }

        // Вспышка сверху (используем несколько частиц для эффекта)
        for (int i = 0; i < 10; i++) {
            entity.getWorld().spawnParticle(
                    org.bukkit.Particle.HEART,
                    entity.getLocation().add(0, height + (i * 0.1), 0),
                    1,
                    0.3, 0.3, 0.3);
        }

        // Звук
        entity.getWorld().playSound(entity.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    }
}
