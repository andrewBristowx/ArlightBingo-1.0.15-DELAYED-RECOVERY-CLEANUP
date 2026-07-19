package com.arlight.bingo.end;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controla el encuentro especial del End del Bingo sin depender de EndDragonFight.
 * Los mundos End personalizados de Arclight pueden generar la isla de YUNG sin
 * inicializar la batalla vanilla; por eso el dragón y los gateways se gestionan aquí.
 */
public class EndEncounterManager implements Listener {

    private final JavaPlugin plugin;

    private String activeWorldName;
    private UUID activeDragonId;
    private Location centralGateway;
    private Location rewardGateway;
    private Location rewardArrival;
    private Location centralReturn;
    private boolean gatewayUnlocked;
    private long encounterGeneration;
    private Location dragonSpawn;
    private BukkitTask dragonMonitorTask;
    private boolean endInitializedForPlayers;
    private final Map<UUID, Long> gatewayCooldowns = new HashMap<>();

    public EndEncounterManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Prepara una partida nueva dentro del mundo End indicado. */
    public void prepare(World world) {
        resetState();
        if (!plugin.getConfig().getBoolean("end-encounter.enabled", true)) {
            plugin.getLogger().info("El encuentro especial del End está desactivado en config.yml.");
            return;
        }
        if (world == null || world.getEnvironment() != World.Environment.THE_END) {
            return;
        }

        this.activeWorldName = world.getName();
        long generation = ++encounterGeneration;

        Location configuredCenter = configuredLocation(world, "end-encounter.central-gateway", 0, 90, 0);
        this.centralGateway = liftAboveTerrain(configuredCenter, 5);
        this.centralReturn = centralGateway.clone().add(0, 2, 4);
        this.rewardGateway = configuredLocation(world, "end-encounter.reward-gateway", 1500, 90, 0);
        this.rewardArrival = rewardGateway.clone().add(0, 2, 4);

        // Mantiene cargado el centro durante toda la pelea. Algunos mods descargan el
        // chunk antes de que la tarea retrasada alcance a crear el dragón.
        world.getChunkAt(centralGateway).setForceLoaded(true);

        removeOldDragons(world);
        buildSafePlatform(centralGateway, Material.END_STONE_BRICKS, 5);
        buildRewardArea(rewardGateway);
        createLockedGatewayFrame(centralGateway);
        createGateway(rewardGateway);

        this.dragonSpawn = configuredLocation(world, "end-encounter.dragon-spawn", 0, 110, 0);
        this.dragonSpawn.setY(Math.max(dragonSpawn.getY(), centralGateway.getY() + 25.0));

        // No se genera todavía. Better End Island inicializa su DragonFight cuando el
        // primer jugador entra al End; si encuentra un dragón antes de crear su portal
        // de salida, lo elimina deliberadamente. onPlayerChangedWorld espera a que YUNG
        // termine y recién entonces crea y vigila nuestro dragón.
        // También iniciamos un monitor independiente del evento de cambio de mundo,
        // porque Arclight puede omitir PlayerChangedWorldEvent al cruzar dimensiones
        // nativas creadas dinámicamente.
        startDragonMonitor(world, generation);

        plugin.getLogger().info("Encuentro del End preparado en " + world.getName()
                + ". Plataforma central: " + formatLocation(centralGateway) + ".");
    }

    public void shutdown() {
        resetState();
    }

    /** Punto seguro usado al entrar desde el portal del Overworld. */
    public Location getSafeArrival(World world) {
        if (!isActiveWorld(world) || centralReturn == null) return null;
        return centralReturn.clone();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        World world = event.getPlayer().getWorld();
        if (!isActiveWorld(world) || gatewayUnlocked || endInitializedForPlayers) return;

        endInitializedForPlayers = true;
        long generation = encounterGeneration;
        plugin.getLogger().info("Jugador detectado en el End del Bingo. Esperando la inicialización "
                + "de YUNG's Better End Island antes de crear el dragón...");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isCurrentEncounter(world, generation) || gatewayUnlocked) return;
            spawnDragonWithRetry(world, dragonSpawn, generation, 1);
            startDragonMonitor(world, generation);
        }, 100L);
    }

    private void startDragonMonitor(World world, long generation) {
        if (dragonMonitorTask != null) dragonMonitorTask.cancel();
        dragonMonitorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isCurrentEncounter(world, generation) || gatewayUnlocked) {
                if (dragonMonitorTask != null) dragonMonitorTask.cancel();
                dragonMonitorTask = null;
                return;
            }

            // Mientras nadie haya entrado, no creamos el dragón: así Better End Island
            // puede preparar primero su altar y su estado interno de DragonFight.
            if (world.getPlayers().isEmpty()) return;

            Entity entity = activeDragonId == null ? null : Bukkit.getEntity(activeDragonId);
            if (!(entity instanceof EnderDragon) || !entity.isValid() || entity.isDead()) {
                plugin.getLogger().warning("El dragón del Bingo desapareció sin morir. Regenerándolo "
                        + "después de la inicialización de Better End Island.");
                activeDragonId = null;
                spawnDragonWithRetry(world, dragonSpawn, generation, 1);
            }
        }, 100L, 100L);
    }

    private void spawnDragonWithRetry(
            World world,
            Location location,
            long generation,
            int attempt
    ) {
        if (!isCurrentEncounter(world, generation) || !plugin.isEnabled()) return;

        Entity current = activeDragonId == null ? null : Bukkit.getEntity(activeDragonId);
        if (current instanceof EnderDragon && current.isValid() && !current.isDead()) return;

        try {
            EnderDragon dragon = (EnderDragon) world.spawnEntity(location, EntityType.ENDER_DRAGON);
            dragon.setCustomName(ChatColor.DARK_PURPLE + "Dragón del Bingo");
            dragon.setCustomNameVisible(true);
            dragon.setPersistent(true);
            dragon.setRemoveWhenFarAway(false);
            dragon.setPhase(EnderDragon.Phase.CIRCLING);
            activeDragonId = dragon.getUniqueId();

            plugin.getLogger().info("Ender Dragon del Bingo generado en " + world.getName()
                    + " (intento " + attempt + ", UUID " + activeDragonId + ").");

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isCurrentEncounter(world, generation)) return;
                Entity entity = activeDragonId == null ? null : Bukkit.getEntity(activeDragonId);
                if (entity instanceof EnderDragon && entity.isValid() && !entity.isDead()) {
                    if (attempt == 1) {
                        Bukkit.broadcastMessage(ChatColor.GOLD + "[Bingo] " + ChatColor.LIGHT_PURPLE
                                + "¡El Dragón del End protege el acceso a la ciudad!");
                    }
                    return;
                }

                plugin.getLogger().warning("Arclight o un mod eliminó el Dragón del Bingo después de "
                        + "generarlo. Se intentará nuevamente.");
                retryDragon(world, location, generation, attempt);
            }, 40L);
        } catch (Throwable error) {
            plugin.getLogger().severe("No se pudo generar el Dragón del Bingo (intento " + attempt
                    + "): " + error.getClass().getName() + ": " + error.getMessage());
            retryDragon(world, location, generation, attempt);
        }
    }

    private void retryDragon(World world, Location location, long generation, int attempt) {
        if (attempt >= 5) {
            plugin.getLogger().severe("El Dragón del Bingo no pudo mantenerse generado después de 5 intentos.");
            Bukkit.broadcastMessage(ChatColor.RED + "[Bingo] No se pudo generar el dragón. "
                    + "Revisa latest.log para ver qué mod lo está bloqueando.");
            return;
        }
        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> spawnDragonWithRetry(world, location, generation, attempt + 1),
                40L
        );
    }

    private void removeOldDragons(World world) {
        for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
            dragon.remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) return;
        if (activeDragonId == null || !activeDragonId.equals(dragon.getUniqueId())) return;
        if (!isActiveWorld(dragon.getWorld())) return;

        gatewayUnlocked = true;
        if (dragonMonitorTask != null) {
            dragonMonitorTask.cancel();
            dragonMonitorTask = null;
        }
        createGateway(centralGateway);

        Bukkit.broadcastMessage(ChatColor.GOLD + "[Bingo] " + ChatColor.GREEN
                + "¡El dragón fue derrotado! El gateway hacia la ciudad del End está abierto.");
        plugin.getLogger().info("Gateway de recompensa habilitado en " + activeWorldName + ".");
    }

    /**
     * Intercepta el intento de teletransporte del bloque END_GATEWAY y establece
     * destinos seguros. Así no dependemos del NBT vanilla del gateway en Arclight.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGatewayTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.END_GATEWAY) return;
        if (activeWorldName == null || event.getFrom().getWorld() == null) return;
        if (!event.getFrom().getWorld().getName().equalsIgnoreCase(activeWorldName)) return;

        if (centralGateway != null && event.getFrom().distanceSquared(centralGateway) <= 16.0) {
            if (!gatewayUnlocked) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "Primero debes derrotar al Dragón del Bingo.");
                return;
            }
            event.setTo(rewardArrival);
            return;
        }

        if (rewardGateway != null && event.getFrom().distanceSquared(rewardGateway) <= 16.0) {
            event.setTo(centralReturn);
        }
    }

    /**
     * Arclight no siempre activa el teletransporte de un END_GATEWAY colocado por
     * Bukkit, porque el bloque no contiene el NBT vanilla de destino. Detectamos al
     * jugador dentro del bloque y hacemos el viaje desde el plugin.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGatewayEnter(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || !isActiveWorld(to.getWorld())) return;

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (gatewayCooldowns.getOrDefault(player.getUniqueId(), 0L) > now) return;

        Location destination = null;
        if (isInsideGateway(to, centralGateway)) {
            if (!gatewayUnlocked) return;
            destination = rewardArrival;
        } else if (isInsideGateway(to, rewardGateway)) {
            destination = centralReturn;
        }

        if (destination == null || destination.getWorld() == null) return;
        gatewayCooldowns.put(player.getUniqueId(), now + 3000L);
        event.setTo(destination.clone());
        player.setFallDistance(0.0F);
        player.sendMessage(ChatColor.DARK_PURPLE + "El gateway del End te transportó.");
    }

    private boolean isInsideGateway(Location playerLocation, Location gateway) {
        if (gateway == null || gateway.getWorld() == null
                || playerLocation.getWorld() != gateway.getWorld()) return false;

        int gatewayX = gateway.getBlockX();
        int gatewayY = gateway.getBlockY();
        int gatewayZ = gateway.getBlockZ();
        int playerX = playerLocation.getBlockX();
        int playerY = playerLocation.getBlockY();
        int playerZ = playerLocation.getBlockZ();

        return playerX == gatewayX && playerZ == gatewayZ
                && (playerY == gatewayY || playerY + 1 == gatewayY);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProtectedBlockBreak(BlockBreakEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Esta estructura del Bingo está protegida.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProtectedExplosion(EntityExplodeEvent event) {
        Iterator<org.bukkit.block.Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            if (isProtected(iterator.next().getLocation())) iterator.remove();
        }
    }

    private boolean isProtected(Location location) {
        if (location.getWorld() == null || activeWorldName == null
                || !location.getWorld().getName().equalsIgnoreCase(activeWorldName)) {
            return false;
        }
        return (centralGateway != null && horizontalDistanceSquared(location, centralGateway) <= 64.0)
                || (rewardGateway != null && horizontalDistanceSquared(location, rewardGateway) <= 2500.0);
    }

    private double horizontalDistanceSquared(Location first, Location second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private void createGateway(Location center) {
        if (center == null || center.getWorld() == null) return;
        buildSafePlatform(center, Material.END_STONE_BRICKS, 3);
        center.getBlock().setType(Material.END_GATEWAY, false);
        center.clone().add(0, -1, 0).getBlock().setType(Material.BEDROCK, false);
        center.clone().add(0, 1, 0).getBlock().setType(Material.BEDROCK, false);
    }

    private void removeGateway(Location center) {
        if (center == null) return;
        center.getBlock().setType(Material.AIR, false);
    }

    /** Deja una estructura visible, pero sin bloque de portal hasta matar al dragón. */
    private void createLockedGatewayFrame(Location center) {
        removeGateway(center);
        for (int y = -2; y <= 2; y++) {
            center.clone().add(0, y, 0).getBlock().setType(y == 0 ? Material.AIR : Material.BEDROCK, false);
        }
        center.clone().add(1, 0, 0).getBlock().setType(Material.BEDROCK, false);
        center.clone().add(-1, 0, 0).getBlock().setType(Material.BEDROCK, false);
        center.clone().add(0, 0, 1).getBlock().setType(Material.BEDROCK, false);
        center.clone().add(0, 0, -1).getBlock().setType(Material.BEDROCK, false);
    }

    /** Construye una pequeña ciudad/torre de purpur y un barco con recompensa garantizada. */
    private void buildRewardArea(Location gateway) {
        World world = gateway.getWorld();
        if (world == null) return;

        // Fuerza únicamente el chunk de destino, no una búsqueda de estructuras.
        world.getChunkAt(gateway.getBlockX() >> 4, gateway.getBlockZ() >> 4);
        buildSafePlatform(gateway, Material.END_STONE_BRICKS, 12);
        createGateway(gateway);

        int baseX = gateway.getBlockX() + 14;
        int baseY = gateway.getBlockY();
        int baseZ = gateway.getBlockZ();

        // Torre compacta estilo ciudad del End.
        for (int y = 0; y <= 14; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    boolean wall = Math.abs(x) == 4 || Math.abs(z) == 4;
                    if (y == 0 || y == 7 || y == 14) {
                        world.getBlockAt(baseX + x, baseY + y, baseZ + z)
                                .setType(Material.PURPUR_BLOCK, false);
                    } else if (wall && ((x + z + y) & 1) == 0) {
                        world.getBlockAt(baseX + x, baseY + y, baseZ + z)
                                .setType(Material.PURPUR_PILLAR, false);
                    }
                }
            }
        }

        // Barco sencillo unido a la torre.
        int shipX = baseX + 16;
        int shipY = baseY + 11;
        for (int x = -7; x <= 7; x++) {
            int halfWidth = Math.max(1, 4 - Math.abs(x) / 2);
            for (int z = -halfWidth; z <= halfWidth; z++) {
                world.getBlockAt(shipX + x, shipY, baseZ + z).setType(Material.PURPUR_BLOCK, false);
            }
        }
        for (int y = 1; y <= 6; y++) {
            world.getBlockAt(shipX, shipY + y, baseZ).setType(Material.PURPUR_PILLAR, false);
        }

        Location chestLocation = new Location(world, shipX + 3, shipY + 1, baseZ);
        chestLocation.getBlock().setType(Material.CHEST, false);
        if (chestLocation.getBlock().getState() instanceof Chest chest) {
            chest.getBlockInventory().clear();
            chest.getBlockInventory().addItem(
                    new ItemStack(Material.ELYTRA),
                    new ItemStack(Material.SHULKER_SHELL, 4),
                    new ItemStack(Material.DIAMOND, 6)
            );
            chest.update(true);
        }

        world.spawnEntity(new Location(world, baseX, baseY + 1, baseZ + 2), EntityType.SHULKER);
        world.spawnEntity(new Location(world, shipX - 3, shipY + 1, baseZ), EntityType.SHULKER);
    }

    private void buildSafePlatform(Location center, Material material, int radius) {
        World world = center.getWorld();
        if (world == null) return;
        int y = center.getBlockY() - 1;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.getBlockAt(center.getBlockX() + x, y, center.getBlockZ() + z)
                        .setType(material, false);
                world.getBlockAt(center.getBlockX() + x, y + 1, center.getBlockZ() + z)
                        .setType(Material.AIR, false);
                world.getBlockAt(center.getBlockX() + x, y + 2, center.getBlockZ() + z)
                        .setType(Material.AIR, false);
            }
        }
    }

    private Location configuredLocation(World world, String path, int x, int y, int z) {
        return new Location(
                world,
                plugin.getConfig().getInt(path + ".x", x) + 0.5,
                plugin.getConfig().getInt(path + ".y", y),
                plugin.getConfig().getInt(path + ".z", z) + 0.5
        );
    }

    /** Sitúa la plataforma por encima de estructuras de YUNG en lugar de atravesarlas. */
    private Location liftAboveTerrain(Location configured, int clearance) {
        World world = configured.getWorld();
        if (world == null) return configured;
        int highest = world.getHighestBlockYAt(configured.getBlockX(), configured.getBlockZ());
        configured.setY(Math.max(configured.getY(), highest + clearance));
        return configured;
    }

    private boolean isCurrentEncounter(World world, long generation) {
        return generation == encounterGeneration
                && isActiveWorld(world)
                && Bukkit.getWorld(world.getName()) == world;
    }

    private String formatLocation(Location location) {
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    private boolean isActiveWorld(World world) {
        return world != null && activeWorldName != null
                && activeWorldName.equalsIgnoreCase(world.getName());
    }

    private void resetState() {
        if (dragonMonitorTask != null) {
            dragonMonitorTask.cancel();
            dragonMonitorTask = null;
        }
        encounterGeneration++;
        activeWorldName = null;
        activeDragonId = null;
        centralGateway = null;
        rewardGateway = null;
        rewardArrival = null;
        centralReturn = null;
        dragonSpawn = null;
        gatewayUnlocked = false;
        endInitializedForPlayers = false;
        gatewayCooldowns.clear();
    }
}
