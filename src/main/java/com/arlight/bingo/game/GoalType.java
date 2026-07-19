package com.arlight.bingo.game;

/**
 * Tipos de objetivo soportados por una casilla de bingo.
 *
 * ITEM_COLLECT / BLOCK_BREAK / BLOCK_PLACE / ENTITY_KILL / ADVANCEMENT
 * se detectan automaticamente mediante eventos de Bukkit (funcionan
 * tanto para contenido vanilla como para la mayoria de contenido modded,
 * ya que Arclight expone los items/bloques/entidades de los mods como
 * Material/EntityType con su propio NamespacedKey).
 *
 * CUSTOM_TRIGGER no se detecta solo: se completa manualmente via
 * comando ("/bingo trigger <jugador> <id>"), pensado para logica interna
 * de mods que no dispara eventos Bukkit estandar.
 */
public enum GoalType {
    ITEM_COLLECT,
    CRAFT_ITEM,
    BLOCK_BREAK,
    BLOCK_PLACE,
    ENTITY_KILL,
    ADVANCEMENT,
    CUSTOM_TRIGGER
}
