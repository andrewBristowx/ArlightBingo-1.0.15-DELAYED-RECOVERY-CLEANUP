package com.arlight.bingo.util;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.StructureSearchResult;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Ajusta el borde mediante un radio base y claves de estructuras configurables.
 * Usa el registro moderno para poder localizar estructuras vanilla y modded.
 */
public class WorldBorderManager {

    /**
     * Se genera un chunk por tick para reducir la carga en Arclight.
     */
    private static final int CHUNKS_POR_TICK = 1;

    /**
     * Radios de los bordes.
     *
     * El diámetro final será el doble del radio.
     */
    private static final int RADIO_OVERWORLD = 4096;
    private static final int RADIO_NETHER = 2048;
    private static final int RADIO_END = 4096;

    private final JavaPlugin plugin;

    public WorldBorderManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Aplica un borde fijo dependiendo de la dimensión y, si está habilitado,
     * pregenera una zona pequeña cerca del centro.
     */
    public CompletableFuture<Void> applyAdaptiveBorder(
            World world,
            ConfigManager cfg
    ) {
        if (!cfg.isWorldBorderEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        int configuredMinimumRadius =
                Math.max(1, cfg.getWorldBorderMinSize() / 2);

        int neededRadius;

        switch (world.getEnvironment()) {
            case NORMAL:
                neededRadius = Math.max(
                        configuredMinimumRadius,
                        RADIO_OVERWORLD
                );
                break;

            case NETHER:
                neededRadius = Math.max(
                        configuredMinimumRadius,
                        RADIO_NETHER
                );
                break;

            case THE_END:
                neededRadius = Math.max(
                        configuredMinimumRadius,
                        RADIO_END
                );
                break;

            default:
                neededRadius = configuredMinimumRadius;
                break;
        }

        // Amplía el radio base si una estructura configurada cayó más lejos.
        // Si Arclight no expone una estructura modded al registro Bukkit, se conserva
        // el radio fijo y la preparación de la partida continúa normalmente.
        neededRadius = locateConfiguredStructures(world, cfg, neededRadius);

        int maxRadius = Math.max(
                1,
                cfg.getWorldBorderMaxSize() / 2
        );

        int finalRadius = Math.min(
                neededRadius,
                maxRadius
        );

        if (finalRadius < neededRadius) {
            plugin.getLogger().warning(
                    "El borde solicitado para "
                            + world.getName()
                            + " era de "
                            + (neededRadius * 2)
                            + " bloques de diámetro, pero fue limitado a "
                            + (finalRadius * 2)
                            + " por world-border.max-size="
                            + cfg.getWorldBorderMaxSize()
                            + "."
            );
        }

        world.getWorldBorder().setCenter(0.0, 0.0);
        world.getWorldBorder().setSize(finalRadius * 2.0);

        plugin.getLogger().info(
                "Borde de "
                        + world.getName()
                        + " establecido en "
                        + (finalRadius * 2)
                        + " bloques de diámetro."
        );

        if (!cfg.isWorldBorderPregenerate()) {
            plugin.getLogger().info(
                    "Pregeneración desactivada para "
                            + world.getName()
                            + "."
            );

            return CompletableFuture.completedFuture(null);
        }

        int configuredPregenerationRadius = Math.max(
                0,
                cfg.getWorldBorderPregenerateRadius()
        );

        int pregenerationRadius = Math.min(
                finalRadius,
                configuredPregenerationRadius
        );

        if (pregenerationRadius <= 0) {
            plugin.getLogger().info(
                    "No se pregenerarán chunks en "
                            + world.getName()
                            + " porque el radio de pregeneración es 0."
            );

            return CompletableFuture.completedFuture(null);
        }

        return pregenerateCore(
                world,
                pregenerationRadius
        );
    }

    private int locateConfiguredStructures(World world, ConfigManager cfg, int currentRadius) {
        List<String> keys;
        switch (world.getEnvironment()) {
            case NORMAL:
                if (!cfg.isLocateStronghold()) return currentRadius;
                keys = cfg.getOverworldStructureKeys();
                break;
            case NETHER:
                if (!cfg.isLocateNetherStructures()) return currentRadius;
                keys = cfg.getNetherStructureKeys();
                break;
            case THE_END:
                if (!cfg.isLocateEndCity()) return currentRadius;
                keys = cfg.getEndStructureKeys();
                break;
            default:
                return currentRadius;
        }

        Location origin = world.getSpawnLocation();
        int neededRadius = currentRadius;
        for (String rawKey : keys) {
            try {
                NamespacedKey key = NamespacedKey.fromString(rawKey);
                if (key == null) {
                    plugin.getLogger().warning("Clave de estructura inválida: " + rawKey);
                    continue;
                }

                Structure structure = Registry.STRUCTURE.get(key);
                if (structure == null) {
                    plugin.getLogger().warning("La estructura " + rawKey
                            + " no está disponible en el registro de " + world.getName() + ".");
                    continue;
                }

                plugin.getLogger().info("Buscando " + rawKey + " en " + world.getName() + "...");
                // La API moderna recibe el radio en chunks, mientras que config.yml
                // lo expresa en bloques. Además, no buscamos más allá de lo que el
                // borde máximo podría incluir; esto evita búsquedas gigantes.
                int usefulBlockRadius = Math.min(cfg.getWorldBorderSearchRadius(),
                        Math.max(16, cfg.getWorldBorderMaxSize() / 2));
                int searchRadiusChunks = Math.max(1,
                        (int) Math.ceil(usefulBlockRadius / 16.0));
                StructureSearchResult result = world.locateNearestStructure(
                        origin, structure, searchRadiusChunks, false);
                if (result == null) {
                    plugin.getLogger().warning("No se encontró " + rawKey + " en " + world.getName()
                            + " dentro del radio de búsqueda configurado.");
                    continue;
                }

                Location found = result.getLocation();
                int distanceFromBorderCenter = (int) Math.ceil(Math.max(
                        Math.abs(found.getX()), Math.abs(found.getZ())));
                neededRadius = Math.max(neededRadius,
                        distanceFromBorderCenter + cfg.getWorldBorderPadding());
                plugin.getLogger().info("Estructura " + rawKey + " encontrada en "
                        + found.getBlockX() + ", " + found.getBlockY() + ", "
                        + found.getBlockZ() + ".");
                if (plugin.getConfig().getBoolean(
                        "world-border.announce-structure-coordinates", true)) {
                    Bukkit.broadcastMessage(ChatColor.GOLD + "[Bingo] "
                            + ChatColor.AQUA + displayStructureName(rawKey)
                            + ChatColor.GRAY + " encontrada en "
                            + ChatColor.YELLOW + "X: " + found.getBlockX()
                            + ", Y: " + found.getBlockY()
                            + ", Z: " + found.getBlockZ()
                            + ChatColor.GRAY + " (" + displayEnvironment(world) + ").");
                }
            } catch (Throwable error) {
                plugin.getLogger().warning("No se pudo localizar " + rawKey + " en "
                        + world.getName() + ": " + error.getClass().getSimpleName()
                        + ": " + error.getMessage());
            }
        }
        return neededRadius;
    }

    private String displayStructureName(String key) {
        return switch (key.toLowerCase()) {
            case "betterstrongholds:stronghold" -> "Stronghold de YUNG";
            case "betterfortresses:fortress" -> "Fortaleza del Nether de YUNG";
            case "minecraft:end_city" -> "Ciudad del End";
            default -> key;
        };
    }

    private String displayEnvironment(World world) {
        return switch (world.getEnvironment()) {
            case NORMAL -> "Overworld";
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> world.getName();
        };
    }

    /**
     * Pregenera los chunks progresivamente en el hilo principal.
     *
     * Arclight no implementa World#getChunkAtAsync(), por lo que se usa
     * getChunkAt(). Solo se procesa un chunk por tick para reducir la carga.
     */
    private CompletableFuture<Void> pregenerateCore(
            World world,
            int radius
    ) {
        CompletableFuture<Void> completion =
                new CompletableFuture<>();

        int chunkRadius = (int) Math.ceil(radius / 16.0);
        List<int[]> chunks = createChunkList(chunkRadius);

        int totalChunks = chunks.size();

        plugin.getLogger().info(
                "Pregenerando "
                        + totalChunks
                        + " chunks en "
                        + world.getName()
                        + " con un radio de "
                        + radius
                        + " bloques..."
        );

        new BukkitRunnable() {

            private int currentIndex = 0;

            @Override
            public void run() {
                try {
                    /*
                     * Comprobar que el plugin continúa habilitado.
                     */
                    if (!plugin.isEnabled()) {
                        completion.completeExceptionally(
                                new IllegalStateException(
                                        "El plugin fue deshabilitado durante "
                                                + "la pregeneración."
                                )
                        );

                        cancel();
                        return;
                    }

                    /*
                     * Comprobar que el mundo continúa cargado.
                     */
                    World loadedWorld = plugin.getServer().getWorld(
                            world.getName()
                    );

                    if (loadedWorld == null) {
                        completion.completeExceptionally(
                                new IllegalStateException(
                                        "El mundo "
                                                + world.getName()
                                                + " fue descargado durante "
                                                + "la pregeneración."
                                )
                        );

                        cancel();
                        return;
                    }

                    int generatedThisTick = 0;

                    while (currentIndex < totalChunks
                            && generatedThisTick < CHUNKS_POR_TICK) {

                        int[] coordinates =
                                chunks.get(currentIndex);

                        int chunkX = coordinates[0];
                        int chunkZ = coordinates[1];

                        /*
                         * Si el chunk todavía no está cargado, se genera
                         * o carga en el hilo principal.
                         */
                        if (!loadedWorld.isChunkLoaded(chunkX, chunkZ)) {
                            loadedWorld.getChunkAt(chunkX, chunkZ);
                        }

                        currentIndex++;
                        generatedThisTick++;
                    }

                    if (currentIndex >= totalChunks) {
                        plugin.getLogger().info(
                                "Pregeneración de "
                                        + world.getName()
                                        + " terminada. "
                                        + totalChunks
                                        + " chunks procesados."
                        );

                        completion.complete(null);
                        cancel();
                    }

                } catch (Throwable throwable) {
                    plugin.getLogger().severe(
                            "Error pregenerando chunks en "
                                    + world.getName()
                                    + ": "
                                    + throwable.getClass().getSimpleName()
                                    + ": "
                                    + throwable.getMessage()
                    );

                    throwable.printStackTrace();

                    completion.completeExceptionally(throwable);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return completion;
    }

    /**
     * Crea la lista de chunks comenzando desde el centro y avanzando hacia
     * el exterior. Esto permite que la zona inicial se prepare primero.
     */
    private List<int[]> createChunkList(int chunkRadius) {
        List<int[]> chunks = new ArrayList<>();

        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                chunks.add(new int[]{x, z});
            }
        }

        chunks.sort((first, second) -> {
            int firstDistance =
                    first[0] * first[0] + first[1] * first[1];

            int secondDistance =
                    second[0] * second[0] + second[1] * second[1];

            return Integer.compare(
                    firstDistance,
                    secondDistance
            );
        });

        return chunks;
    }
}
