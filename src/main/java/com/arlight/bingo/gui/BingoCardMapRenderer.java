package com.arlight.bingo.gui;

import com.arlight.bingo.game.BingoCard;
import com.arlight.bingo.game.BingoGoal;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/**
 * Dibuja en el item fisico del mapa una grilla de colores representando el carton de
 * bingo: cada casilla es un cuadrado (verde = completada, gris = pendiente). No llega a
 * mostrar el icono real de cada item (Minecraft no da una forma simple de pintar iconos de
 * items dentro de un mapa sin recursos graficos externos), pero da una vista rapida del
 * progreso general de un vistazo, y se actualiza sola mientras el jugador sostiene el mapa.
 */
public class BingoCardMapRenderer extends MapRenderer {

    private static final byte BACKGROUND = MapPalette.matchColor(60, 40, 25);
    private static final byte PENDING = MapPalette.matchColor(210, 210, 210);
    private static final byte COMPLETED = MapPalette.matchColor(40, 200, 40);
    private static final byte BORDER = MapPalette.matchColor(20, 15, 10);

    private final BingoCard card;

    public BingoCardMapRenderer(BingoCard card) {
        super(true);
        this.card = card;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        int size = card.getSize();
        int margin = 6;
        int usable = 128 - margin * 2;
        int cellSize = usable / size;

        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                canvas.setPixel(x, y, BACKGROUND);
            }
        }

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                BingoGoal goal = card.getGoal(r, c);
                byte color = goal.isCompleted() ? COMPLETED : PENDING;

                int startX = margin + c * cellSize;
                int startY = margin + r * cellSize;
                for (int x = 0; x < cellSize; x++) {
                    for (int y = 0; y < cellSize; y++) {
                        boolean edge = x == 0 || y == 0 || x == cellSize - 1 || y == cellSize - 1;
                        canvas.setPixel(startX + x, startY + y, edge ? BORDER : color);
                    }
                }
            }
        }
    }
}
