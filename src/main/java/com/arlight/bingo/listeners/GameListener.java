package com.arlight.bingo.listeners;

import com.arlight.bingo.game.BingoGame;
import com.arlight.bingo.game.GameState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;

/**
 * Maneja tres cosas independientes de la logica del juego en si:
 * - Si un jugador muere en la arena (o en uno de sus mundos extra, nether/end), respawnea
 *   en el spawn del mundo lobby en vez del mundo por defecto del servidor.
 * - Cada vez que un jugador ENTRA a la arena (o a un mundo extra), lo deja en modo
 *   supervivencia, le quita cualquier efecto de pocion activo, y le resetea la vida
 *   maxima/actual a la vanilla (20 corazones llenos) -- asi todos arrancan parejos.
 * - Si un jugador se desconecta durante una partida en curso, lo descalifica.
 */
public class GameListener implements Listener {

    private final BingoGame game;

    public GameListener(BingoGame game) {
        this.game = game;
    }

    /**
     * Prioridad HIGHEST: en un servidor hibrido (Arclight/NeoForge) puede haber otros
     * plugins/mods escuchando este mismo evento; corriendo en HIGHEST nos aseguramos de
     * que nuestro cambio de ubicacion de respawn sea el que quede (se aplica al final).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!isArenaRelatedWorld(player.getWorld().getName())) return;

        String lobby = game.getConfigManager().getLobbyWorld();
        if (lobby == null || lobby.isEmpty()) return;

        World lobbyWorld = Bukkit.getWorld(lobby);
        if (lobbyWorld == null) {
            Bukkit.getLogger().warning("[ArlightBingo] No se pudo respawnear en el lobby '" + lobby
                    + "' porque ese mundo no esta cargado.");
            return;
        }
        event.setRespawnLocation(lobbyWorld.getSpawnLocation());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!isArenaRelatedWorld(player.getWorld().getName())) return;

        player.setGameMode(GameMode.SURVIVAL);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        var maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(20.0);
        }
        player.setHealth(20.0);
        player.setFoodLevel(20);
        // OJO: a proposito NO llamamos setSaturation() aca. Forzar una saturacion alta
        // en cada entrada al mundo hace que el HUD de "Saturacion" del cliente quede
        // pegado en pantalla (no es un efecto de pocion real, es el indicador vanilla
        // de saturacion de hambre) -- dejamos que el juego la maneje solo.
        player.setFireTicks(0);
    }

    /**
     * Conecta los tres mundos nativos del Bingo sin depender de Multiverse-NetherPortals.
     * Como el jugador nunca cambia de servidor ni de perfil, conserva exactamente el
     * mismo inventario al cruzar entre Overworld, Nether y End.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaPortal(PlayerPortalEvent event) {
        String arenaName = game.getCurrentArenaWorld();
        if (arenaName == null || !isArenaRelatedWorld(event.getFrom().getWorld().getName())) return;

        World overworld = Bukkit.getWorld(arenaName);
        World nether = findExtraWorld(World.Environment.NETHER);
        World end = findExtraWorld(World.Environment.THE_END);
        World from = event.getFrom().getWorld();
        if (overworld == null || from == null) return;

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL && nether != null) {
            if (from.getEnvironment() == World.Environment.NORMAL) {
                event.setTo(scaledPortalLocation(event.getFrom(), nether, 1.0 / 8.0));
            } else if (from.getEnvironment() == World.Environment.NETHER) {
                event.setTo(scaledPortalLocation(event.getFrom(), overworld, 8.0));
            }
            event.setCanCreatePortal(true);
            return;
        }

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL && end != null) {
            if (from.getEnvironment() == World.Environment.THE_END) {
                event.setTo(overworld.getSpawnLocation());
            } else {
                org.bukkit.Location safeArrival = game.getEndEncounterArrival(end);
                event.setTo(safeArrival != null ? safeArrival : end.getSpawnLocation());
            }
        }
    }

    private World findExtraWorld(World.Environment environment) {
        for (String name : game.getConfigManager().getExtraArenaWorlds()) {
            World world = Bukkit.getWorld(name);
            if (world != null && world.getEnvironment() == environment) return world;
        }
        return null;
    }

    private org.bukkit.Location scaledPortalLocation(
            org.bukkit.Location source,
            World destination,
            double scale
    ) {
        double border = destination.getWorldBorder().getSize() / 2.0 - 16.0;
        double x = Math.max(-border, Math.min(border, source.getX() * scale));
        double z = Math.max(-border, Math.min(border, source.getZ() * scale));
        return new org.bukkit.Location(destination, x, source.getY(), z, source.getYaw(), source.getPitch());
    }

    /** Si alguien se desconecta durante una partida en curso, se lo descalifica. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        game.disqualifyIfInMatch(event.getPlayer());
    }

    /**
     * Mientras un jugador esta jugando una partida en curso, solo se le permite usar el
     * comando /bingo (cuyos subcomandos ya estan a su vez restringidos a "arena" y "leave"
     * por BingoCommand para jugadores no-admin) -- cualquier otro comando de otro plugin
     * queda bloqueado, para que no puedan usar /home, /tpa, etc. para hacer trampa.
     * Los admins (permiso arlightbingo.admin) quedan exentos de esta restriccion.
     */
    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (game.getState() != GameState.RUNNING) return;
        if (game.getTeamOf(player) == null) return;
        if (player.hasPermission("arlightbingo.admin")) return;

        String message = event.getMessage().toLowerCase();
        if (message.startsWith("/bingo")) return;

        event.setCancelled(true);
        player.sendMessage(org.bukkit.ChatColor.RED + "Durante la partida solo podes usar comandos de /bingo.");
    }

    private boolean isArenaRelatedWorld(String worldName) {
        String arena = game.getCurrentArenaWorld();
        if (arena != null && worldName.equalsIgnoreCase(arena)) return true;
        for (String extra : game.getConfigManager().getExtraArenaWorlds()) {
            if (worldName.equalsIgnoreCase(extra)) return true;
        }
        return false;
    }
}
