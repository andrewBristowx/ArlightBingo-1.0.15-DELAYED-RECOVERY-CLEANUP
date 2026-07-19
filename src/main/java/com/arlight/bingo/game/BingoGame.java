package com.arlight.bingo.game;

import com.arlight.bingo.gui.BingoCardItem;
import com.arlight.bingo.end.EndEncounterManager;
import com.arlight.bingo.util.ArenaWorldManager;
import com.arlight.bingo.util.BingoScoreboard;
import com.arlight.bingo.util.ConfigManager;
import com.arlight.bingo.util.MaterialResolver;
import com.arlight.bingo.util.MatchBossBar;
import com.arlight.bingo.util.StorageManager;
import com.arlight.bingo.util.WorldBorderManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class BingoGame {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    private GameState state = GameState.WAITING;
    private final Map<String, BingoTeam> teams = new LinkedHashMap<>();
    private final Map<UUID, String> playerTeamName = new HashMap<>();
    private final Map<UUID, SavedInventory> savedInventories = new HashMap<>();

    private BukkitTask timerTask;
    private BukkitTask autostartTask;
    private BukkitTask inventoryScanTask;
    private int autostartSecondsRemaining;
    private long endTimeMillis;
    private long matchDurationMillis;
    private String currentArenaWorld;

    private StorageManager storageManager;
    private ArenaWorldManager multiverse;
    private WorldBorderManager worldBorderManager;
    private EndEncounterManager endEncounterManager;

    public void setWorldBorderManager(WorldBorderManager worldBorderManager) {
        this.worldBorderManager = worldBorderManager;
    }

    public void setEndEncounterManager(EndEncounterManager endEncounterManager) {
        this.endEncounterManager = endEncounterManager;
    }

    public org.bukkit.Location getEndEncounterArrival(World world) {
        return endEncounterManager == null ? null : endEncounterManager.getSafeArrival(world);
    }
    private BingoScoreboard scoreboard;
    private final MatchBossBar bossBar = new MatchBossBar();
    private WinListener winListener; // opcional: lo setea BingoPlugin si detecta ArlightCore instalado
    private SessionBridge sessionBridge;

    /** Callback desacoplado para avisar quien gano, sin que este archivo dependa de ningun otro plugin. */
    public interface WinListener {
        void onWin(List<BingoTeam> winners);
    }

    /** Puente opcional para que ArlightCore administre la sesión global. */
    public interface SessionBridge {
        boolean begin(Player player);
        void end(Player player);
    }

    public void setWinListener(WinListener winListener) {
        this.winListener = winListener;
    }

    public void setSessionBridge(SessionBridge sessionBridge) {
        this.sessionBridge = sessionBridge;
    }

    public BingoGame(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void setStorageManager(StorageManager storageManager) {
        this.storageManager = storageManager;
    }

    public void setArenaWorldManager(ArenaWorldManager multiverse) {
        this.multiverse = multiverse;
    }

    public ArenaWorldManager getArenaWorldManager() {
        return multiverse;
    }

    public void setScoreboard(BingoScoreboard scoreboard) {
        this.scoreboard = scoreboard;
    }

    public String getCurrentArenaWorld() {
        return currentArenaWorld;
    }

    private void autosave() {
        if (storageManager != null) {
            storageManager.save(this);
        }
    }

    /**
     * Teletransporta al jugador y, unos ticks despues, le vuelve a aplicar el scoreboard.
     * Es necesario porque al cambiar de mundo/dimension el cliente a veces deja de mostrar
     * el sidebar hasta que se le vuelve a enviar, aunque el objeto Scoreboard del servidor
     * siga siendo el mismo.
     */
    private void teleportAndKeepScoreboard(Player player, String world) {
        if (multiverse == null) return;
        multiverse.teleportToWorld(player, world);
        if (scoreboard != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    scoreboard.applyTo(player);
                }
            }, 10L);
        }
    }

    public GameState getState() {
        return state;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public boolean isTeamMode() {
        return configManager.isTeamMode();
    }

    public Collection<BingoTeam> getTeams() {
        return teams.values();
    }

    private int countAllPlayers() {
        int total = 0;
        for (BingoTeam t : teams.values()) total += t.getMembers().size();
        return total;
    }

    /** En modo FFA, cada jugador es su propio "equipo", identificado por su nombre. */
    public void addPlayer(Player player, String teamNameOrNull) {
        if (sessionBridge != null && !sessionBridge.begin(player)) {
            player.sendMessage(ChatColor.RED + "Ya estás participando en otro minijuego.");
            return;
        }
        saveInventoryIfNeeded(player);
        String teamName = isTeamMode()
                ? (teamNameOrNull != null ? teamNameOrNull : "sin-equipo")
                : player.getName();

        BingoTeam team = teams.computeIfAbsent(teamName, BingoTeam::new);
        team.addMember(player.getUniqueId());
        playerTeamName.put(player.getUniqueId(), teamName);

        player.sendMessage(ChatColor.GREEN + "Te has unido al bingo"
                + (isTeamMode() ? " en el equipo " + ChatColor.GOLD + teamName : "") + ChatColor.GREEN + ".");

        // Primero al lobby, despues el scoreboard y el item -- asi el jugador ya esta
        // parado en el mundo correcto cuando recibe todo lo demas.
        if (multiverse != null && configManager.isArenaWorldEnabled()) {
            String lobby = configManager.getLobbyWorld();
            if (lobby != null && !lobby.isEmpty()) {
                teleportAndKeepScoreboard(player, lobby);
            }
        }

        if (scoreboard != null) {
            scoreboard.applyTo(player);
        }

        // Al unirse la partida todavia no arranco (no hay carton asignado), asi que en vez
        // del item del carton le damos un item de informacion y uno para salir de la sala.
        // El inventario se limpia por completo para que no se cuele nada de antes.
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        player.getInventory().addItem(com.arlight.bingo.gui.LobbyItems.createInfoItem(plugin, configManager));
        player.getInventory().addItem(com.arlight.bingo.gui.LobbyItems.createLeaveItem(plugin));

        onPlayerCountChanged();
        autosave();
    }

    /** Salir de la SALA DE ESPERA (antes de que arranque la partida). Ver disqualifyPlayer() para salir en pleno juego. */
    public void removePlayer(Player player) {
        removePlayer(player, true);
    }

    private void removePlayer(Player player, boolean closeSession) {
        String teamName = playerTeamName.remove(player.getUniqueId());
        if (teamName != null) {
            BingoTeam team = teams.get(teamName);
            if (team != null) {
                team.removeMember(player.getUniqueId());
                if (team.getMembers().isEmpty()) {
                    teams.remove(teamName);
                }
            }
        }
        if (scoreboard != null) {
            scoreboard.removeFrom(player);
        }

        if (closeSession) {
            teleportToLobbyOrStay(player);
            restoreSavedInventory(player);
            endCoreSession(player);
        } else {
            // El Core conserva su propia copia hasta que el jugador vuelva a entrar.
            savedInventories.remove(player.getUniqueId());
        }

        onPlayerCountChanged();
        autosave();
    }

    /**
     * Teletransporta al mundo lobby configurado (arena-world.lobby-world) si esta cargado.
     * Si NO esta configurado o no esta cargado, NO fuerza ningun teletransporte -- deja al
     * jugador donde ya esta, en vez de mandarlo al mundo primario del servidor (que puede
     * ser distinto al lobby real que el admin este usando, ej. un mundo llamado "legos").
     */
    private void teleportToLobbyOrStay(Player player) {
        String lobby = configManager.getLobbyWorld();
        if (lobby == null || lobby.isEmpty()) return;
        World lobbyWorld = Bukkit.getWorld(lobby);
        if (lobbyWorld == null) return;
        player.teleport(lobbyWorld.getSpawnLocation());
    }

    /** Llamado por el listener de desconexion: solo actua si la partida esta corriendo y el jugador esta anotado. */
    public void disqualifyIfInMatch(Player player) {
        if (getTeamOf(player) == null) return;
        if (state == GameState.RUNNING) {
            disqualifyPlayer(player, ChatColor.RED + player.getName()
                    + " se desconecto y quedo descalificado del Bingo.", false);
        } else {
            removePlayer(player, false);
        }
    }

    /**
     * Saca a un jugador de una partida EN CURSO (por desconexion o por usar /bingo leave
     * mientras se juega). A diferencia de removePlayer(), esto puede terminar la partida
     * si con esta salida solo queda un equipo/jugador en pie (gana automaticamente).
     */
    public void disqualifyPlayer(Player player, String broadcastMessage) {
        disqualifyPlayer(player, broadcastMessage, true);
    }

    private void disqualifyPlayer(Player player, String broadcastMessage, boolean closeSession) {
        BingoTeam team = getTeamOf(player);
        if (team == null) return;

        playerTeamName.remove(player.getUniqueId());
        team.removeMember(player.getUniqueId());
        boolean teamEliminated = team.getMembers().isEmpty();
        if (teamEliminated) {
            teams.remove(team.getName());
        }

        if (broadcastMessage != null) {
            Bukkit.broadcastMessage(broadcastMessage);
        }

        if (scoreboard != null) {
            scoreboard.removeFrom(player);
        }
        if (closeSession && player.isOnline()) {
            teleportToLobbyOrStay(player);
            restoreSavedInventory(player);
        }
        if (closeSession) {
            endCoreSession(player);
        } else {
            savedInventories.remove(player.getUniqueId());
        }

        if (state != GameState.RUNNING) {
            autosave();
            return;
        }

        if (teams.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Bingo] " + ChatColor.RED + "No queda nadie jugando, se cancela la partida.");
            stop();
            return;
        }

        if (teams.size() == 1) {
            BingoTeam winner = teams.values().iterator().next();
            Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[Bingo] ¡" + winner.getName()
                    + " gana por ser el unico que queda en la partida!");
            endMatchWithCelebration(Collections.singletonList(winner));
            return;
        }

        autosave();
    }

    public BingoTeam getTeamOf(Player player) {
        String teamName = playerTeamName.get(player.getUniqueId());
        return teamName == null ? null : teams.get(teamName);
    }

    /**
     * Se llama cada vez que se une/sale un jugador mientras se espera partida.
     * Gestiona el arranque/cancelacion de la cuenta regresiva de auto-inicio y
     * actualiza el scoreboard de sala de espera.
     */
    private void onPlayerCountChanged() {
        if (state != GameState.WAITING && state != GameState.COUNTDOWN) return;

        int total = countAllPlayers();

        if (state == GameState.WAITING && total >= configManager.getMinPlayersToStart()) {
            beginAutostartCountdown();
        } else if (state == GameState.COUNTDOWN && total < configManager.getMinPlayersToStart()) {
            cancelAutostartCountdown();
        }

        updateLobbyScoreboard();
    }

    private void updateLobbyScoreboard() {
        if (scoreboard == null) return;
        Integer countdown = state == GameState.COUNTDOWN ? autostartSecondsRemaining : null;
        scoreboard.showLobby(countAllPlayers(), configManager.getMaxPlayersScoreboard(), countdown);
    }

    private void beginAutostartCountdown() {
        state = GameState.COUNTDOWN;
        autostartSecondsRemaining = configManager.getAutoStartCountdownSeconds();
        Bukkit.broadcastMessage(ChatColor.GOLD + "[Bingo] " + ChatColor.GREEN
                + "Suficientes jugadores! La partida empieza en " + autostartSecondsRemaining + "s.");

        autostartTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            autostartSecondsRemaining--;
            updateLobbyScoreboard();

            if (autostartSecondsRemaining <= 0) {
                cancelAutostartTaskOnly();
                prepareArenaThenFinalCountdown();
            } else if (autostartSecondsRemaining <= 5 || autostartSecondsRemaining % 10 == 0) {
                Bukkit.broadcastMessage(ChatColor.GOLD + "[Bingo] " + ChatColor.YELLOW
                        + "Empieza en " + autostartSecondsRemaining + "...");
            }
        }, 20L, 20L);
    }

    private void cancelAutostartCountdown() {
        cancelAutostartTaskOnly();
        if (state == GameState.COUNTDOWN) {
            state = GameState.WAITING;
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Bingo] " + ChatColor.RED
                    + "Faltan jugadores, se cancelo la cuenta regresiva.");
        }
    }

    private void cancelAutostartTaskOnly() {
        if (autostartTask != null) {
            autostartTask.cancel();
            autostartTask = null;
        }
    }

    /** Cuenta regresiva final "5, 4, 3, 2, 1" antes de que arranque la partida de verdad. */
    private void beginFinalCountdownThenStart() {
        state = GameState.COUNTDOWN;
        final int[] remaining = {configManager.getFinalCountdownSeconds()};

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (remaining[0] <= 0) {
                task.cancel();
                Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "COMIENZA!");
                actuallyStart();
                return;
            }
            Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + String.valueOf(remaining[0]));
            remaining[0]--;
        }, 0L, 20L);
    }

    /**
     * Se llama cuando termina la cuenta de 50s (o cuando un admin fuerza /bingo start):
     * primero regenera y teletransporta a todos a la arena, y RECIEN AHI arranca la cuenta
     * final 5-4-3-2-1 (ya estando todos parados en el mundo de la partida).
     */
    private void prepareArenaThenFinalCountdown() {
        if (teams.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.RED + "[Bingo] No hay jugadores para iniciar la partida.");
            state = GameState.WAITING;
            return;
        }

        state = GameState.COUNTDOWN;
        Bukkit.broadcastMessage(ChatColor.GOLD + "[Bingo] " + ChatColor.GREEN + "Preparando la arena...");
        setupArenaWorldAsync(this::beginFinalCountdownThenStart);
    }

    /**
     * Inicio manual (comando de admin): salta la cuenta de auto-inicio (los 50s) y
     * va directo a preparar la arena + la cuenta regresiva final 5-4-3-2-1.
     */
    public void start() {
        if (teams.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.RED + "[Bingo] No hay jugadores para iniciar la partida.");
            return;
        }
        cancelAutostartTaskOnly();
        prepareArenaThenFinalCountdown();
    }

    /** Aqui es donde realmente arranca la partida: se reparten cartones y se activa todo. */
    private void actuallyStart() {
        if (teams.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.RED + "[Bingo] No hay jugadores para iniciar la partida.");
            state = GameState.WAITING;
            return;
        }

        int size = configManager.getCardSize();
        int needed = size * size;
        List<BingoGoal> pool = new ArrayList<>(configManager.getGoalPool());

        if (pool.size() < needed) {
            Bukkit.broadcastMessage(ChatColor.RED + "[Bingo] No hay suficientes objetivos configurados ("
                    + pool.size() + "/" + needed + "). Revisa config.yml.");
            state = GameState.WAITING;
            return;
        }

        // Cada equipo/jugador recibe un carton barajado de forma independiente,
        // asi ningun carton es identico entre equipos.
        for (BingoTeam team : teams.values()) {
            Collections.shuffle(pool);
            List<BingoGoal> selected = new ArrayList<>();
            for (int i = 0; i < needed; i++) {
                selected.add(pool.get(i).copyFresh());
            }
            team.setCard(new BingoCard(size, selected));
        }

        clearAllPlayerInventories();

        state = GameState.RUNNING;
        if (configManager.getTimeLimitMinutes() > 0) {
            matchDurationMillis = configManager.getTimeLimitMinutes() * 60_000L;
            endTimeMillis = System.currentTimeMillis() + matchDurationMillis;
            timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkTimeLimit, 20L, 20L);

            if (bossBar != null) {
                bossBar.start();
                for (BingoTeam team : teams.values()) {
                    for (UUID uuid : team.getMembers()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) bossBar.addPlayer(p);
                    }
                }
                bossBar.update(matchDurationMillis, matchDurationMillis);
            }
        }

        inventoryScanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanAllInventories, 20L, 20L);

        if (scoreboard != null) {
            scoreboard.resetProgress();
            scoreboard.showRunning();
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "[Bingo] " + ChatColor.GREEN + "La partida ha comenzado! Usa /bingo card para ver tu carton.");
        autosave();
    }

    /**
     * Limpia el inventario (contenido, armadura, offhand) de todos los jugadores anotados
     * al arrancar una partida nueva, para que items de partidas anteriores no den progreso
     * gratis (via el escaneo de inventario) ni queden dando vueltas. Les vuelve a dar el
     * item fisico del carton despues de limpiar.
     */
    private void clearAllPlayerInventories() {
        for (BingoTeam team : teams.values()) {
            for (UUID uuid : team.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                saveInventoryIfNeeded(p);
                p.getInventory().clear();
                p.getInventory().setArmorContents(new ItemStack[4]);
                p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                p.getInventory().addItem(BingoCardItem.createForTeam(plugin, team));
            }
        }
    }

    /**
     * Regenera (borra y crea de nuevo, de forma nativa) el mundo de arena configurado y sus
     * mundos extra, ajusta el borde adaptativo de cada uno (y pregenera una zona chica cerca
     * del spawn), y RECIEN CUANDO TODO ESO TERMINA teletransporta a los jugadores y llama a
     * onReady. No depende de ningun plugin externo.
     */
    private void setupArenaWorldAsync(Runnable onReady) {
        if (multiverse == null || !configManager.isArenaWorldEnabled()) {
            onReady.run();
            return;
        }

        String world = configManager.getGameWorld();
        if (world == null || world.isEmpty()) {
            plugin.getLogger().warning("arena-world.enabled esta en true pero no hay arena-world.game-world configurado.");
            onReady.run();
            return;
        }

        this.currentArenaWorld = world;
        List<String> worldsToPrepare = new ArrayList<>();
        worldsToPrepare.add(world);
        worldsToPrepare.addAll(configManager.getExtraArenaWorlds());

        if (configManager.isRegenOnStart()) {
            // Bukkit.createWorld() es sincrono/bloqueante: cuando regenerateWorld() termina,
            // el mundo y su spawn seguro ya estan listos.
            multiverse.regenerateWorld(world);
            for (String extra : configManager.getExtraArenaWorlds()) {
                multiverse.regenerateWorld(extra);
            }
        }

        // El borde adaptativo (buscar estructuras + pregenerar una zona chica) es asincrono;
        // esperamos a que TODOS los mundos terminen antes de teletransportar a nadie, para
        // que no camine hacia una zona que todavia se esta generando.
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        if (worldBorderManager != null) {
            for (String w : worldsToPrepare) {
                World bukkitWorld = Bukkit.getWorld(w);
                if (bukkitWorld != null) {
                    futures.add(worldBorderManager.applyAdaptiveBorder(bukkitWorld, configManager));
                }
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((res, err) -> {
            if (err != null) {
                plugin.getLogger().warning("Error preparando el borde/pregeneracion de la arena: " + err.getMessage());
            }
            // Volvemos al hilo principal para teletransportar (la API de Bukkit no es thread-safe).
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (endEncounterManager != null) {
                    for (String preparedWorld : worldsToPrepare) {
                        World candidate = Bukkit.getWorld(preparedWorld);
                        if (candidate != null && candidate.getEnvironment() == World.Environment.THE_END) {
                            endEncounterManager.prepare(candidate);
                            break;
                        }
                    }
                }
                if (configManager.isTeleportPlayersOnStart()) {
                    for (BingoTeam team : teams.values()) {
                        for (UUID uuid : team.getMembers()) {
                            Player p = Bukkit.getPlayer(uuid);
                            if (p != null) {
                                teleportAndKeepScoreboard(p, world);
                            }
                        }
                    }
                }
                onReady.run();
            });
        });
    }

    /**
     * Escanea el inventario de cada jugador conectado y actualiza el progreso de los
     * objetivos ITEM_COLLECT / CRAFT_ITEM de su equipo. Esto es mucho mas confiable que
     * depender de eventos puntuales (recoger del suelo, craftear en una mesa vanilla),
     * ya que cubre items obtenidos por cualquier via: hornos, cofres, trueques,
     * sistemas de auto-recogida de mods, etc. Se usa el maximo historico visto (no la
     * cantidad actual), asi que gastar el item despues de conseguirlo no revierte el progreso.
     */
    private void scanAllInventories() {
        for (BingoTeam team : teams.values()) {
            BingoCard card = team.getCard();
            if (card == null) continue;

            for (BingoGoal goal : card.getGoals()) {
                if (goal.isCompleted()) continue;
                if (goal.getType() != GoalType.ITEM_COLLECT && goal.getType() != GoalType.CRAFT_ITEM) continue;

                Material material = MaterialResolver.resolve(goal.getTarget());
                if (material == null) continue;

                int total = 0;
                for (UUID uuid : team.getMembers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) continue;
                    total += countMaterial(p, material);
                }

                boolean justCompleted = goal.updateProgressIfHigher(total);
                if (justCompleted) {
                    onGoalCompleted(team, goal);
                }
            }

            if (team.getGuiInventory() != null) {
                com.arlight.bingo.gui.CardGUI.refresh(team.getGuiInventory(), card);
            }
        }
    }

    private int countMaterial(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) count += stack.getAmount();
        }
        for (ItemStack stack : player.getInventory().getArmorContents()) {
            if (stack != null && stack.getType() == material) count += stack.getAmount();
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.getType() == material) count += offhand.getAmount();
        return count;
    }

    private void checkTimeLimit() {
        long remaining = endTimeMillis - System.currentTimeMillis();
        bossBar.update(remaining, matchDurationMillis);

        if (remaining <= 0) {
            if (configManager.getWinCondition() == WinCondition.POINTS) {
                List<BingoTeam> leaders = announcePointsWinner();
                if (!leaders.isEmpty()) {
                    endMatchWithCelebration(leaders);
                    return;
                }
            } else {
                Bukkit.broadcastMessage(ChatColor.GOLD + "[Bingo] " + ChatColor.RED + "Se acabo el tiempo. Nadie completo el carton.");
            }
            stop();
        }
    }

    /** Anuncia quien tiene mas casillas completadas (1 punto por casilla) cuando se acaba el tiempo. Devuelve el/los ganador(es). */
    private List<BingoTeam> announcePointsWinner() {
        int bestPoints = -1;
        List<BingoTeam> leaders = new ArrayList<>();

        for (BingoTeam team : teams.values()) {
            if (team.getCard() == null) continue;
            int points = team.getCard().countCompleted();
            if (points > bestPoints) {
                bestPoints = points;
                leaders.clear();
                leaders.add(team);
            } else if (points == bestPoints) {
                leaders.add(team);
            }
        }

        Bukkit.broadcastMessage("");
        if (leaders.isEmpty() || bestPoints <= 0) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Bingo] " + ChatColor.RED + "Se acabo el tiempo. Nadie sumo puntos.");
            return new ArrayList<>();
        } else if (leaders.size() == 1) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[Bingo] ¡" + leaders.get(0).getName()
                    + " gano con " + bestPoints + " punto(s)!");
        } else {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < leaders.size(); i++) {
                if (i > 0) names.append(", ");
                names.append(leaders.get(i).getName());
            }
            Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[Bingo] Empate entre " + names
                    + " con " + bestPoints + " punto(s) cada uno!");
        }
        Bukkit.broadcastMessage("");
        return leaders;
    }

    public void stop() {
        haltGameSystems();
        sendPlayersBackToLobby();
        clearAllCards();
        state = GameState.WAITING;
        autosave();
        onPlayerCountChanged(); // reevalua si hay que arrancar la cuenta de nuevo y refresca el scoreboard de lobby
    }

    private void haltGameSystems() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (inventoryScanTask != null) {
            inventoryScanTask.cancel();
            inventoryScanTask = null;
        }
        cancelAutostartTaskOnly();
        bossBar.stop();
    }

    /** Quita el carton de todos los equipos (GUI y mapa cacheados tambien se invalidan). */
    private void clearAllCards() {
        for (BingoTeam team : teams.values()) {
            team.setCard(null);
        }
    }

    /**
     * Termina la partida con festejo: fuegos artificiales + titulo en pantalla para los
     * ganadores, y recien despues de "celebration-seconds" se devuelve a todos al lobby
     * y se vuelve al estado WAITING (con el scoreboard de sala de espera de nuevo).
     */
    private void endMatchWithCelebration(List<BingoTeam> winners) {
        state = GameState.ENDED; // durante el festejo dejamos el scoreboard de resultados como esta
        haltGameSystems();
        autosave();
        celebrate(winners);
        if (winListener != null) {
            winListener.onWin(winners);
        }

        int delaySeconds = configManager.getCelebrationSeconds();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.broadcastMessage(ChatColor.GRAY + "Volviendo al lobby...");
            sendPlayersBackToLobby();
            clearAllCards();
            state = GameState.WAITING;
            autosave();
            onPlayerCountChanged();
        }, delaySeconds * 20L);
    }

    private void celebrate(List<BingoTeam> winners) {
        String winnerNames = winners.size() == 1 ? winners.get(0).getName() : "Empate";

        for (BingoTeam team : teams.values()) {
            boolean isWinner = winners.contains(team);
            for (UUID uuid : team.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;

                if (isWinner) {
                    p.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "¡GANASTE EL BINGO!",
                            ChatColor.GREEN + "Buen juego!", 10, 70, 20);
                    launchFireworks(p);
                } else {
                    p.sendTitle(ChatColor.YELLOW + "Partida terminada",
                            ChatColor.GRAY + "Gano: " + winnerNames, 10, 70, 20);
                }
            }
        }
    }

    private void launchFireworks(Player player) {
        org.bukkit.Location loc = player.getLocation();
        org.bukkit.World world = loc.getWorld();
        if (world == null) return;

        for (int i = 0; i < 3; i++) {
            org.bukkit.entity.Firework firework = world.spawn(loc, org.bukkit.entity.Firework.class);
            org.bukkit.inventory.meta.FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(org.bukkit.FireworkEffect.builder()
                    .withColor(org.bukkit.Color.YELLOW, org.bukkit.Color.ORANGE, org.bukkit.Color.LIME)
                    .withFade(org.bukkit.Color.RED)
                    .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                    .trail(true)
                    .flicker(true)
                    .build());
            meta.setPower(1);
            firework.setFireworkMeta(meta);
        }
    }

    private void sendPlayersBackToLobby() {
        String lobby = configManager.getLobbyWorld();
        boolean shouldTeleport = multiverse != null
                && configManager.isArenaWorldEnabled()
                && configManager.isTeleportBackOnEnd()
                && lobby != null && !lobby.isEmpty();

        for (BingoTeam team : teams.values()) {
            for (UUID uuid : team.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    if (shouldTeleport) teleportAndKeepScoreboard(p, lobby);
                    restoreSavedInventory(p);
                    endCoreSession(p);
                }
            }
        }
    }

    /**
     * Cancela la tarea del temporizador sin cambiar el estado de la partida ni autoguardar.
     * Se usa al deshabilitar el plugin (ej. reinicio del servidor), donde ya guardamos el
     * estado tal cual estaba (RUNNING incluido) para poder restaurarlo despues.
     */
    public void cancelTimerOnly() {
        haltGameSystems();
    }

    public void reset() {
        stop();
        state = GameState.WAITING;
        teams.clear();
        playerTeamName.clear();
    }

    private void saveInventoryIfNeeded(Player player) {
        savedInventories.computeIfAbsent(player.getUniqueId(), ignored ->
                new SavedInventory(
                        cloneItems(player.getInventory().getStorageContents()),
                        cloneItems(player.getInventory().getArmorContents()),
                        cloneItem(player.getInventory().getItemInOffHand()),
                        player.getInventory().getHeldItemSlot()
                ));
    }

    private void restoreSavedInventory(Player player) {
        SavedInventory saved = savedInventories.remove(player.getUniqueId());
        if (saved == null) return;

        player.getInventory().clear();
        player.getInventory().setStorageContents(cloneItems(saved.storage()));
        player.getInventory().setArmorContents(cloneItems(saved.armor()));
        player.getInventory().setItemInOffHand(cloneItem(saved.offHand()));
        player.getInventory().setHeldItemSlot(saved.heldSlot());
        player.updateInventory();
    }

    private void endCoreSession(Player player) {
        if (sessionBridge != null) sessionBridge.end(player);
    }

    private ItemStack[] cloneItems(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = cloneItem(source[i]);
        }
        return copy;
    }

    private ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private record SavedInventory(
            ItemStack[] storage,
            ItemStack[] armor,
            ItemStack offHand,
            int heldSlot
    ) {
    }

    /**
     * Llamar cada vez que una casilla se completa para un equipo/jugador.
     * Revisa condicion de victoria y termina la partida si corresponde.
     */
    public void onGoalCompleted(BingoTeam team, BingoGoal goal) {
        if (state != GameState.RUNNING) return;

        if (configManager.isBroadcastGoalCompletion()) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Bingo] " + ChatColor.AQUA + team.getName()
                    + ChatColor.WHITE + " completo: " + ChatColor.YELLOW + goal.getDisplayName());
        }
        if (scoreboard != null) {
            scoreboard.updateTeamProgress(team.getName(), team.getCard().countCompleted(), goal.getDisplayName());
        }

        WinCondition wc = configManager.getWinCondition();
        boolean won;
        if (wc == WinCondition.POINTS) {
            // En modo puntos, completar el carton entero sigue siendo una victoria instantanea
            // (bonus); si no, se sigue jugando y se decide por puntos cuando se acabe el tiempo.
            won = team.getCard().isFullCardComplete();
        } else {
            won = (wc == WinCondition.LINE && team.getCard().hasLineComplete())
                    || ((wc == WinCondition.FULL_CARD || wc == WinCondition.BLACKOUT) && team.getCard().isFullCardComplete());
        }

        if (won) {
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[Bingo] ¡" + team.getName() + " ha ganado la partida de Bingo!");
            Bukkit.broadcastMessage("");
            endMatchWithCelebration(Collections.singletonList(team));
        } else {
            autosave();
        }
    }

    /**
     * Reconstruye el estado de la partida a partir de datos guardados (StorageManager),
     * por ejemplo tras un reinicio del servidor a mitad de partida.
     */
    public void restoreFromStorage(GameState savedState, Map<String, BingoTeam> savedTeams, long savedEndTimeMillis, String savedArenaWorld) {
        teams.clear();
        playerTeamName.clear();
        teams.putAll(savedTeams);
        for (BingoTeam team : teams.values()) {
            for (UUID uuid : team.getMembers()) {
                playerTeamName.put(uuid, team.getName());
            }
        }

        this.state = savedState;
        this.currentArenaWorld = savedArenaWorld;
        if (state == GameState.RUNNING) {
            inventoryScanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanAllInventories, 20L, 20L);
            if (configManager.getTimeLimitMinutes() > 0 && savedEndTimeMillis > 0) {
                this.matchDurationMillis = configManager.getTimeLimitMinutes() * 60_000L;
                this.endTimeMillis = savedEndTimeMillis;
                timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkTimeLimit, 20L, 20L);

                bossBar.start();
                for (BingoTeam team : teams.values()) {
                    for (UUID uuid : team.getMembers()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) bossBar.addPlayer(p);
                    }
                }
            }
        }
    }

    public long getEndTimeMillis() {
        return endTimeMillis;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
