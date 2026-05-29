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
        getLogger().info("BearTraps (Приподнятый с точным уроном) запущен!");
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

        // Установка капкана (ПКМ)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getItem() != null && event.getClickedBlock() != null) {
            ItemMeta meta = event.getItem().getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(trapKey, PersistentDataType.BYTE)) {
                event.setCancelled(true);
                
                // ИЗМЕНЕНИЕ: приподняли координату Y с -0.1 до 0.02, чтобы модель лежала на блоке
                Location spawnLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0.02, 0.5);
                spawnTrap(spawnLoc);
                
                if (player.getGameMode() != GameMode.CREATIVE) {
                    event.getItem().setAmount(event.getItem().getAmount() - 1);
                }
                player.playSound(spawnLoc, Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.8f);
                return;
            }
        }

        // Демонтаж рукой (ЛКМ)
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Location clickLoc = event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
            if (clickLoc.getWorld() != null) {
                for (Entity entity : clickLoc.getWorld().getNearbyEntities(clickLoc, 1.2, 1.2, 1.2)) {
                    if (entity instanceof ItemDisplay display) {
                        if (display.getPersistentDataContainer().has(trapIdKey, PersistentDataType.STRING)) {
                            String id = display.getPersistentDataContainer().get(trapIdKey, PersistentDataType.STRING);
                            String state = display.getPersistentDataContainer().get(trapStateKey, PersistentDataType.STRING);
                            
                            removeTrapEntities(display.getWorld(), id);
                            
                            if (player.getGameMode() == GameMode.CREATIVE || !"triggered".equals(state)) {
                                ItemStack trapItem = new ItemStack(Material.CHAIN);
                                ItemMeta m = trapItem.getItemMeta();
                                if (m != null) {
                                    m.setDisplayName(ChatColor.DARK_GRAY + "Медвежий капкан");
                                    m.getPersistentDataContainer().set(trapKey, PersistentDataType.BYTE, (byte) 1);
                                    trapItem.setItemMeta(m);
                                }
                                player.getInventory().addItem(trapItem);
                            }
                            
                            player.sendMessage(ChatColor.YELLOW + "Капкан убран.");
                            player.playSound(clickLoc, Sound.BLOCK_CHAIN_BREAK, 0.7f, 1.2f);
                            break;
                        }
                    }
                }
            }
        }
    }

    private void spawnTrap(Location loc) {
        String id = UUID.randomUUID().toString();

        // 1. КОРПУС ИЗ ЦЕПЕЙ (Рамка основания на земле)
        float[][] baseOffsets = {
            {0.0f, 0.01f, -0.3f, 0},
            {0.0f, 0.01f, 0.3f, 0},
            {-0.3f, 0.01f, 0.0f, 90},
            {0.3f, 0.01f, 0.0f, 90}
        };

        for (int i = 0; i < baseOffsets.length; i++) {
            ItemDisplay basePart = loc.getWorld().spawn(loc, ItemDisplay.class);
            basePart.setItemStack(new ItemStack(Material.CHAIN));
            basePart.getPersistentDataContainer().set(trapIdKey, PersistentDataType.STRING, id);
            basePart.getPersistentDataContainer().set(trapPartKey, PersistentDataType.STRING, i == 0 ? "trigger_anchor" : "static_base");
            if (i == 0) basePart.getPersistentDataContainer().set(trapStateKey, PersistentDataType.STRING, "armed");

            basePart.setTransformation(new Transformation(
                    new Vector3f(baseOffsets[i][0], baseOffsets[i][1], baseOffsets[i][2]),
                    new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0),
                    new Vector3f(0.8f, 0.15f, 0.8f),
                    new AxisAngle4f((float) Math.toRadians(baseOffsets[i][3]), 0, 0, 1)
            ));
        }

        // 2. ЗУБЬЯ И БОКОВЫЕ ЦЕПИ
        float[] teethZOffsets = {-0.25f, -0.1f, 0.1f, 0.25f};

        // Левая челюсть и её зубья
        spawnMovingPart(loc, id, "left", Material.CHAIN, new Vector3f(-0.2f, 0.05f, 0f), -65, new Vector3f(0.8f, 0.15f, 0.8f));
        for (float zOffset : teethZOffsets) {
            spawnMovingPart(loc, id, "left", Material.IRON_NUGGET, new Vector3f(-0.25f, 0.07f, zOffset), -65, new Vector3f(0.4f, 0.4f, 0.4f));
        }

        // Правая челюсть и её зубья
        spawnMovingPart(loc, id, "right", Material.CHAIN, new Vector3f(0.2f, 0.05f, 0f), 65, new Vector3f(0.8f, 0.15f, 0.8f));
        for (float zOffset : teethZOffsets) {
            spawnMovingPart(loc, id, "right", Material.IRON_NUGGET, new Vector3f(0.25f, 0.07f, zOffset), 65, new Vector3f(0.4f, 0.4f, 0.4f));
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

    private void startDetectionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                        if (display.getPersistentDataContainer().has(trapPartKey, PersistentDataType.STRING)) {
                            String part = display.getPersistentDataContainer().get(trapPartKey, PersistentDataType.STRING);
                            String state = display.getPersistentDataContainer().get(trapStateKey, PersistentDataType.STRING);
                            
                            if ("trigger_anchor".equals(part) && "armed".equals(state)) {
                                Location loc = display.getLocation();
                                // ИЗМЕНЕНИЕ: Увеличили зону детекции по высоте до 0.85, чтобы точно ловить наступающего игрока
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

        // Анимация моментального схлопывания зубьев и цепей в центр
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 2.0, 2.0, 2.0)) {
            if (entity instanceof ItemDisplay jaw) {
                if (id.equals(jaw.getPersistentDataContainer().get(trapIdKey, PersistentDataType.STRING))) {
                    String side = jaw.getPersistentDataContainer().get(trapPartKey, PersistentDataType.STRING);
                    
                    if ("left".equals(side) || "right".equals(side)) {
                        jaw.setInterpolationDuration(3);
                        jaw.setInterpolationDelay(0);
                        
                        Vector3f currentTrans = jaw.getTransformation().getTranslation();
                        Vector3f currentScale = jaw.getTransformation().getScale();
                        
                        float newX = "left".equals(side) ? -0.04f : 0.04f;
                        float newAngle = "left".equals(side) ? -4f : 4f;
                        
                        jaw.setTransformation(new Transformation(
                                new Vector3f(newX, 0.18f, currentTrans.z), 
                                new AxisAngle4f((float) Math.toRadians(newAngle), 0, 0, 1), 
                                currentScale,
                                new AxisAngle4f()
                        ));
                    }
                }
            }
        }

        // НАНОСИМ УРОН И НАКЛАДЫВАЕМ ЭФФЕКТЫ СХЛОПЫВАНИЯ
        victim.damage(8.0); // 8.0 единиц = 4 сердца чистого физического урона
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 140, 5, false, true)); // Замедление VI (почти обездвижен)
        victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false)); // Кратковременная слепота от шока

        // Удаление уничтоженного капкана через 3 секунды
        new BukkitRunnable() {
            @Override
            public void run() {
                removeTrapEntities(anchor.getWorld(), id);
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
