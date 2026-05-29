package ru.yourname.beartraps;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;

public class BearTraps extends JavaPlugin implements Listener, CommandExecutor {

    private NamespacedKey trapKey;
    private NamespacedKey trapIdKey;
    private NamespacedKey trapPartKey;
    private NamespacedKey trapStateKey;

    @Override
    public void onEnable() {
        trapKey = new NamespacedKey(this, "is_bear_trap");
        trapIdKey = new NamespacedKey(this, "trap_id");
        trapPartKey = new NamespacedKey(this, "trap_part");
        trapStateKey = new NamespacedKey(this, "trap_state");

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("cleartraps") != null) {
            getCommand("cleartraps").setExecutor(this);
        }
        
        registerRecipe();
        startDetectionTask();
        getLogger().info("BearTraps успешно активирован и готов к работе!");
    }

    private void registerRecipe() {
        ItemStack item = new ItemStack(Material.CHAIN);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_GRAY + "Медвежий капкан");
            meta.getPersistentDataContainer().set(trapKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }

        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, "trap_recipe"), item);
        recipe.shape("NNN", "NCN", "NNN");
        recipe.setIngredient('N', Material.IRON_NUGGET);
        recipe.setIngredient('C', Material.CHAIN);
        getServer().addRecipe(recipe);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // 1. Установка капкана (ПКМ)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getItem() != null && event.getClickedBlock() != null) {
            ItemMeta meta = event.getItem().getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(trapKey, PersistentDataType.BYTE)) {
                event.setCancelled(true);
                
                // Высчитываем блок над тем, на который кликнули
                Location spawnLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0.0, 0.5);
                spawnTrap(spawnLoc);
                
                if (player.getGameMode() != GameMode.CREATIVE) {
                    event.getItem().setAmount(event.getItem().getAmount() - 1);
                }
                player.playSound(spawnLoc, Sound.BLOCK_ANVIL_PLACE, 0.6f, 1.7f);
                return;
            }
        }

        // 2. Демонтаж рукой без консоли (ЛКМ по блоку с капканом)
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Location clickLoc = event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
            for (ItemDisplay display : clickLoc.getNearbyEntitiesByType(ItemDisplay.class, 1.5)) {
                if (display.getPersistentDataContainer().has(trapIdKey, PersistentDataType.STRING)) {
                    String id = display.getPersistentDataContainer().get(trapIdKey, PersistentDataType.STRING);
                    String state = display.getPersistentDataContainer().get(trapStateKey, PersistentDataType.STRING);
                    
                    removeTrapEntities(display.getWorld(), id);
                    
                    // Если капкан убирают целым (не сработавшим), возвращаем предмет в инвентарь
                    if (player.getGameMode() == GameMode.CREATIVE || !"triggered".equals(state)) {
                        ItemStack trapItem = new ItemStack(Material.CHAIN);
                        ItemMeta meta = trapItem.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(ChatColor.DARK_GRAY + "Медвежий капкан");
                            meta.getPersistentDataContainer().set(trapKey, PersistentDataType.BYTE, (byte) 1);
                            trapItem.setItemMeta(meta);
                        }
                        player.getInventory().addItem(trapItem);
                    }
                    
                    player.sendMessage(ChatColor.YELLOW + "Капкан успешно демонтирован.");
                    player.playSound(clickLoc, Sound.BLOCK_CHAIN_BREAK, 0.8f, 1.2f);
                    break;
                }
            }
        }
    }

    private void spawnTrap(Location loc) {
        String id = UUID.randomUUID().toString();

        // Основание капкана (Кладем цепь горизонтально)
        ItemDisplay base = loc.getWorld().spawn(loc, ItemDisplay.class);
        base.setItemStack(new ItemStack(Material.CHAIN));
        base.getPersistentDataContainer().set(trapIdKey, PersistentDataType.STRING, id);
        base.getPersistentDataContainer().set(trapPartKey, PersistentDataType.STRING, "base");
        base.getPersistentDataContainer().set(trapStateKey, PersistentDataType.STRING, "armed");
        base.setTransformation(new Transformation(
                new Vector3f(0, 0.02f, 0),
                new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0), // Ложим на бок
                new Vector3f(1.2f, 0.2f, 1.2f),
                new AxisAngle4f()
        ));

        // Левая челюсть (Железный самородок развернут на -45 градусов)
        ItemDisplay leftJaw = loc.getWorld().spawn(loc, ItemDisplay.class);
        leftJaw.setItemStack(new ItemStack(Material.IRON_NUGGET));
        leftJaw.getPersistentDataContainer().set(trapIdKey, PersistentDataType.STRING, id);
        leftJaw.getPersistentDataContainer().set(trapPartKey, PersistentDataType.STRING, "left");
        leftJaw.setTransformation(new Transformation(
                new Vector3f(-0.25f, 0.05f, 0),
                new AxisAngle4f((float) Math.toRadians(-45), 0, 0, 1),
                new Vector3f(2.2f, 2.2f, 2.2f),
                new AxisAngle4f()
        ));

        // Правая челюсть (Железный самородок развернут на +45 градусов)
        ItemDisplay rightJaw = loc.getWorld().spawn(loc, ItemDisplay.class);
        rightJaw.setItemStack(new ItemStack(Material.IRON_NUGGET));
        rightJaw.getPersistentDataContainer().set(trapIdKey, PersistentDataType.STRING, id);
        rightJaw.getPersistentDataContainer().set(trapPartKey, PersistentDataType.STRING, "right");
        rightJaw.setTransformation(new Transformation(
                new Vector3f(0.25f, 0.05f, 0),
                new AxisAngle4f((float) Math.toRadians(45), 0, 0, 1),
                new Vector3f(2.2f, 2.2f, 2.2f),
                new AxisAngle4f()
        ));
    }

    private void startDetectionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                        if (display.getPersistentDataContainer().has(trapPartKey, PersistentDataType.STRING)) {
                            String part = display.getPersistentDataContainer().get(trapPartKey, PersistentDataType.STRING);
                            String state = display.getPersistentDataContainer().get(trapStateKey, PersistentDataType.STRING);
                            
                            // Проверяем триггер только на "базе", чтобы не гонять код 3 раза для одного капкана
                            if ("base".equals(part) && "armed".equals(state)) {
                                Location loc = display.getLocation();
                                for (Entity entity : loc.getWorld().getNearbyEntities(loc, 0.6, 0.6, 0.6)) {
                                    if (entity instanceof LivingEntity && !(entity instanceof ArmorStand)) {
                                        // Креативных игроков не трогаем
                                        if (entity instanceof Player && ((Player) entity).getGameMode() == GameMode.CREATIVE) {
                                            continue;
                                        }
                                        triggerTrap(display, (LivingEntity) entity);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 4L); // Проверка каждые 4 тика (стабильно и без лагов)
    }

    private void triggerTrap(ItemDisplay base, LivingEntity victim) {
        String id = base.getPersistentDataContainer().get(trapIdKey, PersistentDataType.STRING);
        base.getPersistentDataContainer().set(trapStateKey, PersistentDataType.STRING, "triggered");

        Location loc = base.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.0f, 0.5f);
        loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.9f, 1.9f);
        loc.getWorld().spawnParticle(Particle.CRIT, loc.clone().add(0, 0.2, 0), 25, 0.1, 0.1, 0.1, 0.1);

        // Запуск анимации схлопывания зубьев (интерполяция матриц за 3 тика)
        for (ItemDisplay jaw : loc.getNearbyEntitiesByType(ItemDisplay.class, 2.0)) {
            if (id.equals(jaw.getPersistentDataContainer().get(trapIdKey, PersistentDataType.STRING))) {
                String part = jaw.getPersistentDataContainer().get(trapPartKey, PersistentDataType.STRING);
                
                jaw.setInterpolationDuration(3); // Скорость схлопывания в тиках
                jaw.setInterpolationDelay(0);
                
                if ("left".equals(part)) {
                    jaw.setTransformation(new Transformation(
                            new Vector3f(-0.05f, 0.05f, 0),
                            new AxisAngle4f(0, 0, 0, 1), // Сброс угла вращения в 0
                            new Vector3f(2.2f, 2.2f, 2.2f),
                            new AxisAngle4f()
                    ));
                } else if ("right".equals(part)) {
                    jaw.setTransformation(new Transformation(
                            new Vector3f(0.05f, 0.05f, 0),
                            new AxisAngle4f(0, 0, 0, 1), // Сброс угла вращения в 0
                            new Vector3f(2.2f, 2.2f, 2.2f),
                            new AxisAngle4f()
                    ));
                }
            }
        }

        // Нанесение урона и дебафф (Замедление 4 уровня на 5 секунд)
        victim.damage(8.0); 
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 3, false, true));

        // Полное удаление структуры капкана через 3 секунды после срабатывания
        new BukkitRunnable() {
            @Override
            public void run() {
                removeTrapEntities(base.getWorld(), id);
            }
        }.runTaskLater(this, 60L);
    }

    private void removeTrapEntities(World world, String id) {
        if (id == null) return;
        for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
            if (id.equals(display.getPersistentDataContainer().get(trapIdKey, PersistentDataType.STRING))) {
                display.remove();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("cleartraps")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Команда доступна только игрокам.");
                return true;
            }
            
            Player player = (Player) sender;
            double radius = 10.0;
            
            if (args.length > 0) {
                try {
                    radius = Double.parseDouble(args[0]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Неверный формат радиуса!");
                    return true;
                }
            }

            int removedCount = 0;
            Location center = player.getLocation();
            
            for (ItemDisplay display : center.getNearbyEntitiesByType(ItemDisplay.class, radius)) {
                if (display.getPersistentDataContainer().has(trapPartKey, PersistentDataType.STRING) &&
                    "base".equals(display.getPersistentDataContainer().get(trapPartKey, PersistentDataType.STRING))) {
                    
                    String id = display.getPersistentDataContainer().get(trapIdKey, PersistentDataType.STRING);
                    removeTrapEntities(player.getWorld(), id);
                    removedCount++;
                }
            }

            player.sendMessage(ChatColor.GREEN + "Успешно очищено капканов в радиусе: " + removedCount);
            return true;
        }
        return false;
    }
}
