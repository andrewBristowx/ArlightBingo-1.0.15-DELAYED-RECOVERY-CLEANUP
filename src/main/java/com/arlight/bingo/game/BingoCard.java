package com.arlight.bingo.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Carton de bingo de un jugador o equipo: una cuadricula NxN de BingoGoal.
 */
public class BingoCard {

    private final int size;
    private final List<BingoGoal> goals; // orden fila por fila, tamano size*size

    public BingoCard(int size, List<BingoGoal> goals) {
        this.size = size;
        this.goals = goals;
    }

    public int getSize() {
        return size;
    }

    public List<BingoGoal> getGoals() {
        return goals;
    }

    public BingoGoal getGoal(int row, int col) {
        return goals.get(row * size + col);
    }

    public List<BingoGoal> findByTarget(GoalType type, String target) {
        List<BingoGoal> matches = new ArrayList<>();
        for (BingoGoal g : goals) {
            if (g.getType() == type && !g.isCompleted()
                    && g.getTarget() != null && g.getTarget().equalsIgnoreCase(target)) {
                matches.add(g);
            }
        }
        return matches;
    }

    public BingoGoal findById(String id) {
        for (BingoGoal g : goals) {
            if (g.getId().equalsIgnoreCase(id)) return g;
        }
        return null;
    }

    public boolean isFullCardComplete() {
        for (BingoGoal g : goals) {
            if (!g.isCompleted()) return false;
        }
        return true;
    }

    /** Devuelve true si hay al menos una fila, columna o diagonal completa. */
    public boolean hasLineComplete() {
        // filas
        for (int r = 0; r < size; r++) {
            boolean full = true;
            for (int c = 0; c < size; c++) {
                if (!getGoal(r, c).isCompleted()) { full = false; break; }
            }
            if (full) return true;
        }
        // columnas
        for (int c = 0; c < size; c++) {
            boolean full = true;
            for (int r = 0; r < size; r++) {
                if (!getGoal(r, c).isCompleted()) { full = false; break; }
            }
            if (full) return true;
        }
        // diagonales
        boolean diag1 = true, diag2 = true;
        for (int i = 0; i < size; i++) {
            if (!getGoal(i, i).isCompleted()) diag1 = false;
            if (!getGoal(i, size - 1 - i).isCompleted()) diag2 = false;
        }
        return diag1 || diag2;
    }

    public int countCompleted() {
        int count = 0;
        for (BingoGoal g : goals) if (g.isCompleted()) count++;
        return count;
    }
}
