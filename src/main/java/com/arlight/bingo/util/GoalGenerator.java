package com.arlight.bingo.util;

import com.arlight.bingo.game.BingoGoal;
import com.arlight.bingo.game.GoalType;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.*;

/**
 * Genera objetivos de bingo al azar a partir de los Material/EntityType de Bukkit,
 * asi el admin no tiene que listar cada objetivo a mano en config.yml.
 *
 * Filtra (best-effort, no es una lista exhaustiva perfecta) bloques/items que no son
 * obtenibles de forma normal en survival: bedrock, barrier, command blocks, structure
 * blocks, jigsaw, spawners, etc. Ademas respeta la blacklist configurable del admin
 * (items puntuales y mods enteros baneados con /bingo blacklist).
 */
public class GoalGenerator {

    private static final Set<String> BLACKLIST = new HashSet<>(Arrays.asList(
            "BEDROCK", "BARRIER", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK",
            "COMMAND_BLOCK_MINECART", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "DEBUG_STICK",
            "LIGHT", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "END_PORTAL_FRAME", "END_PORTAL", "END_GATEWAY",
            "NETHER_PORTAL", "MOVING_PISTON", "PETRIFIED_OAK_SLAB", "KNOWLEDGE_BOOK", "DRAGON_EGG",
            "REINFORCED_DEEPSLATE", "FIRE", "SOUL_FIRE", "AIR", "CAVE_AIR", "VOID_AIR", "BUBBLE_COLUMN",
            "WATER", "LAVA", "FROSTED_ICE", "SPAWNER_MINECART", "LEGACY_AIR"
    ));

    private static final Set<EntityType> ENTITY_BLACKLIST = new HashSet<>(Arrays.asList(
            EntityType.PLAYER, EntityType.ARMOR_STAND, EntityType.GIANT
    ));

    private GoalGenerator() {
    }

    public static List<BingoGoal> generate(int amount, Set<String> blacklistItems, Set<String> blacklistMods, boolean vanillaOnly) {
        List<BingoGoal> candidates = new ArrayList<>();

        for (Material material : Material.values()) {
            if (material.isLegacy() || material.isAir() || BLACKLIST.contains(material.name())) continue;
            if (material.name().endsWith("_SPAWN_EGG")) continue; // no tiene sentido pedir "consigue el huevo de X"

            String key = keyOf(material);
            if (vanillaOnly && !isVanilla(key)) continue;
            if (isBlacklisted(key, blacklistItems, blacklistMods)) continue;

            if (material.isItem()) {
                candidates.add(new BingoGoal(
                        "auto_item_" + material.name().toLowerCase(Locale.ROOT),
                        GoalType.ITEM_COLLECT,
                        key,
                        1,
                        "Consigue " + spanishName(material.name())
                ));
            }

            if (material.isBlock() && material.isItem()) {
                candidates.add(new BingoGoal(
                        "auto_break_" + material.name().toLowerCase(Locale.ROOT),
                        GoalType.BLOCK_BREAK,
                        key,
                        1,
                        "Rompe " + spanishName(material.name())
                ));
            }
        }

        for (EntityType type : EntityType.values()) {
            if (ENTITY_BLACKLIST.contains(type)) continue;
            Class<?> entityClass;
            try {
                entityClass = type.getEntityClass();
            } catch (Exception e) {
                continue;
            }
            if (entityClass == null || !LivingEntity.class.isAssignableFrom(entityClass)) continue;

            String key = keyOf(type);
            if (vanillaOnly && !isVanilla(key)) continue;
            if (isBlacklisted(key, blacklistItems, blacklistMods)) continue;

            candidates.add(new BingoGoal(
                    "auto_kill_" + type.name().toLowerCase(Locale.ROOT),
                    GoalType.ENTITY_KILL,
                    key,
                    1,
                    "Mata a " + spanishName(type.name())
            ));
        }

        Collections.shuffle(candidates);
        return candidates.size() > amount ? new ArrayList<>(candidates.subList(0, amount)) : candidates;
    }

    private static boolean isVanilla(String key) {
        return key.toLowerCase(Locale.ROOT).startsWith("minecraft:");
    }

    private static boolean isBlacklisted(String key, Set<String> blacklistItems, Set<String> blacklistMods) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (blacklistItems.contains(lower)) return true;
        String namespace = lower.contains(":") ? lower.split(":", 2)[0] : lower;
        return blacklistMods.contains(namespace);
    }

    /**
     * Usa el namespaced key REAL del Material/EntityType (material.getKey() /
     * type.getKey()), en vez de asumir siempre "minecraft:", para que esto
     * funcione tambien con materiales/entidades que un servidor hibrido como
     * Arclight haya registrado bajo el namespace de un mod.
     */
    private static String keyOf(Material material) {
        try {
            return material.getKey().getNamespace() + ":" + material.getKey().getKey();
        } catch (Exception e) {
            return "minecraft:" + material.name().toLowerCase(Locale.ROOT);
        }
    }

    private static String keyOf(EntityType type) {
        try {
            return type.getKey().getNamespace() + ":" + type.getKey().getKey();
        } catch (Exception e) {
            return "minecraft:" + type.name().toLowerCase(Locale.ROOT);
        }
    }

    private static String spanishName(String enumName) {
        return SpanishNames.translateOrFallback(enumName, prettyName(enumName));
    }

    private static String prettyName(String enumName) {
        String[] parts = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
