package com.arlight.bingo.listeners;

import com.arlight.bingo.game.*;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.util.List;

/**
 * Objetivos que se detectan al momento con un evento puntual: romper/colocar
 * bloques, matar entidades, completar logros. NOTA: ITEM_COLLECT y CRAFT_ITEM
 * NO se manejan aqui -- se detectan con un escaneo periodico del inventario
 * (ver BingoGame/InventoryScanTask), porque depender de eventos de "recoger
 * del suelo" o "craftear en la mesa" resulta poco confiable (items que llegan
 * por hornos, cofres, trueques, mods con recogida automatica, etc. no
 * disparan esos eventos).
 */
public class GoalListener implements Listener {

    private final BingoGame game;

    public GoalListener(BingoGame game) {
        this.game = game;
    }

    /**
     * Convierte el namespaced key de un bloque/entidad/logro a texto
     * (ej. "minecraft:diamond" o "modid:custom_item").
     */
    private String keyOf(NamespacedKey key) {
        return key.getNamespace() + ":" + key.getKey();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (game.getState() != GameState.RUNNING) return;
        Player player = event.getPlayer();
        BingoTeam team = game.getTeamOf(player);
        if (team == null || team.getCard() == null) return;

        String key = keyOf(event.getBlock().getType().getKey());
        applyProgress(team, GoalType.BLOCK_BREAK, key, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (game.getState() != GameState.RUNNING) return;
        Player player = event.getPlayer();
        BingoTeam team = game.getTeamOf(player);
        if (team == null || team.getCard() == null) return;

        String key = keyOf(event.getBlock().getType().getKey());
        applyProgress(team, GoalType.BLOCK_PLACE, key, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (game.getState() != GameState.RUNNING) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        BingoTeam team = game.getTeamOf(killer);
        if (team == null || team.getCard() == null) return;

        String key = keyOf(event.getEntityType().getKey());
        applyProgress(team, GoalType.ENTITY_KILL, key, 1);
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (game.getState() != GameState.RUNNING) return;
        BingoTeam team = game.getTeamOf(event.getPlayer());
        if (team == null || team.getCard() == null) return;

        Advancement adv = event.getAdvancement();
        String key = adv.getKey().getNamespace() + ":" + adv.getKey().getKey();
        applyProgress(team, GoalType.ADVANCEMENT, key, 1);
    }

    private void applyProgress(BingoTeam team, GoalType type, String key, int amount) {
        List<BingoGoal> matches = team.getCard().findByTarget(type, key);
        for (BingoGoal goal : matches) {
            boolean justCompleted = goal.addProgress(amount);
            if (justCompleted) {
                game.onGoalCompleted(team, goal);
            }
        }
    }
}
