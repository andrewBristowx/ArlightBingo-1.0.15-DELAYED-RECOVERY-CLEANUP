package com.arlight.bingo.listeners;

import com.arlight.bingo.game.BingoGame;
import com.arlight.bingo.game.GameState;
import com.arlight.bingo.util.ArenaWorldManager;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Permite crear un cartel con primera linea "[Bingo]" para que los jugadores
 * hagan click derecho y se unan a la partida (y sean teletransportados al
 * lobby o a la arena segun el estado actual).
 */
public class SignListener implements Listener {

    private static final String TAG = "[Bingo]";

    private final BingoGame game;
    private final ArenaWorldManager multiverse;

    public SignListener(BingoGame game, ArenaWorldManager multiverse) {
        this.game = game;
        this.multiverse = multiverse;
    }

    @EventHandler
    public void onSignCreate(SignChangeEvent event) {
        if (!TAG.equalsIgnoreCase(event.getLine(0))) return;

        if (!event.getPlayer().hasPermission("arlightbingo.admin")) {
            event.setLine(0, ChatColor.RED + "no-perm");
            event.getPlayer().sendMessage(ChatColor.RED + "No tienes permiso para crear carteles de Bingo.");
            return;
        }

        event.setLine(0, ChatColor.GOLD + TAG);
        event.getPlayer().sendMessage(ChatColor.GREEN + "Cartel de Bingo creado. Click derecho para unirse/entrar.");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!(block.getState() instanceof Sign)) return;

        Sign sign = (Sign) block.getState();
        if (!TAG.equalsIgnoreCase(ChatColor.stripColor(sign.getLine(0)))) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (game.getState() == GameState.RUNNING) {
            if (game.getTeamOf(player) != null && game.getCurrentArenaWorld() != null && multiverse != null) {
                if (!multiverse.teleportToWorld(player, game.getCurrentArenaWorld())) {
                    player.sendMessage(ChatColor.RED + "No se pudo entrar a la arena (mundo no cargado).");
                }
            } else {
                player.sendMessage(ChatColor.RED + "La partida ya empezo y no estas anotado.");
            }
            return;
        }

        game.addPlayer(player, null);
    }
}
