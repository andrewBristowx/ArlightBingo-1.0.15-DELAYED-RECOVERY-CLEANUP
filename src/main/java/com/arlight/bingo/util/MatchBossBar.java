package com.arlight.bingo.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

/**
 * Boss bar compartida que muestra el tiempo restante de la partida en curso.
 * Solo se usa cuando hay un limite de tiempo configurado (time-limit-minutes > 0).
 */
public class MatchBossBar {

    private BossBar bossBar;

    public void start() {
        bossBar = Bukkit.createBossBar(ChatColor.GOLD + "Bingo", BarColor.GREEN, BarStyle.SOLID);
    }

    public void addPlayer(Player player) {
        if (bossBar != null) {
            bossBar.addPlayer(player);
        }
    }

    public void removePlayer(Player player) {
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }

    /** Actualiza el titulo y el progreso segun el tiempo restante. */
    public void update(long remainingMillis, long totalMillis) {
        if (bossBar == null) return;

        long remainingSeconds = Math.max(0, remainingMillis / 1000);
        long minutes = remainingSeconds / 60;
        long seconds = remainingSeconds % 60;

        bossBar.setTitle(ChatColor.GOLD + "Bingo " + ChatColor.WHITE + "- Tiempo restante: "
                + ChatColor.YELLOW + String.format("%02d:%02d", minutes, seconds));

        double progress = totalMillis > 0 ? clamp((double) remainingMillis / totalMillis) : 0;
        bossBar.setProgress(progress);

        if (progress < 0.2) {
            bossBar.setColor(BarColor.RED);
        } else if (progress < 0.5) {
            bossBar.setColor(BarColor.YELLOW);
        } else {
            bossBar.setColor(BarColor.GREEN);
        }
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public void stop() {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }
}
