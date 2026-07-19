package com.arlight.bingo.integration;

import com.arlight.bingo.game.BingoGame;
import com.arlight.bingo.game.BingoTeam;
import com.arlight.bingo.game.GameState;
import com.arlight.bingo.gui.BingoCardItem;
import com.arlight.bingo.gui.LobbyItems;
import com.arlight.core.api.ArlightCoreAPI;
import com.arlight.core.api.MinigameProvider;
import com.arlight.core.api.MinigameStatus;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Integracion OPCIONAL con ArlightCore: registra el Bingo en el item selector de minijuegos
 * y otorga XP a los ganadores. Esta clase solo se carga (se referencia) desde BingoPlugin
 * DESPUES de comprobar que el plugin ArlightCore esta instalado, asi que si no lo esta,
 * el resto del plugin nunca toca las clases de ArlightCoreAPI y no hay ningun error.
 */
public final class CoreIntegration {

    private CoreIntegration() {
    }

    public static void register(BingoGame game) {
        game.setSessionBridge(new BingoGame.SessionBridge() {
            @Override
            public boolean begin(Player player) {
                return ArlightCoreAPI.beginMinigameSession(player, "bingo");
            }

            @Override
            public void end(Player player) {
                ArlightCoreAPI.endMinigameSession(player);
            }
        });

        ArlightCoreAPI.registerMinigame(new MinigameProvider() {
            @Override
            public String getId() {
                return "bingo";
            }

            @Override
            public String getDisplayName() {
                return ChatColor.GOLD + "Bingo";
            }

            @Override
            public ItemStack getIcon() {
                return new ItemStack(Material.COMPASS);
            }

            @Override
            public MinigameStatus getStatus() {
                return game.getState() == GameState.WAITING ? MinigameStatus.WAITING : MinigameStatus.IN_PROGRESS;
            }

            @Override
            public void join(Player player) {
                game.addPlayer(player, null);
            }

            @Override
            public void leave(Player player) {
                if (game.getState() == GameState.RUNNING) {
                    game.disqualifyPlayer(player, ChatColor.RED + player.getName()
                            + " abandonó el Bingo y quedó descalificado.");
                } else {
                    game.removePlayer(player);
                }
            }

            @Override
            public void handleDisconnect(Player player) {
                game.disqualifyIfInMatch(player);
            }

            @Override
            public void cleanupAfterRecovery(Player player) {
                // En Arclight el inventario del jugador puede volver a sincronizarse varios
                // ticks después del PlayerJoinEvent. Por eso hacemos una limpieza inmediata
                // y dos comprobaciones posteriores. Las comprobaciones son seguras: solo
                // reconocen objetos marcados por Bingo o sus copias antiguas exactas.
                cleanupRecoveredItems(game, player, "inmediata");
                Bukkit.getScheduler().runTaskLater(game.getPlugin(),
                        () -> cleanupRecoveredItems(game, player, "5 ticks"), 5L);
                Bukkit.getScheduler().runTaskLater(game.getPlugin(),
                        () -> cleanupRecoveredItems(game, player, "20 ticks"), 20L);
            }

            @Override
            public boolean isPlaying(Player player) {
                return game.getTeamOf(player) != null;
            }

            @Override
            public int getCurrentPlayers() {
                return game.getTeams().stream().mapToInt(team -> team.getMembers().size()).sum();
            }

            @Override
            public int getMaxPlayers() {
                return game.getConfigManager().getMaxPlayersScoreboard();
            }
        });

        game.setWinListener(winners -> {
            for (BingoTeam team : winners) {
                for (UUID uuid : team.getMembers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        ArlightCoreAPI.addWinXp(p);
                    }
                }
            }
        });
    }

    private static void cleanupRecoveredItems(BingoGame game, Player player, String phase) {
        if (!player.isOnline() || game.getTeamOf(player) != null) return;

        ItemStack[] contents = player.getInventory().getContents();
        int removed = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (LobbyItems.isTemporaryLobbyItem(game.getPlugin(), item)
                    || BingoCardItem.isBingoCardItem(game.getPlugin(), item)) {
                player.getInventory().setItem(slot, null);
                removed++;
            }
        }

        if (removed > 0) player.updateInventory();
        game.getPlugin().getLogger().info("Recuperación de " + player.getName()
                + " (" + phase + "): " + removed
                + " objeto(s) temporal(es) de Bingo eliminado(s).");
    }
}
