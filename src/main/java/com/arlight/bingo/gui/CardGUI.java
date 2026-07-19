package com.arlight.bingo.gui;

import com.arlight.bingo.game.BingoCard;
import com.arlight.bingo.game.BingoGoal;
import com.arlight.bingo.util.MaterialResolver;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Construye una representacion visual (inventario) del carton de bingo.
 * Es solo de lectura: el listener CardGUIListener cancela cualquier intento
 * de mover/sacar items de este inventario.
 *
 * IMPORTANTE: esta clase usa a proposito solo la API "clasica" de Bukkit (ChatColor +
 * String), NO componentes de Adventure (Component/displayName(Component)/translationKey()).
 * En un servidor hibrido como Arclight (NeoForge + Bukkit), esas extensiones mas nuevas de
 * Paper pueden no estar completamente implementadas y romper la GUI en tiempo de ejecucion
 * sin avisar en el juego (solo se ve en la consola del servidor).
 */
public class CardGUI {

    public static final String TITLE = ChatColor.GOLD + "Carton de Bingo";
    public static final NamespacedKey MARKER_KEY = new NamespacedKey("arlightbingo", "card-gui-marker");

    public static Inventory build(BingoCard card) {
        int rows = Math.min(6, Math.max(1, card.getSize())); // maximo 6 filas (54 slots) por limite de Bukkit
        Inventory inv = Bukkit.createInventory(null, rows * 9, TITLE);
        refresh(inv, card);
        return inv;
    }

    /** Vuelve a pintar los items de un inventario YA ABIERTO, para que los jugadores vean el progreso en vivo. */
    public static void refresh(Inventory inv, BingoCard card) {
        int size = card.getSize();
        int rows = Math.min(6, Math.max(1, size));
        int cols = Math.min(9, size);

        for (int r = 0; r < size && r < rows; r++) {
            for (int c = 0; c < size && c < cols; c++) {
                inv.setItem(r * 9 + c, buildIcon(card.getGoal(r, c)));
            }
        }
    }

    private static ItemStack buildIcon(BingoGoal goal) {
        Material resolved = goal.isCompleted() ? null : resolveMaterial(goal.getTarget());
        Material material = goal.isCompleted() ? Material.LIME_STAINED_GLASS_PANE : (resolved != null ? resolved : Material.PAPER);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ChatColor nameColor = goal.isCompleted() ? ChatColor.GREEN : ChatColor.YELLOW;
            meta.setDisplayName(nameColor + goal.getDisplayName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Progreso: " + ChatColor.WHITE + goal.getProgress() + "/" + goal.getAmountRequired());
            lore.add(goal.isCompleted() ? ChatColor.GREEN + "✔ Completado" : ChatColor.RED + "Pendiente");
            if (!goal.isCompleted() && resolved == null && goal.getTarget() != null) {
                lore.add(ChatColor.DARK_GRAY + "(icono generico, item modded: " + goal.getTarget() + ")");
            }
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(MARKER_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Resuelve el Material de Bukkit correspondiente al namespaced key del objetivo,
     * delegando en MaterialResolver (que prueba varias estrategias para vanilla y mods).
     */
    private static Material resolveMaterial(String target) {
        return MaterialResolver.resolve(target);
    }
}
