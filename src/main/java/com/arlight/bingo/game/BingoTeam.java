package com.arlight.bingo.game;

import org.bukkit.inventory.Inventory;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Representa una "unidad" que compite en la partida: un equipo (modo team-mode)
 * o un jugador individual (modo FFA, donde cada jugador es su propio "equipo").
 * Cada unidad tiene su propio carton independiente.
 */
public class BingoTeam {

    private final String name;
    private final Set<UUID> members = new HashSet<>();
    private BingoCard card;
    private Inventory guiInventory; // cache: permite refrescar la GUI en vivo sin tener que reabrirla
    private MapView mapView; // el mapa fisico compartido de este equipo, con su renderer propio

    public BingoTeam(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public boolean hasMember(UUID uuid) {
        return members.contains(uuid);
    }

    public BingoCard getCard() {
        return card;
    }

    public void setCard(BingoCard card) {
        this.card = card;
        this.guiInventory = null; // el carton cambio (nueva partida): invalidamos la GUI cacheada

        if (mapView != null) {
            for (MapRenderer renderer : new ArrayList<>(mapView.getRenderers())) {
                mapView.removeRenderer(renderer);
            }
            if (card != null) {
                mapView.addRenderer(new com.arlight.bingo.gui.BingoCardMapRenderer(card));
            }
        }
    }

    public Inventory getGuiInventory() {
        return guiInventory;
    }

    public void setGuiInventory(Inventory guiInventory) {
        this.guiInventory = guiInventory;
    }

    public MapView getMapView() {
        return mapView;
    }

    public void setMapView(MapView mapView) {
        this.mapView = mapView;
    }
}
