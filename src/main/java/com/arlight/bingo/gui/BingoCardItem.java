package com.arlight.bingo.gui;

import com.arlight.bingo.game.BingoTeam;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Item fisico que representa el carton de bingo. Los jugadores lo reciben al unirse
 * y al hacer click derecho con el en la mano se les abre la GUI de su carton actual.
 * Cuando se conoce el equipo, ademas se le asigna un mapa (MapView) con una grilla de
 * colores que muestra el progreso del carton de un vistazo (se actualiza sola).
 */
public class BingoCardItem {

    private static final String KEY_NAME = "bingo-card-item";

    private BingoCardItem() {
    }

    public static NamespacedKey markerKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, KEY_NAME);
    }

    /** Version simple, sin mapa visual (fallback cuando todavia no se conoce el equipo del jugador). */
    public static ItemStack create(JavaPlugin plugin) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applyDisplay(meta, plugin);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Version completa: el mapa muestra una grilla de colores con el progreso del carton
     * del equipo (verde = casilla completada), y se actualiza sola mientras se sostiene.
     */
    public static ItemStack createForTeam(JavaPlugin plugin, BingoTeam team) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        ItemMeta rawMeta = item.getItemMeta();
        if (!(rawMeta instanceof MapMeta) || team.getCard() == null) {
            return create(plugin);
        }

        MapMeta meta = (MapMeta) rawMeta;
        MapView view = team.getMapView();
        if (view == null) {
            World world = Bukkit.getWorlds().get(0);
            view = Bukkit.createMap(world);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            view.setLocked(true);
            for (MapRenderer renderer : new ArrayList<>(view.getRenderers())) {
                view.removeRenderer(renderer);
            }
            view.addRenderer(new BingoCardMapRenderer(team.getCard()));
            team.setMapView(view);
        }

        meta.setMapView(view);
        applyDisplay(meta, plugin);
        item.setItemMeta(meta);
        return item;
    }

    private static void applyDisplay(ItemMeta meta, JavaPlugin plugin) {
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Carton de Bingo");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Click derecho para ver tu carton",
                ChatColor.DARK_GRAY + "ArlightBingo"
        ));
        meta.getPersistentDataContainer().set(markerKey(plugin), PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isBingoCardItem(JavaPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (meta.getPersistentDataContainer().has(markerKey(plugin), PersistentDataType.BYTE)) return true;

        // Compatibilidad con cartones guardados por versiones antiguas.
        return item.getType() == Material.FILLED_MAP
                && meta.hasDisplayName()
                && "Carton de Bingo".equals(ChatColor.stripColor(meta.getDisplayName()));
    }
}
