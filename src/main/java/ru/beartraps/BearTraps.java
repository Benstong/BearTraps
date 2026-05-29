package ru.beartraps;

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
    private NamespacedKey trapBaitKey; // Ключ для проверки наличия приманки

    @Override
    public void onEnable() {
        trapKey = new NamespacedKey(this, "is_bear_trap");
        trapIdKey = new NamespacedKey(this, "trap_id");
        trapPartKey = new NamespacedKey(this, "trap_part");
        trapStateKey = new NamespacedKey(this, "trap_state");
        trapBaitKey = new NamespacedKey(this, "trap_has_bait");

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("cleartraps") != null) {
            getCommand("cleartraps").setExecutor(this);
        }
        
        registerRecipe();
        startDetectionTask();
        getLogger().info("BearTraps (Приманка для Зомби) запущен!");
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
        ItemStack handItem = event.getItem();

        // 1. Установка закрытого капкана (ПКМ)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && handItem != null && event.getClickedBlock() != null) {
            ItemMeta meta = handItem.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(trapKey, PersistentDataType.BYTE)) {
                event.setCancelled(true);
                
                Location spawnLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0.02, 0.5);
                spawnClosedTrap(spawnLoc);
                
                if (player.getGameMode() != GameMode.CREATIVE) {
                    handItem.setAmount(handItem.getAmount() - 1);
                }
                player.playSound(spawnLoc, Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.5f);
                player.sendMessage(ChatColor.GRAY + "Вы установили капкан. Его нужно взвести (ПКМ пустой рукой).");
                return;
            }
        }

        // 2. Взаимодействие с установленным капканом (Взвод / Установка приманки)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Location clickLoc = event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
            if (clickLoc.getWorld() != null) {
                for (Entity entity : clickLoc.getWorld().getNearbyEntities(clickLoc, 1.2, 1.2, 1.2)) {
                    if (entity instanceof ItemDisplay display && display.getPersistentDataContainer().has(trapIdKey, PersistentDataType.STRING)) {
                        
                        String id = display.getPersistentDataContainer().get(trapIdKey, PersistentDataType.STRING);
                        ItemDisplay anchor = findAnchor(display.getWorld(), id);
                        
                        if (anchor != null) {
                            String state = anchor.getPersistentDataContainer().get(trapStateKey, PersistentDataType.STRING);
                            
                            // Ситуация А: Игрок нажал Shift + ПКМ с Гнилой плотью по ОТКРЫТОМУ (armed) капкану
                            if ("armed".equals(state) && player.isSneaking() && handItem != null && handItem.getType() == Material.ROTTEN_FLESH) {
                                event.setCancelled(true);
                                
                                // Проверяем, нет ли уже там приманки
                                if (!anchor.getPersistentDataContainer().has(trapBaitKey, PersistentDataType.BYTE)) {
                                    anchor.getPersistentDataContainer().set(trapBaitKey, PersistentDataType.BYTE, (byte) 1);
                                    
                                    // Спавним гнилую плоть визуально по центру капкана
                                    ItemDisplay baitDisplay = anchor.getWorld().spawn(anchor.getLocation(), ItemDisplay.class);
                                    baitDisplay.setItemStack(new ItemStack(Material.ROTTEN_FLESH));
                                    baitDisplay.getPersistentDataContainer().set(trapIdKey, PersistentDataType.STRING, id);
                                    baitDisplay.getPersistentDataContainer().set(trapPartKey, PersistentDataType.STRING, "bait");
                                    baitDisplay.setTransformation(new Transformation(
                                            new Vector3f(0f, 0.08f, 0f), // Чуть-чуть приподнята в центре
                                            new AxisAngle4f(0, 0, 0, 1),
                                            new Vector3f(0.4f, 0.4f, 0.4f), // Маленький кусочек мяса
                                            new AxisAngle4f()
                                    ));
                                    
                                    if (player.getGameMode() != GameMode.CREATIVE) {
                                        handItem.setAmount(handItem.getAmount() - 1);
                                    }
                                    
                                    player.playSound(anchor.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.6f, 0.8f);
                                    player.sendMessage(ChatColor.GOLD + "Вы положили гнилую плоть в капкан. Зомби поблизости учуют запах!");
                                }
                                return;
                            }
                            
                            // Ситуация Б: Обычный взвод пустого/захлопнутого капкана (ПКМ без предметов)
                            if (handItem == null && ("closed".equals(state) || "triggered".equals(state))) {
                                event.setCancelled(true);
                                playArmingAnimation(player, anchor);
                                return;
                            }
                        }
                    }
                }
            }
        }

        // 3. Демонтаж рукой (ЛКМ)
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Location clickLoc = event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
            if (clickLoc.getWorld() != null) {
                for (Entity entity : clickLoc.getWorld().getNearbyEntities(clickLoc, 1.2, 1.2, 1.2)) {
                    if (entity instanceof ItemDisplay display) {
                        if (display.getPersistentDataContainer().has(trapIdKey, PersistentDataType.STRING)) {
                            String id = display.getPersistentDataContainer().get(trapIdKey, PersistentDataType.STRING);
                            
                            removeTrapEntities(display.getWorld(), id);
                            
                            ItemStack trapItem = new ItemStack(Material.CHAIN);
                            ItemMeta m = trapItem.getItemMeta();
                            if (m != null) {
                                m.setDisplayName(ChatColor.DARK_GRAY + "Медвежий капкан");
                                m.getPersistentDataContainer().set(trapKey, PersistentDataType.BYTE, (byte) 1);
                                trapItem.setItemMeta(m);
                            }
                            player.getInventory().addItem(trapItem);
                            
                            player.sendMessage(ChatColor.YELLOW + "Капкан успешно демонтирован.");
                            player.playSound(clickLoc, Sound.BLOCK_CHAIN_BREAK, 0.7f, 1.2f);
                            break;
                        }
                    }
                }
            }
        }
    }

    private void spawnClosedTrap(Location loc) {
        String id = UUID.randomUUID().toString();

        // 1. СТАТИЧЕСКОЕ ОСНОВАНИЕ
        float[][] baseOffsets = {
            {0.0f, 0.01f, -0.3f, 0}, {0.0f, 0.01f, 0.3f, 0},
            {-0.3f, 0.01f, 0.0f, 90}, {0.3f, 0.01f, 0.0f, 90}
        };

        for (int i = 0; i < baseOffsets.length; i++) {
            ItemDisplay basePart = loc.getWorld().spawn(loc, ItemDisplay.class);
            basePart.setItemStack(new ItemStack(Material.CHAIN));
            basePart.getPersistentDataContainer().set(trapIdKey, PersistentDataType.STRING, id);
            basePart.getPersistentDataContainer().set(trapPartKey, PersistentDataType.STRING, i == 0 ? "trigger_anchor" : "static_base");
            if (i == 0) basePart.getPersistentDataContainer().set(trapStateKey, PersistentDataType.STRING, "closed");

            basePart.setTransformation(new Transformation(
                    new Vector3f(baseOffsets[i][0], baseOffsets[i][1], baseOffsets[i][2]),
                    new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0),
                    new Vector3f(0.8f, 0.15f, 0.8f),
                    new AxisAngle4f((float) Math.toRadians(baseOffsets[i][3]), 0, 0, 1)
            ));
        }

        // 2. ЧЕЛЮСТИ И ЗУБЬЯ
        float[] teethZOffsets = {-0.25f, -0.1f, 0.1f, 0.25f};

        spawnMovingPart(loc, id, "left", Material.CHAIN, new Vector3f(-0.04f, 0.18f, 0f), -4, new Vector3f(0.8f, 0.15f, 0.8f));
        for (float zOffset : teethZOffsets) {
            spawnMovingPart(loc, id, "left", Material.IRON_NUGGET, new Vector3f(-0.04f, 0.20f, zOffset), -4, new Vector3f(0.4f, 0.4f, 0.4f));
        }

        long activeTrapsCount = loc.getWorld().getEntitiesByClass(ItemDisplay.class).stream()
                .filter(e -> "trigger_anchor".equals(e.getPersistentDataContainer().get(trapPartKey, PersistentDataType.STRING))).count();
        spawnMovingPart(loc, id, "right", Material.CHAIN, new Vector3f(0.04f, 0.18f, 0f), 4, new Vector3f(0.8f, 0.15f, 0.8f));
        for (float zOffset : teethZOffsets) {
            spawnMovingPart(loc, id, "right", Material.IRON_NUGGET, new Vector3f(0.04f, 0.20f, zOffset), 4, new Vector3f(0.4f, 0.4f, 0.4f));
        }
    }

    private void spawnMovingPart(Location loc, String id, String side, Material material, Vector3f translation, float angle, Vector3f scale) {
        ItemDisplay display = loc.getWorld().spawn(loc, ItemDisplay.class);
        display.setItemStack(new ItemStack(material));
        display.getPersistentDataContainer().set(trapIdKey, PersistentDataType.STRING, id);
        display.getPersistentDataContainer().set(trapPartKey, PersistentDataType.STRING, side);
        
        display.setTransformation(new Transformation(
                translation,
                new AxisAngle4f((float) Math.toRadians(angle), 0, 0, 1),
                scale,
                new AxisAngle4f()
        ));
    }

    private void playArmingAnimation(Player player, ItemDisplay anchor) {
        Location loc = anchor.getLocation();
        String id = anchor.getPersistentDataContainer().get(trapIdKey, PersistentDataType.STRING);

        player.swingMainHand();
        player.setSneaking(true);
        loc.getWorld().playSound(loc, Sound.BLOCK_CHAIN_PLACE, 0.8f, 0.8f);
        loc.getWorld().playSound(loc, Sound.BLOCK_IRON_DOOR_OPEN, 0.5f, 0.5f);

        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 2.0, 2.0, 2.0)) {
            if (entity instanceof ItemDisplay jaw && id.equals(jaw.getPersistentDataContainer().get(trapIdKey, PersistentDataType.STRING))) {
                String side = jaw.getPersistentDataContainer().get(trapPartKey, PersistentDataType.STRING);
                
                if ("left".equals(side) || "right".equals(side)) {
                    jaw.setInterpolationDuration(10);
                    jaw.setInterpolationDelay(0);
                    
                    Vector3f currentTrans = jaw.getTransformation().getTranslation();
                    Vector3f currentScale = jaw.getTransformation().getScale();
                    boolean isNugget = jaw.getItemStack().getType() == Material.IRON_NUGGET;

                    float newX = "left".equals(side) ? (isNugget ? -0.25f : -0.2f) : (isNugget ? 0.25f : 0.2f);
                    float newAngle = "left".equals(side) ? -65f : 65f;
                    float newY = isNugget ? 0.07f : 0.05f;

                    jaw.setTransformation(new Transformation(
                            new Vector3f(newX, newY, currentTrans.z),
                            new AxisAngle4f((float) Math.toRadians(newAngle), 0, 0, 1),
                            currentScale,
                            new AxisAngle4f()
                    ));
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) player.setSneaking(false);
                anchor.getPersistentDataContainer().set(trapStateKey, PersistentDataType.STRING, "armed");
                loc.getWorld().playSound(loc, Sound.BLOCK_LEVER_CLICK, 0.9f, 0.7f);
                player.sendMessage(ChatColor.GREEN + "Капкан успешно взведен!");
            }
        }.runTaskLater(this, 10L);
    }

    private void startDetectionTask() {
        new BukkitRunnable() {
            int baitTimer = 0;

            @Override
            public void run() {
                baitTimer++;
                boolean runBaitLogic = (baitTimer % 10 == 0); // Каждые 2 секунды (40 тиков) пересчитываем ИИ приманки

                for (World world : Bukkit.getWorlds()) {
                    for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                        if (display.getPersistentDataContainer().has(trapPartKey, PersistentDataType.STRING)) {
                            String part = display.getPersistentDataContainer().get(trapPartKey, PersistentDataType.STRING);
                            String state = display.getPersistentDataContainer().get(trapStateKey, PersistentDataType.STRING);
                            
                            if ("trigger_anchor".equals(part) && "armed".equals(state)) {
                                Location loc = display.getLocation();
                                
                                // А) ЛОГИКА ПРИМАНКИ (Привлечение зомби)
                                if (runBaitLogic && display.getPersistentDataContainer().has(trapBaitKey, PersistentDataType.BYTE)) {
                                    // Визуальные споры запаха мяса
                                    loc.getWorld().spawnParticle(Particle.SNEEZE, loc.clone().add(0, 0.1, 0), 3, 0.2, 0.0, 0.2, 0.01);
                                    
                                    // Агрим зомби в радиусе 16 блоков
                                    for (Entity nearby : loc.getWorld().getNearbyEntities(loc, 16.0, 5.0, 16.0)) {
                                        if (nearby instanceof Zombie zombie) {
                                            // Если у зомби нет цели или он идет за игроком — переманиваем его на точку капкана
                                            zombie.getPathfinder().moveTo(loc, 1.2);
                                        }
                                    }
                                }

                                // Б) ОБЫЧНАЯ ДЕТЕКЦИЯ НАСТУПАНИЯ
                                for (Entity entity : loc.getWorld().getNearbyEntities(loc, 0.5, 0.85, 0.5)) {
                                    if (entity instanceof LivingEntity && !(entity instanceof ArmorStand)) {
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
        }.runTaskTimer(this, 0L, 4L);
    }

    private void triggerTrap(ItemDisplay anchor, LivingEntity victim) {
        String id = anchor.getPersistentDataContainer().get(trapIdKey, PersistentDataType.STRING);
        anchor.getPersistentDataContainer().set(trapStateKey, PersistentDataType.STRING, "triggered");

        Location loc = anchor.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.0f, 0.5f);
        loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.9f, 1.8f);
        loc.getWorld().spawnParticle(Particle.CRIT, loc.clone().add(0, 0.2, 0), 20, 0.1, 0.1, 0.1, 0.1);

        // Очищаем приманку из данных и удаляем её 3D модель
        if (anchor.getPersistentDataContainer().has(trapBaitKey, PersistentDataType.BYTE)) {
            anchor.getPersistentDataContainer().remove(trapBaitKey);
        }

        // Анимация моментального схлопывания деталей
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 2.0, 2.0, 2.0)) {
            if (entity instanceof ItemDisplay part && id.equals(part.getPersistentDataContainer().get(trapIdKey, PersistentDataType.STRING))) {
                String pName = part.getPersistentDataContainer().get(trapPartKey, PersistentDataType.STRING);
                
                if ("left".equals(pName) || "right".equals(pName)) {
                    part.setInterpolationDuration(3);
                    part.setInterpolationDelay(0);
                    
                    Vector3f currentTrans = part.getTransformation().getTranslation();
                    Vector3f currentScale = part.getTransformation().getScale();
                    
                    float newX = "left".equals(pName) ? -0.04f : 0.04f;
                    float newAngle = "left".equals(pName) ? -4f : 4f;
                    
                    part.setTransformation(new Transformation(
                            new Vector3f(newX, 0.18f, currentTrans.z), 
                            new AxisAngle4f((float) Math.toRadians(newAngle), 0, 0, 1), 
                            currentScale,
                            new AxisAngle4f()
                    ));
                } else if ("bait".equals(pName)) {
                    // Кусочек плоти уничтожается ("съедается" капканом)
                    part.remove();
                }
            }
        }

        // Нанесение урона
        victim.damage(8.0); 
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 140, 5, false, true));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
    }

    private ItemDisplay findAnchor(World world, String id) {
        for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
            if (id.equals(display.getPersistentDataContainer().get(trapIdKey, PersistentDataType.STRING))) {
                if ("trigger_anchor".equals(display.getPersistentDataContainer().get(trapPartKey, PersistentDataType.STRING))) {
                    return display;
                }
            }
        }
        return null;
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
        if (cmd.getName().equalsIgnoreCase("cleartraps") && sender instanceof Player player) {
            double radius = 10.0;
            if (args.length > 0) {
                try { radius = Double.parseDouble(args[0]); } catch (NumberFormatException e) { return false; }
            }

            int removedCount = 0;
            Location center = player.getLocation();
            
            if (center.getWorld() != null) {
                for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                    if (entity instanceof ItemDisplay display) {
                        if (display.getPersistentDataContainer().has(trapPartKey, PersistentDataType.STRING) &&
                            "trigger_anchor".equals(display.getPersistentDataContainer().get(trapPartKey, PersistentDataType.STRING))) {
                            
                            String id = display.getPersistentDataContainer().get(trapIdKey, PersistentDataType.STRING);
                            removeTrapEntities(player.getWorld(), id);
                            removedCount++;
                        }
                    }
                }
            }
            player.sendMessage(ChatColor.GREEN + "Капканов удалено: " + removedCount);
            return true;
        }
        return false;
    }
}
