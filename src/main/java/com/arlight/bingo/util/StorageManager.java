package com.arlight.bingo.util;

import com.arlight.bingo.game.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Guarda el estado de la partida en curso (equipos, cartones, progreso) en un
 * archivo gamedata.yml dentro de la carpeta del plugin, para que si el servidor
 * se reinicia a mitad de partida, esta se pueda restaurar tal cual estaba.
 */
public class StorageManager {

    private final JavaPlugin plugin;
    private final File file;

    public StorageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gamedata.yml");
    }

    public void save(BingoGame game) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("state", game.getState().name());
        yaml.set("end-time-millis", game.getEndTimeMillis());
        yaml.set("arena-world", game.getCurrentArenaWorld());

        int index = 0;
        for (BingoTeam team : game.getTeams()) {
            // Usamos un indice numerico como clave de ruta (en vez del nombre del equipo)
            // para no tener problemas con puntos u otros caracteres especiales en el nombre.
            String base = "teams." + index;
            yaml.set(base + ".team-name", team.getName());

            List<String> members = new ArrayList<>();
            for (UUID uuid : team.getMembers()) members.add(uuid.toString());
            yaml.set(base + ".members", members);

            BingoCard card = team.getCard();
            if (card != null) {
                yaml.set(base + ".card.size", card.getSize());
                List<Map<String, Object>> goalsData = new ArrayList<>();
                for (BingoGoal goal : card.getGoals()) {
                    Map<String, Object> gm = new LinkedHashMap<>();
                    gm.put("id", goal.getId());
                    gm.put("type", goal.getType().name());
                    gm.put("target", goal.getTarget());
                    gm.put("amount-required", goal.getAmountRequired());
                    gm.put("display-name", goal.getDisplayName());
                    gm.put("progress", goal.getProgress());
                    gm.put("completed", goal.isCompleted());
                    goalsData.add(gm);
                }
                yaml.set(base + ".card.goals", goalsData);
            }
            index++;
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar el estado de la partida de bingo.", e);
        }
    }

    /** Devuelve true si habia una partida guardada y se restauro. */
    public boolean load(BingoGame game) {
        if (!file.exists()) return false;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        GameState state;
        try {
            state = GameState.valueOf(yaml.getString("state", "WAITING"));
        } catch (IllegalArgumentException e) {
            state = GameState.WAITING;
        }
        long endTimeMillis = yaml.getLong("end-time-millis", 0);
        String arenaWorld = yaml.getString("arena-world", null);

        Map<String, BingoTeam> teams = new LinkedHashMap<>();
        ConfigurationSection teamsSection = yaml.getConfigurationSection("teams");
        if (teamsSection != null) {
            for (String key : teamsSection.getKeys(false)) {
                ConfigurationSection ts = teamsSection.getConfigurationSection(key);
                if (ts == null) continue;

                String teamName = ts.getString("team-name", key);
                BingoTeam team = new BingoTeam(teamName);

                for (String uuidStr : ts.getStringList("members")) {
                    try {
                        team.addMember(UUID.fromString(uuidStr));
                    } catch (IllegalArgumentException ignored) {
                        // uuid corrupto en el archivo, se ignora ese miembro
                    }
                }

                ConfigurationSection cardSec = ts.getConfigurationSection("card");
                if (cardSec != null) {
                    int size = cardSec.getInt("size", 5);
                    List<Map<?, ?>> goalsRaw = cardSec.getMapList("goals");
                    List<BingoGoal> goals = new ArrayList<>();
                    for (Map<?, ?> gm : goalsRaw) {
                        try {
                            String id = String.valueOf(gm.get("id"));
                            GoalType type = GoalType.valueOf(String.valueOf(gm.get("type")));
                            Object targetObj = gm.get("target");
                            String target = targetObj == null || targetObj.equals("null") ? null : String.valueOf(targetObj);
                            Object amountObj = gm.get("amount-required");
                            int amountRequired = amountObj == null ? 1 : ((Number) amountObj).intValue();
                            String displayName = String.valueOf(gm.get("display-name"));
                            Object progressObj = gm.get("progress");
                            int progress = progressObj == null ? 0 : ((Number) progressObj).intValue();
                            boolean completed = Boolean.TRUE.equals(gm.get("completed"));

                            BingoGoal goal = new BingoGoal(id, type, target, amountRequired, displayName);
                            if (completed) {
                                goal.forceComplete();
                            } else if (progress > 0) {
                                goal.addProgress(progress);
                            }
                            goals.add(goal);
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "Objetivo corrupto en gamedata.yml, se ignora.", e);
                        }
                    }
                    team.setCard(new BingoCard(size, goals));
                }

                teams.put(teamName, team);
            }
        }

        game.restoreFromStorage(state, teams, endTimeMillis, arenaWorld);
        return true;
    }
}
