package com.arlight.bingo.gui;

import com.arlight.bingo.util.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Items temporales que se entregan a un jugador mientras espera en la sala del bingo
 * (antes de que arranque la partida): uno con la explicacion del juego, y otro para salir.
 * Se reemplazan por el item real del carton (ver BingoCardItem) cuando arranca la partida.
 */
public class LobbyItems {

    private static final String INFO_KEY_NAME = "bingo-lobby-info-item";
    private static final String LEAVE_KEY_NAME = "bingo-lobby-leave-item";

    private LobbyItems() {
    }

    public static NamespacedKey infoKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, INFO_KEY_NAME);
    }

    public static NamespacedKey leaveKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, LEAVE_KEY_NAME);
    }

    public static ItemStack createInfoItem(JavaPlugin plugin, ConfigManager cfg) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "¿De que va el Bingo?");

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Cada jugador (o equipo) recibe un carton");
            lore.add(ChatColor.GRAY + "de " + cfg.getCardSize() + "x" + cfg.getCardSize() + " objetivos al azar:");
            lore.add(ChatColor.GRAY + "conseguir items, romper bloques, matar");
            lore.add(ChatColor.GRAY + "mobs o completar logros.");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Modo: " + ChatColor.WHITE + (cfg.isTeamMode() ? "Por equipos" : "Individual (todos contra todos)"));
            lore.add(ChatColor.YELLOW + "Como se gana: " + ChatColor.WHITE + winConditionText(cfg));
            if (cfg.getTimeLimitMinutes() > 0) {
                lore.add(ChatColor.YELLOW + "Duracion: " + ChatColor.WHITE + cfg.getTimeLimitMinutes() + " minutos");
            }
            lore.add("");
            lore.add(ChatColor.GRAY + "Una vez que arranque la partida vas a recibir");
            lore.add(ChatColor.GRAY + "tu carton (click derecho para verlo).");

            meta.setLore(lore);
            meta.getPersistentDataContainer().set(infoKey(plugin), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String winConditionText(ConfigManager cfg) {
        switch (cfg.getWinCondition()) {
            case LINE:
                return "Completar una fila, columna o diagonal";
            case FULL_CARD:
            case BLACKOUT:
                return "Completar el carton entero";
            case POINTS:
            default:
                return "1 punto por casilla, gana quien tenga mas al acabar el tiempo";
        }
    }

    public static ItemStack createLeaveItem(JavaPlugin plugin) {
        ItemStack item = new ItemStack(Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Salir de la sala");
            meta.setLore(List.of(ChatColor.GRAY + "Click derecho para dejar de esperar la partida"));
            meta.getPersistentDataContainer().set(leaveKey(plugin), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isInfoItem(JavaPlugin plugin, ItemStack item) {
        return hasMarker(item, infoKey(plugin));
    }

    public static boolean isLeaveItem(JavaPlugin plugin, ItemStack item) {
        return hasMarker(item, leaveKey(plugin));
    }

    /**
     * Reconoce también copias antiguas que hayan perdido su PersistentDataContainer
     * al ser guardadas por una versión anterior o por un servidor híbrido.
     */
    public static boolean isTemporaryLobbyItem(JavaPlugin plugin, ItemStack item) {
        if (isInfoItem(plugin, item) || isLeaveItem(plugin, item)) return true;
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        // Algunas capas híbridas conservan el PDC pero lo exponen con la clave de una
        // versión anterior del plugin. Reconocer el nombre de la clave evita depender
        // del namespace exacto y sigue siendo específico de nuestros dos objetos.
        boolean marked = meta.getPersistentDataContainer().getKeys().stream()
                .map(NamespacedKey::getKey)
                .anyMatch(key -> INFO_KEY_NAME.equals(key) || LEAVE_KEY_NAME.equals(key));
        if (marked) return true;
        if (!meta.hasDisplayName()) return false;

        String name = ChatColor.stripColor(meta.getDisplayName());
        if (name == null) return false;
        String normalized = name.trim()
                .replace('¿', ' ')
                .replace('?', ' ')
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
        return (item.getType() == Material.BOOK && "de que va el bingo".equals(normalized))
                || (item.getType() == Material.RED_DYE && "salir de la sala".equals(normalized));
    }

    private static boolean hasMarker(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
