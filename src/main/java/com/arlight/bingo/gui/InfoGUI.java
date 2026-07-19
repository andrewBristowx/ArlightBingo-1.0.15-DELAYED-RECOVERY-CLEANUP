package com.arlight.bingo.gui;

import com.arlight.bingo.util.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Menu (inventario) que se abre al hacer click en el item de informacion de la sala de
 * espera. Por ahora tiene un solo item (la misma explicacion del juego), pero queda
 * como una GUI de verdad para poder agregarle mas items despues (reglas, creditos, etc).
 */
public class InfoGUI {

    public static final String TITLE = ChatColor.AQUA + "¿De que va el Bingo?";

    public static Inventory build(JavaPlugin plugin, ConfigManager cfg) {
        Inventory inv = Bukkit.createInventory(null, 9, TITLE);
        inv.setItem(4, LobbyItems.createInfoItem(plugin, cfg));
        return inv;
    }
}
