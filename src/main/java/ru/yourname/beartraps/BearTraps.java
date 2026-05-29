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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;

public class BearTraps extends JavaPlugin implements Listener, CommandExecutor {

    private final NamespacedKey TRAP_KEY = new NamespacedKey(this, "is_bear_trap");
    private final NamespacedKey TRAP_ID = new NamespacedKey(this, "trap_id");

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("cleartraps").setExecutor(this);
        registerRecipe();
        startDetectionTask();
    }

    private void registerRecipe() {
        ItemStack item = new ItemStack(Material.CHAIN);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GRAY + "Медвежий капкан");
        meta.setLore(java.util.List.of(ChatColor.GRAY + "Установи на землю, чтобы поймать добычу"));
        meta.getPersistentDataContainer().set(TRAP_KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);

        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, "trap_recipe"), item);
        recipe.shape("NNN", "NCN", "NNN");
        recipe.setIngredient('N', Material.IRON_NUGGET);
        recipe.setIngredient('C', Material.CHAIN);
        getServer().addRecipe(recipe);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Установка: ПКМ по блоку
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getItem() != null 
                && event.getItem().getItemMeta().getPersistentDataContainer().has(TRAP_KEY, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            Location loc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
            spawnTrap(loc);
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) event.getItem().subtract();
        }
        
        // Снятие: ЛКМ по капкану
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            removeTrapsAt(event.getClickedBlock().getLocation().add(0, 1, 0), 1.0);
        }
    }

    private void spawnTrap(Location loc) {
        String id = UUID.randomUUID().toString();
        spawnPart(loc, Material.CHAIN, 0, -0.4f, 1f, 0.5f, 1f, id); 
        spawnPart(loc, Material.IRON_NUGGET, -0.3f, -0.3f, 1.5f, 1.5f, 1.5f, id); 
        spawnPart(loc, Material.IRON_NUGGET, 0.3f, -0.3f, 1.5f, 1.5f, 1.5f, id);
    }

    private void spawnPart(Location loc, Material mat, float x, float y, float sx, float sy, float sz, String id) {
        ItemDisplay display = loc.getWorld().spawn(loc, ItemDisplay.class);
        display.setItemStack(new ItemStack(mat));
        display.getPersistentDataContainer().set(TRAP_ID, PersistentDataType.STRING, id);
        display.setTransformation(new Transformation(new Vector3f(x, y, 0), new AxisAngle4f(), new Vector3f(sx, sy, sz), new AxisAngle4f()));
    }

    private void startDetectionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                        if (display.getPersistentDataContainer().has(TRAP_ID, PersistentDataType.STRING)) {
                            Location loc = display.getLocation();
                            for (Entity e : loc.getWorld().getNearbyEntities(loc, 0.7, 0.7, 0.7)) {
                                if (e instanceof LivingEntity && !(e instanceof ArmorStand)) {
                                    triggerTrap(loc, display.getPersistentDataContainer().get(TRAP_ID, PersistentDataType.STRING));
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0, 10);
    }

    private void triggerTrap(Location loc, String id) {
        loc.getWorld().playSound(loc, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1, 0.5f);
        loc.getWorld().spawnParticle(Particle.CRIT, loc, 30);
        loc.getWorld().getNearbyEntities(loc, 1, 1, 1).stream()
                .filter(e -> e instanceof LivingEntity).forEach(e -> ((LivingEntity) e).damage(8));
        removeTrapsById(id);
    }

    private void removeTrapsById(String id) {
        for (World world : Bukkit.getWorlds()) {
            world.getEntitiesByClass(ItemDisplay.class).stream()
                    .filter(d -> id.equals(d.getPersistentDataContainer().get(TRAP_ID, PersistentDataType.STRING)))
                    .forEach(Entity::remove);
        }
    }

    private void removeTrapsAt(Location loc, double radius) {
        loc.getWorld().getEntitiesByClass(ItemDisplay.class).stream()
                .filter(d -> d.getPersistentDataContainer().has(TRAP_ID, PersistentDataType.STRING) && d.getLocation().distance(loc) <= radius)
                .forEach(d -> {
                    d.remove();
                    d.getWorld().dropItemNaturally(d.getLocation(), new ItemStack(Material.CHAIN));
                });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player p) {
            double radius = (args.length > 0) ? Double.parseDouble(args[0]) : 10;
            removeTrapsAt(p.getLocation(), radius);
            p.sendMessage(ChatColor.GREEN + "Капканы очищены!");
        }
        return true;
    }
}
