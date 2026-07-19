package com.arlight.bingo.game;

import java.util.Objects;

/**
 * Representa una casilla del carton de bingo: su definicion (leida de config.yml)
 * y su estado de progreso para un jugador o equipo concreto.
 */
public class BingoGoal {

    private final String id;
    private final GoalType type;
    private final String target;   // namespaced key (minecraft:xxx o modid:xxx), puede ser null en CUSTOM_TRIGGER
    private final int amountRequired;
    private final String displayName;

    private int progress = 0;
    private boolean completed = false;

    public BingoGoal(String id, GoalType type, String target, int amountRequired, String displayName) {
        this.id = id;
        this.type = type;
        this.target = target;
        this.amountRequired = Math.max(1, amountRequired);
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public GoalType getType() {
        return type;
    }

    public String getTarget() {
        return target;
    }

    public int getAmountRequired() {
        return amountRequired;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getProgress() {
        return progress;
    }

    public boolean isCompleted() {
        return completed;
    }

    /**
     * Suma progreso a la casilla. Devuelve true si con esto la casilla se completa
     * justo ahora (para poder anunciarlo solo una vez).
     */
    public boolean addProgress(int amount) {
        if (completed) return false;
        progress += amount;
        if (progress >= amountRequired) {
            progress = amountRequired;
            completed = true;
            return true;
        }
        return false;
    }

    /**
     * Actualiza el progreso solo si el nuevo valor es mayor al actual (usado por el
     * escaneo de inventario: nos interesa el maximo que el jugador/equipo llego a
     * tener alguna vez, no la cantidad actual, para que craftear-y-gastar el item
     * igual cuente como conseguido). Devuelve true si con esto se completa justo ahora.
     */
    public boolean updateProgressIfHigher(int newAmount) {
        if (completed) return false;
        if (newAmount > progress) {
            progress = Math.min(newAmount, amountRequired);
        }
        if (progress >= amountRequired) {
            completed = true;
            return true;
        }
        return false;
    }

    public void forceComplete() {
        this.progress = amountRequired;
        this.completed = true;
    }

    /**
     * Crea una copia limpia (sin progreso) de este objetivo, para asignarla
     * a un nuevo carton de jugador/equipo.
     */
    public BingoGoal copyFresh() {
        return new BingoGoal(id, type, target, amountRequired, displayName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BingoGoal)) return false;
        BingoGoal other = (BingoGoal) o;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
