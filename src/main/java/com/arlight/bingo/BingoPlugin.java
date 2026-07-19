package com.arlight.bingo;

import com.arlight.bingo.commands.BingoCommand;
import com.arlight.bingo.end.EndEncounterManager;
import com.arlight.bingo.game.BingoGame;
import com.arlight.bingo.game.GameState;
import com.arlight.bingo.gui.CardGUIListener;
import com.arlight.bingo.listeners.GoalListener;
import com.arlight.bingo.listeners.SignListener;
import com.arlight.bingo.util.BingoScoreboard;
import com.arlight.bingo.util.ConfigManager;
import com.arlight.bingo.util.ArenaWorldManager;
import com.arlight.bingo.util.StorageManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BingoPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private BingoGame game;
    private StorageManager storageManager;
    private ArenaWorldManager arenaWorldManager;
    private BingoScoreboard scoreboard;
    private EndEncounterManager endEncounterManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.game = new BingoGame(this, configManager);
        this.storageManager = new StorageManager(this);
        this.game.setStorageManager(storageManager);

        this.arenaWorldManager = new ArenaWorldManager(this);
        this.game.setArenaWorldManager(arenaWorldManager);
        this.game.setWorldBorderManager(new com.arlight.bingo.util.WorldBorderManager(this));

        this.endEncounterManager = new EndEncounterManager(this);
        this.game.setEndEncounterManager(endEncounterManager);
        getServer().getPluginManager().registerEvents(endEncounterManager, this);

        this.scoreboard = new BingoScoreboard();
        this.game.setScoreboard(scoreboard);

        getServer().getPluginManager().registerEvents(new GoalListener(game), this);
        getServer().getPluginManager().registerEvents(new CardGUIListener(game, this), this);
        getServer().getPluginManager().registerEvents(new SignListener(game, arenaWorldManager), this);
        getServer().getPluginManager().registerEvents(new com.arlight.bingo.listeners.GameListener(game), this);
        getServer().getPluginManager().registerEvents(new com.arlight.bingo.listeners.LobbyItemListener(game, this), this);
        BingoCommand bingoCommand = new BingoCommand(game, this);
        getCommand("bingo").setExecutor(bingoCommand);
        getCommand("bingo").setTabCompleter(bingoCommand);

        // Si el servidor se reinicio a mitad de una partida, la restauramos.
        boolean restored = storageManager.load(game);
        if (restored && game.getState() == GameState.RUNNING) {
            getLogger().info("Se restauro una partida de bingo que estaba en curso.");
        }

        // Integracion OPCIONAL con ArlightCore (selector de minijuegos + XP al ganar).
        // Si ArlightCore no esta instalado, esto se salta por completo y el resto del
        // plugin funciona exactamente igual.
        if (getServer().getPluginManager().getPlugin("ArlightCore") != null) {
            try {
                com.arlight.bingo.integration.CoreIntegration.register(game);
                getLogger().info("Integracion con ArlightCore activada.");
            } catch (Throwable t) {
                getLogger().warning("No se pudo activar la integracion con ArlightCore: " + t.getMessage());
            }
        }

        getLogger().info("ArlightBingo habilitado correctamente.");
    }

    @Override
    public void onDisable() {
        if (game != null) {
            if (storageManager != null) {
                // Guardamos el estado tal cual esta (incluyendo RUNNING) para poder
                // restaurar la partida si esto fue un reinicio del servidor.
                storageManager.save(game);
            }
            game.cancelTimerOnly();
        }
        if (endEncounterManager != null) {
            endEncounterManager.shutdown();
        }
        getLogger().info("ArlightBingo deshabilitado.");
    }

    public BingoGame getGame() {
        return game;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public ArenaWorldManager getArenaWorldManager() {
        return arenaWorldManager;
    }
}
