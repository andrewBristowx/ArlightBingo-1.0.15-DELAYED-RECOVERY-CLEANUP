package com.arlight.bingo.gui;

import com.arlight.bingo.game.BingoGame;
import com.arlight.bingo.game.BingoTeam;
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

public class CardGUIListener implements Listener {

    private final BingoGame game;
    private final JavaPlugin plugin;

    public CardGUIListener(BingoGame game, JavaPlugin plugin) {
        this.game = game;
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (isCardInventory(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isCardInventory(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    /** Click derecho con el item fisico "Carton de Bingo" en la mano: abre la GUI. */
    @EventHandler
    public void onUseCardItem(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!BingoCardItem.isBingoCardItem(plugin, item)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        BingoTeam team = game.getTeamOf(player);
        if (team == null || team.getCard() == null) {
            player.sendMessage(ChatColor.RED + "No tienes un carton activo todavia.");
            return;
        }
        player.openInventory(getOrBuildInventory(team));
    }

    private org.bukkit.inventory.Inventory getOrBuildInventory(BingoTeam team) {
        org.bukkit.inventory.Inventory inv = team.getGuiInventory();
        if (inv == null) {
            inv = CardGUI.build(team.getCard());
            team.setGuiInventory(inv);
        }
        return inv;
    }

    private boolean isCardInventory(String title) {
        return ChatColor.stripColor(title).equals(ChatColor.stripColor(CardGUI.TITLE));
    }
}
