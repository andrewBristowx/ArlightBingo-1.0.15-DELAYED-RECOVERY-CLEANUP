package com.arlight.bingo.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Traduce el nombre tecnico (constante de Material/EntityType, ej. "DIAMOND_SWORD") a un
 * nombre en espanol para mostrar en los objetivos del bingo. Cubre los items/bloques/mobs
 * vanilla mas comunes (los que suelen salir en los objetivos generados automaticamente).
 * Si un nombre no esta en el diccionario, se devuelve el nombre "bonito" en ingles como
 * respaldo (nunca se rompe, solo se ve en ingles ese caso puntual).
 *
 * Si el admin quiere ampliar o corregir alguna traduccion, puede editarse este archivo
 * y recompilar -- no esta pensado como configuracion en caliente, ya que son nombres fijos.
 */
public class SpanishNames {

    private static final Map<String, String> NAMES = new HashMap<>();

    private SpanishNames() {
    }

    public static String translateOrFallback(String enumName, String englishFallback) {
        String es = NAMES.get(enumName.toUpperCase(Locale.ROOT));
        return es != null ? es : englishFallback;
    }

    static {
        // --- Minerales, lingotes y gemas ---
        put("COAL", "Carbon"); put("COAL_ORE", "Mineral de carbon"); put("DEEPSLATE_COAL_ORE", "Mineral de carbon (deepslate)");
        put("IRON_INGOT", "Lingote de hierro"); put("IRON_ORE", "Mineral de hierro"); put("DEEPSLATE_IRON_ORE", "Mineral de hierro (deepslate)"); put("RAW_IRON", "Hierro en bruto");
        put("GOLD_INGOT", "Lingote de oro"); put("GOLD_ORE", "Mineral de oro"); put("DEEPSLATE_GOLD_ORE", "Mineral de oro (deepslate)"); put("RAW_GOLD", "Oro en bruto"); put("GOLD_NUGGET", "Pepita de oro");
        put("DIAMOND", "Diamante"); put("DIAMOND_ORE", "Mineral de diamante"); put("DEEPSLATE_DIAMOND_ORE", "Mineral de diamante (deepslate)");
        put("EMERALD", "Esmeralda"); put("EMERALD_ORE", "Mineral de esmeralda"); put("DEEPSLATE_EMERALD_ORE", "Mineral de esmeralda (deepslate)");
        put("LAPIS_LAZULI", "Lapislazuli"); put("LAPIS_ORE", "Mineral de lapislazuli"); put("DEEPSLATE_LAPIS_ORE", "Mineral de lapislazuli (deepslate)");
        put("REDSTONE", "Redstone"); put("REDSTONE_ORE", "Mineral de redstone"); put("DEEPSLATE_REDSTONE_ORE", "Mineral de redstone (deepslate)");
        put("COPPER_INGOT", "Lingote de cobre"); put("COPPER_ORE", "Mineral de cobre"); put("DEEPSLATE_COPPER_ORE", "Mineral de cobre (deepslate)"); put("RAW_COPPER", "Cobre en bruto");
        put("NETHERITE_INGOT", "Lingote de netherita"); put("NETHERITE_SCRAP", "Chatarra de netherita"); put("ANCIENT_DEBRIS", "Escombros antiguos");
        put("QUARTZ", "Cuarzo del Nether"); put("NETHER_QUARTZ_ORE", "Mineral de cuarzo del Nether");
        put("AMETHYST_SHARD", "Fragmento de amatista");

        // --- Bloques comunes ---
        put("STONE", "Piedra"); put("COBBLESTONE", "Piedra tallada"); put("DEEPSLATE", "Deepslate"); put("COBBLED_DEEPSLATE", "Deepslate tallada");
        put("DIRT", "Tierra"); put("GRASS_BLOCK", "Bloque de hierba"); put("SAND", "Arena"); put("RED_SAND", "Arena roja"); put("GRAVEL", "Grava");
        put("OAK_LOG", "Tronco de roble"); put("SPRUCE_LOG", "Tronco de abeto"); put("BIRCH_LOG", "Tronco de abedul");
        put("JUNGLE_LOG", "Tronco de jungla"); put("ACACIA_LOG", "Tronco de acacia"); put("DARK_OAK_LOG", "Tronco de roble oscuro");
        put("MANGROVE_LOG", "Tronco de mangle"); put("CHERRY_LOG", "Tronco de cerezo");
        put("OAK_PLANKS", "Tablones de roble"); put("SPRUCE_PLANKS", "Tablones de abeto"); put("BIRCH_PLANKS", "Tablones de abedul");
        put("GLASS", "Vidrio"); put("OBSIDIAN", "Obsidiana"); put("BEDROCK", "Roca madre");
        put("NETHERRACK", "Piedra del Nether"); put("SOUL_SAND", "Arena de las almas"); put("SOUL_SOIL", "Tierra de las almas");
        put("END_STONE", "Piedra del End"); put("PURPUR_BLOCK", "Bloque de purpur");
        put("ICE", "Hielo"); put("PACKED_ICE", "Hielo compacto"); put("BLUE_ICE", "Hielo azul"); put("SNOW_BLOCK", "Bloque de nieve");
        put("CLAY", "Arcilla"); put("TERRACOTTA", "Terracota"); put("BRICK", "Ladrillo"); put("BRICKS", "Bloque de ladrillos");
        put("MOSS_BLOCK", "Bloque de musgo"); put("MUD", "Barro"); put("CALCITE", "Calcita"); put("TUFF", "Toba");

        // --- Comida ---
        put("APPLE", "Manzana"); put("BREAD", "Pan"); put("COOKED_BEEF", "Bistec"); put("BEEF", "Carne de res cruda");
        put("COOKED_PORKCHOP", "Chuleta de cerdo asada"); put("PORKCHOP", "Chuleta de cerdo cruda");
        put("COOKED_CHICKEN", "Pollo asado");
        put("COOKED_MUTTON", "Cordero asado"); put("MUTTON", "Cordero crudo");
        put("COOKED_RABBIT", "Conejo asado");
        put("COOKED_COD", "Bacalao asado"); put("COD", "Bacalao crudo");
        put("COOKED_SALMON", "Salmon asado"); put("SALMON", "Salmon crudo");
        put("CARROT", "Zanahoria"); put("POTATO", "Papa"); put("BAKED_POTATO", "Papa asada"); put("BEETROOT", "Remolacha");
        put("MELON_SLICE", "Rodaja de sandia"); put("PUMPKIN_PIE", "Pastel de calabaza"); put("CAKE", "Pastel");
        put("COOKIE", "Galleta"); put("HONEY_BOTTLE", "Botella de miel"); put("GOLDEN_APPLE", "Manzana dorada");
        put("ENCHANTED_GOLDEN_APPLE", "Manzana dorada encantada");

        // --- Herramientas, armas y armadura ---
        put("WOODEN_SWORD", "Espada de madera"); put("STONE_SWORD", "Espada de piedra"); put("IRON_SWORD", "Espada de hierro");
        put("GOLDEN_SWORD", "Espada de oro"); put("DIAMOND_SWORD", "Espada de diamante"); put("NETHERITE_SWORD", "Espada de netherita");
        put("WOODEN_PICKAXE", "Pico de madera"); put("STONE_PICKAXE", "Pico de piedra"); put("IRON_PICKAXE", "Pico de hierro");
        put("GOLDEN_PICKAXE", "Pico de oro"); put("DIAMOND_PICKAXE", "Pico de diamante"); put("NETHERITE_PICKAXE", "Pico de netherita");
        put("WOODEN_AXE", "Hacha de madera"); put("STONE_AXE", "Hacha de piedra"); put("IRON_AXE", "Hacha de hierro");
        put("GOLDEN_AXE", "Hacha de oro"); put("DIAMOND_AXE", "Hacha de diamante"); put("NETHERITE_AXE", "Hacha de netherita");
        put("WOODEN_SHOVEL", "Pala de madera"); put("IRON_SHOVEL", "Pala de hierro"); put("DIAMOND_SHOVEL", "Pala de diamante");
        put("BOW", "Arco"); put("CROSSBOW", "Ballesta"); put("ARROW", "Flecha"); put("TRIDENT", "Triente"); put("SHIELD", "Escudo");
        put("LEATHER_HELMET", "Casco de cuero"); put("IRON_HELMET", "Casco de hierro"); put("DIAMOND_HELMET", "Casco de diamante");
        put("IRON_CHESTPLATE", "Peto de hierro"); put("DIAMOND_CHESTPLATE", "Peto de diamante");
        put("IRON_BOOTS", "Botas de hierro"); put("DIAMOND_BOOTS", "Botas de diamante");
        put("FISHING_ROD", "Cana de pescar"); put("FLINT_AND_STEEL", "Mechero"); put("SHEARS", "Tijeras");

        // --- Utilidad / misc ---
        put("STICK", "Palo"); put("STRING", "Hilo"); put("FEATHER", "Pluma"); put("LEATHER", "Cuero"); put("BONE", "Hueso");
        put("GUNPOWDER", "Polvora"); put("SLIME_BALL", "Bola de slime"); put("ENDER_PEARL", "Perla de Ender"); put("ENDER_EYE", "Ojo de Ender");
        put("BLAZE_ROD", "Vara de blaze"); put("BLAZE_POWDER", "Polvo de blaze"); put("GHAST_TEAR", "Lagrima de ghast");
        put("MAGMA_CREAM", "Crema de magma"); put("SPIDER_EYE", "Ojo de arana"); put("FERMENTED_SPIDER_EYE", "Ojo de arana fermentado");
        put("PHANTOM_MEMBRANE", "Membrana de fantasma"); put("NAUTILUS_SHELL", "Caracol nautilo"); put("HEART_OF_THE_SEA", "Corazon del mar");
        put("TOTEM_OF_UNDYING", "Toten de la inmortalidad"); put("ELYTRA", "Elitros"); put("EXPERIENCE_BOTTLE", "Botella de experiencia");
        put("BOOK", "Libro"); put("ENCHANTED_BOOK", "Libro encantado"); put("PAPER", "Papel"); put("MAP", "Mapa"); put("COMPASS", "Brujula"); put("CLOCK", "Reloj");
        put("BUCKET", "Balde"); put("WATER_BUCKET", "Balde de agua"); put("LAVA_BUCKET", "Balde de lava"); put("MILK_BUCKET", "Balde de leche");
        put("SADDLE", "Silla de montar"); put("LEAD", "Correa"); put("NAME_TAG", "Etiqueta");
        put("TNT", "TNT"); put("CHEST", "Cofre"); put("ENDER_CHEST", "Cofre de Ender"); put("ANVIL", "Yunque"); put("CRAFTING_TABLE", "Mesa de crafteo");
        put("FURNACE", "Horno"); put("BLAST_FURNACE", "Alto horno"); put("SMOKER", "Ahumador"); put("ENCHANTING_TABLE", "Mesa de encantamientos");
        put("BREWING_STAND", "Soporte de pociones"); put("CAULDRON", "Caldero"); put("BEACON", "Faro"); put("CONDUIT", "Conduit");
        put("SPYGLASS", "Catalejo"); put("RECOVERY_COMPASS", "Brujula de recuperacion");

        // --- Mobs ---
        put("ZOMBIE", "Zombi"); put("SKELETON", "Esqueleto"); put("CREEPER", "Creeper"); put("SPIDER", "Arana"); put("CAVE_SPIDER", "Arana de cueva");
        put("ENDERMAN", "Enderman"); put("WITCH", "Bruja"); put("SLIME", "Slime"); put("MAGMA_CUBE", "Cubo de magma");
        put("BLAZE", "Blaze"); put("GHAST", "Ghast"); put("PHANTOM", "Fantasma"); put("DROWNED", "Ahogado"); put("HUSK", "Husk");
        put("STRAY", "Fantasmal"); put("PILLAGER", "Saqueador"); put("VINDICATOR", "Vindicador"); put("EVOKER", "Evocador");
        put("RAVAGER", "Devastador"); put("VEX", "Vex"); put("SILVERFISH", "Lepisma"); put("ENDERMITE", "Endermite");
        put("GUARDIAN", "Guardian"); put("ELDER_GUARDIAN", "Guardian anciano"); put("SHULKER", "Shulker"); put("WITHER_SKELETON", "Esqueleto wither");
        put("PIGLIN", "Piglin"); put("PIGLIN_BRUTE", "Piglin brutal"); put("ZOMBIFIED_PIGLIN", "Piglin zombificado"); put("HOGLIN", "Hoglin"); put("ZOGLIN", "Zoglin");
        put("WITHER", "Wither"); put("ENDER_DRAGON", "Dragon del End"); put("WARDEN", "Warden");
        put("COW", "Vaca"); put("PIG", "Cerdo"); put("SHEEP", "Oveja"); put("CHICKEN", "Gallina"); put("RABBIT", "Conejo"); put("HORSE", "Caballo");
        put("VILLAGER", "Aldeano"); put("WANDERING_TRADER", "Comerciante errante"); put("WOLF", "Lobo"); put("CAT", "Gato"); put("FOX", "Zorro");
        put("BEE", "Abeja"); put("PANDA", "Panda"); put("TURTLE", "Tortuga"); put("DOLPHIN", "Delfin"); put("SQUID", "Calamar"); put("AXOLOTL", "Ajolote");
        put("GOAT", "Cabra"); put("FROG", "Rana"); put("ALLAY", "Allay"); put("CAMEL", "Camello"); put("SNIFFER", "Sniffer");
    }

    private static void put(String key, String value) {
        NAMES.put(key, value);
    }
}
