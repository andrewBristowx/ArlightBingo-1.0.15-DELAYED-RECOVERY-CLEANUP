package com.arlight.bingo.commands;

import com.arlight.bingo.game.BingoGame;
import com.arlight.bingo.game.BingoGoal;
import com.arlight.bingo.game.BingoTeam;
import com.arlight.bingo.game.GameState;
import com.arlight.bingo.gui.BingoCardItem;
import com.arlight.bingo.gui.CardGUI;
import com.arlight.bingo.util.ConfigManager;
import com.arlight.bingo.util.ArenaWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BingoCommand implements CommandExecutor, TabCompleter {

    private final BingoGame game;
    private final JavaPlugin plugin;

    public BingoCommand(BingoGame game, JavaPlugin plugin) {
        this.game = game;
        this.plugin = plugin;
    }

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "start", "stop", "join", "leave", "card", "carditem", "lobby", "arena",
            "trigger", "world", "blacklist", "vanillaonly", "reload"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase();

        if (args.length == 2) {
            switch (sub) {
                case "world":
                    return filter(Arrays.asList("lobby", "game", "info", "resetarena", "addextra", "removeextra"), args[1]);
                case "blacklist":
                    return filter(Arrays.asList("item", "mod", "removeitem", "removemod", "list"), args[1]);
                case "vanillaonly":
                    return filter(Arrays.asList("true", "false"), args[1]);
                case "trigger":
                    return filter(onlinePlayerNames(), args[1]);
                default:
                    return List.of();
            }
        }

        if (args.length == 3) {
            switch (sub) {
                case "world":
                    if (args[1].equalsIgnoreCase("lobby") || args[1].equalsIgnoreCase("game")
                            || args[1].equalsIgnoreCase("resetarena") || args[1].equalsIgnoreCase("addextra")) {
                        return filter(worldNames(), args[2]);
                    }
                    if (args[1].equalsIgnoreCase("removeextra")) {
                        return filter(new ArrayList<>(game.getConfigManager().getExtraArenaWorlds()), args[2]);
                    }
                    return List.of();
                case "blacklist":
                    if (args[1].equalsIgnoreCase("removeitem")) {
                        return filter(new ArrayList<>(game.getConfigManager().getBlacklistItems()), args[2]);
                    }
                    if (args[1].equalsIgnoreCase("removemod")) {
                        return filter(new ArrayList<>(game.getConfigManager().getBlacklistMods()), args[2]);
                    }
                    return List.of();
                case "trigger": {
                    Player target = Bukkit.getPlayerExact(args[1]);
                    BingoTeam team = target != null ? game.getTeamOf(target) : null;
                    if (team == null || team.getCard() == null) return List.of();
                    List<String> ids = team.getCard().getGoals().stream().map(BingoGoal::getId).collect(Collectors.toList());
                    return filter(ids, args[2]);
                }
                default:
                    return List.of();
            }
        }

        return List.of();
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    private List<String> worldNames() {
        return Bukkit.getWorlds().stream().map(w -> w.getName()).collect(Collectors.toList());
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /bingo <start|stop|join|leave|card|carditem|lobby|arena|trigger|world|blacklist|vanillaonly|reload>");
            return true;
        }

        String sub = args[0].toLowerCase();

        // Mientras la partida esta EN CURSO, los jugadores normales (sin permiso de admin) solo
        // pueden usar /bingo arena y /bingo leave -- el resto (join, card, lobby, etc.) no tiene
        // sentido a mitad de partida o ya lo reemplaza el item fisico del carton.
        boolean isEssentialDuringMatch = sub.equals("arena") || sub.equals("leave");
        if (game.getState() == GameState.RUNNING && sender instanceof Player
                && !sender.hasPermission("arlightbingo.admin") && !isEssentialDuringMatch) {
            sender.sendMessage(ChatColor.RED + "Durante la partida solo podes usar /bingo arena y /bingo leave.");
            return true;
        }

        switch (sub) {
            case "start":
                if (!checkAdmin(sender)) return true;
                game.start();
                return true;

            case "stop":
                if (!checkAdmin(sender)) return true;
                game.stop();
                sender.sendMessage(ChatColor.YELLOW + "Partida detenida.");
                return true;

            case "reload":
                if (!checkAdmin(sender)) return true;
                game.getConfigManager().load();
                sender.sendMessage(ChatColor.GREEN + "Configuracion recargada.");
                return true;

            case "join": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Solo un jugador puede usar esto.");
                    return true;
                }
                Player player = (Player) sender;
                String teamName = args.length > 1 ? args[1] : null;
                game.addPlayer(player, teamName);
                return true;
            }

            case "leave": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                if (game.getState() == GameState.RUNNING) {
                    game.disqualifyPlayer(player, ChatColor.RED + player.getName() + " abandono el Bingo y quedo descalificado.");
                } else {
                    game.removePlayer(player);
                    sender.sendMessage(ChatColor.YELLOW + "Has salido de la partida.");
                }
                return true;
            }

            case "card": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                BingoTeam team = game.getTeamOf(player);
                if (team == null || team.getCard() == null) {
                    sender.sendMessage(ChatColor.RED + "No tienes un carton activo todavia.");
                    return true;
                }
                player.openInventory(getOrBuildInventory(team));
                return true;
            }

            case "trigger": {
                if (!checkAdmin(sender)) return true;
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Uso: /bingo trigger <jugador> <idObjetivo>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Jugador no encontrado (debe estar conectado).");
                    return true;
                }
                BingoTeam team = game.getTeamOf(target);
                if (team == null || team.getCard() == null) {
                    sender.sendMessage(ChatColor.RED + "Ese jugador no tiene carton activo.");
                    return true;
                }
                BingoGoal goal = team.getCard().findById(args[2]);
                if (goal == null) {
                    sender.sendMessage(ChatColor.RED + "No existe ese id de objetivo en el carton.");
                    return true;
                }
                if (!goal.isCompleted()) {
                    goal.forceComplete();
                    game.onGoalCompleted(team, goal);
                }
                sender.sendMessage(ChatColor.GREEN + "Objetivo marcado como completado.");
                return true;
            }

            case "carditem": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                BingoTeam team = game.getTeamOf(player);
                org.bukkit.inventory.ItemStack cardItem = team != null
                        ? BingoCardItem.createForTeam(plugin, team)
                        : BingoCardItem.create(plugin);
                player.getInventory().addItem(cardItem);
                player.sendMessage(ChatColor.GREEN + "Te dimos un carton de Bingo (click derecho para verlo).");
                return true;
            }

            case "vanillaonly": {
                if (!checkAdmin(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Uso: /bingo vanillaonly <true|false>  (actual: "
                            + game.getConfigManager().isVanillaOnly() + ")");
                    return true;
                }
                boolean value = Boolean.parseBoolean(args[1]);
                game.getConfigManager().setVanillaOnly(value);
                sender.sendMessage(ChatColor.GREEN + "vanilla-only ahora en " + value
                        + " (pool de objetivos regenerado).");
                return true;
            }

            case "blacklist": {
                if (!checkAdmin(sender)) return true;
                if (!(sender instanceof Player) && args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Desde consola necesitas dar el id explicito.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Uso: /bingo blacklist <item|mod|removeitem|removemod|list> [valor]");
                    return true;
                }
                Player p = (sender instanceof Player) ? (Player) sender : null;

                switch (args[1].toLowerCase()) {
                    case "item": {
                        String key = args.length >= 3 ? args[2] : heldItemKey(p, sender);
                        if (key == null) return true;
                        boolean added = game.getConfigManager().addBlacklistItem(key);
                        sender.sendMessage(added
                                ? ChatColor.GREEN + key + " agregado a la blacklist de items."
                                : ChatColor.YELLOW + "Ese item ya estaba en la blacklist.");
                        return true;
                    }
                    case "mod": {
                        String modId = args.length >= 3 ? args[2] : heldItemModId(p, sender);
                        if (modId == null) return true;
                        boolean added = game.getConfigManager().addBlacklistMod(modId);
                        sender.sendMessage(added
                                ? ChatColor.GREEN + "Mod '" + modId + "' baneado por completo de los objetivos."
                                : ChatColor.YELLOW + "Ese mod ya estaba baneado.");
                        return true;
                    }
                    case "removeitem": {
                        if (args.length < 3) {
                            sender.sendMessage(ChatColor.RED + "Uso: /bingo blacklist removeitem <namespace:id>");
                            return true;
                        }
                        boolean removed = game.getConfigManager().removeBlacklistItem(args[2]);
                        sender.sendMessage(removed
                                ? ChatColor.GREEN + "Quitado de la blacklist."
                                : ChatColor.YELLOW + "No estaba en la blacklist.");
                        return true;
                    }
                    case "removemod": {
                        if (args.length < 3) {
                            sender.sendMessage(ChatColor.RED + "Uso: /bingo blacklist removemod <modid>");
                            return true;
                        }
                        boolean removed = game.getConfigManager().removeBlacklistMod(args[2]);
                        sender.sendMessage(removed
                                ? ChatColor.GREEN + "Mod quitado del ban."
                                : ChatColor.YELLOW + "Ese mod no estaba baneado.");
                        return true;
                    }
                    case "list": {
                        sender.sendMessage(ChatColor.GOLD + "Items baneados: " + ChatColor.WHITE + game.getConfigManager().getBlacklistItems());
                        sender.sendMessage(ChatColor.GOLD + "Mods baneados: " + ChatColor.WHITE + game.getConfigManager().getBlacklistMods());
                        return true;
                    }
                    default:
                        sender.sendMessage(ChatColor.RED + "Subcomando de blacklist desconocido.");
                        return true;
                }
            }

            case "lobby": {
                if (!(sender instanceof Player)) return true;
                ArenaWorldManager mv = game.getArenaWorldManager();
                String lobby = game.getConfigManager().getLobbyWorld();
                if (mv == null || lobby == null || lobby.isEmpty() || !mv.teleportToWorld((Player) sender, lobby)) {
                    sender.sendMessage(ChatColor.RED + "No hay un lobby configurado o no esta cargado.");
                }
                return true;
            }

            case "arena": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                ArenaWorldManager mv = game.getArenaWorldManager();
                String arena = game.getCurrentArenaWorld();
                if (game.getState() != GameState.RUNNING || arena == null || mv == null) {
                    sender.sendMessage(ChatColor.RED + "No hay ninguna partida en curso.");
                    return true;
                }
                if (game.getTeamOf(player) == null) {
                    sender.sendMessage(ChatColor.RED + "No estas anotado en la partida en curso.");
                    return true;
                }
                if (!mv.teleportToWorld(player, arena)) {
                    sender.sendMessage(ChatColor.RED + "El mundo de la arena no esta cargado.");
                }
                return true;
            }

            case "world": {
                if (!checkAdmin(sender)) return true;
                ConfigManager cfg = game.getConfigManager();
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Uso: /bingo world <lobby|game|info|resetarena|addextra|removeextra> [mundo]");
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "lobby":
                        if (args.length < 3) {
                            sender.sendMessage(ChatColor.RED + "Uso: /bingo world lobby <mundo>");
                            return true;
                        }
                        cfg.setLobbyWorld(args[2]);
                        sender.sendMessage(ChatColor.GREEN + "Mundo lobby seteado a " + args[2] + ".");
                        return true;
                    case "game":
                        if (args.length < 3) {
                            sender.sendMessage(ChatColor.RED + "Uso: /bingo world game <mundo>");
                            return true;
                        }
                        cfg.setGameWorld(args[2]);
                        sender.sendMessage(ChatColor.GREEN + "Mundo de partida seteado a " + args[2] + ".");
                        return true;
                    case "info":
                        sender.sendMessage(ChatColor.GOLD + "Lobby: " + ChatColor.WHITE + cfg.getLobbyWorld());
                        sender.sendMessage(ChatColor.GOLD + "Mundo de partida: " + ChatColor.WHITE + cfg.getGameWorld());
                        sender.sendMessage(ChatColor.GOLD + "Mundos extra (nether/end, etc.): " + ChatColor.WHITE + cfg.getExtraArenaWorlds());
                        return true;
                    case "addextra": {
                        if (args.length < 3) {
                            sender.sendMessage(ChatColor.RED + "Uso: /bingo world addextra <mundo>  (ej. el nether/end de la arena)");
                            return true;
                        }
                        boolean added = cfg.addExtraArenaWorld(args[2]);
                        sender.sendMessage(added
                                ? ChatColor.GREEN + args[2] + " agregado como mundo extra (se regenerara junto con la arena)."
                                : ChatColor.YELLOW + "Ese mundo ya estaba en la lista.");
                        return true;
                    }
                    case "removeextra": {
                        if (args.length < 3) {
                            sender.sendMessage(ChatColor.RED + "Uso: /bingo world removeextra <mundo>");
                            return true;
                        }
                        boolean removed = cfg.removeExtraArenaWorld(args[2]);
                        sender.sendMessage(removed
                                ? ChatColor.GREEN + args[2] + " quitado de los mundos extra."
                                : ChatColor.YELLOW + "Ese mundo no estaba en la lista.");
                        return true;
                    }
                    case "resetarena": {
                        String target = args.length >= 3 ? args[2] : cfg.getGameWorld();
                        ArenaWorldManager mgr = game.getArenaWorldManager();
                        if (mgr == null || target == null || target.isEmpty()) {
                            sender.sendMessage(ChatColor.RED + "No hay mundo de partida configurado.");
                            return true;
                        }
                        sender.sendMessage(ChatColor.YELLOW + "Confirmando y regenerando '" + target + "' ahora mismo...");
                        mgr.forceClaimAndRegenerate(target);
                        sender.sendMessage(ChatColor.GREEN + "Listo. De ahora en mas el plugin regenerara ese mundo solo en cada partida.");
                        return true;
                    }
                    default:
                        sender.sendMessage(ChatColor.RED + "Subcomando de world desconocido.");
                        return true;
                }
            }

            default:
                sender.sendMessage(ChatColor.RED + "Subcomando desconocido.");
                return true;
        }
    }

    private org.bukkit.inventory.Inventory getOrBuildInventory(BingoTeam team) {
        org.bukkit.inventory.Inventory inv = team.getGuiInventory();
        if (inv == null) {
            inv = CardGUI.build(team.getCard());
            team.setGuiInventory(inv);
        }
        return inv;
    }

    private boolean checkAdmin(CommandSender sender) {
        if (!sender.hasPermission("arlightbingo.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permiso para hacer eso.");
            return false;
        }
        return true;
    }

    /** Devuelve el namespaced key ("modid:item") del item en la mano principal, o null (con mensaje) si no hay uno valido. */
    private String heldItemKey(Player player, CommandSender sender) {
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Necesitas ser un jugador sosteniendo el item, o dar el id explicito.");
            return null;
        }
        org.bukkit.inventory.ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            sender.sendMessage(ChatColor.RED + "Tenes que sostener el item en la mano principal.");
            return null;
        }
        return held.getType().getKey().getNamespace() + ":" + held.getType().getKey().getKey();
    }

    /** Devuelve solo el namespace/modid del item en la mano principal. */
    private String heldItemModId(Player player, CommandSender sender) {
        String key = heldItemKey(player, sender);
        if (key == null) return null;
        return key.contains(":") ? key.split(":", 2)[0] : key;
    }
}
