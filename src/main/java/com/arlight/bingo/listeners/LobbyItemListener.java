package com.arlight.bingo.listeners;

import com.arlight.bingo.game.BingoGame;
import com.arlight.bingo.gui.InfoGUI;
import com.arlight.bingo.gui.LobbyItems;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class LobbyItemListener implements Listener {

    private final BingoGame game;
    private final JavaPlugin plugin;

    public LobbyItemListener(BingoGame game, JavaPlugin plugin) {
        this.game = game;
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        if (LobbyItems.isInfoItem(plugin, item)) {
            event.setCancelled(true);
            player.openInventory(InfoGUI.build(plugin, game.getConfigManager()));
            return;
        }

        if (LobbyItems.isLeaveItem(plugin, item)) {
            event.setCancelled(true);
            if (game.getTeamOf(player) == null) return;
            game.removePlayer(player);
            player.sendMessage(ChatColor.YELLOW + "Saliste de la sala de espera del bingo.");
        }
    }

    @EventHandler
    public void onClickInfoGUI(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(InfoGUI.TITLE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDragInfoGUI(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(InfoGUI.TITLE)) {
            event.setCancelled(true);
        }
    }
}
