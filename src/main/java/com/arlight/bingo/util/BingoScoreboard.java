package com.arlight.bingo.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scoreboard (sidebar) compartido para todos los jugadores anotados al bingo.
 * En sala de espera muestra cantidad de jugadores y la cuenta regresiva de auto-inicio.
 * Durante la partida muestra UNA linea por jugador/equipo con sus puntos y el ultimo
 * objetivo que completo (se actualiza en el mismo lugar, no se acumula una lista larga),
 * ordenada de mayor a menor puntaje.
 */
public class BingoScoreboard {

    private static final int MAX_LINES = 12;

    private final Scoreboard scoreboard;
    private final Objective objective;

    private final Map<String, Integer> pointsByTeam = new LinkedHashMap<>();
    private final Map<String, String> lastGoalByTeam = new LinkedHashMap<>();

    public BingoScoreboard() {
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = scoreboard.registerNewObjective("bingo", "dummy", ChatColor.GOLD + "" + ChatColor.BOLD + "BINGO");
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public void applyTo(Player player) {
        player.setScoreboard(scoreboard);
    }

    public void removeFrom(Player player) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        if (player.getScoreboard() == scoreboard) {
            player.setScoreboard(main);
        }
    }

    public void showLobby(int players, int maxPlayers, Integer countdownSecondsOrNull) {
        clearLines();
        int score = 10;
        setLine(ChatColor.YELLOW + "Jugadores: " + ChatColor.WHITE + players + "/" + maxPlayers, score--);
        if (countdownSecondsOrNull != null) {
            setLine(ChatColor.AQUA + "Empieza en: " + ChatColor.WHITE + countdownSecondsOrNull + "s", score--);
        } else {
            setLine(ChatColor.GRAY + "Esperando jugadores...", score--);
        }
    }

    /** Reinicia el progreso mostrado (llamar al arrancar una partida nueva). */
    public void resetProgress() {
        pointsByTeam.clear();
        lastGoalByTeam.clear();
    }

    /**
     * Actualiza la linea de este jugador/equipo (puntos + ultimo objetivo completado).
     * No agrega una linea nueva por cada objetivo: siempre reemplaza la linea anterior
     * de ese mismo jugador/equipo.
     */
    public void updateTeamProgress(String teamName, int points, String lastGoalName) {
        pointsByTeam.put(teamName, points);
        lastGoalByTeam.put(teamName, lastGoalName);
        showRunning();
    }

    public void showRunning() {
        clearLines();
        int score = 15;
        setLine(ChatColor.AQUA + "" + ChatColor.BOLD + "Progreso:", score--);

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(pointsByTeam.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());

        if (sorted.isEmpty()) {
            setLine(ChatColor.GRAY + "(nadie completo nada todavia)", score--);
            return;
        }

        int maxTeams = Math.max(1, MAX_LINES / 2);
        int shown = 0;
        for (Map.Entry<String, Integer> entry : sorted) {
            if (shown >= maxTeams || score < 0) break;
            String last = lastGoalByTeam.get(entry.getKey());

            setLine(ChatColor.GREEN + entry.getKey() + ChatColor.WHITE + ": " + ChatColor.YELLOW + entry.getValue() + "p", score--);
            if (last != null && score >= 0) {
                setLine(ChatColor.GRAY + " \u00bb " + ChatColor.ITALIC + shorten(last, 28), score--);
            }
            shown++;
        }
    }

    private String shorten(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private void clearLines() {
        for (String entry : new ArrayList<>(scoreboard.getEntries())) {
            scoreboard.resetScores(entry);
        }
    }

    private void setLine(String text, int score) {
        // Cada linea debe ser un "entry" unico en el scoreboard; si hay texto repetido
        // le agregamos un color "invisible" al final para diferenciarlo.
        String unique = text;
        while (scoreboard.getEntries().contains(unique)) {
            unique = unique + ChatColor.RESET;
        }
        objective.getScore(unique).setScore(score);
    }
}
