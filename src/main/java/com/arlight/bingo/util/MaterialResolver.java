package com.arlight.bingo.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

/**
 * Resuelve el Material de Bukkit correspondiente a un namespaced key en texto
 * ("minecraft:diamond" o "modid:custom_item"). En un servidor hibrido (Arclight)
 * los items de mods pueden estar registrados de formas distintas segun la version,
 * asi que probamos varias estrategias antes de rendirnos.
 */
public class MaterialResolver {

    private MaterialResolver() {
    }

    /** Devuelve null si no se pudo resolver ningun Material real (no solo un generico de relleno). */
    public static Material resolve(String target) {
        if (target == null || target.isEmpty()) return null;

        String[] parts = target.contains(":") ? target.split(":", 2) : new String[]{"minecraft", target};
        String namespace = parts[0];
        String key = parts[1];

        // Estrategia 1: Registry.MATERIAL con el namespaced key exacto.
        try {
            NamespacedKey nsKey = new NamespacedKey(namespace, key);
            Material mat = Registry.MATERIAL.get(nsKey);
            if (mat != null && !mat.isAir()) return mat;
        } catch (Exception ignored) {
            // namespace/key con formato invalido para NamespacedKey
        }

        // Estrategia 2: Material.matchMaterial, mas tolerante (revisa nombres legacy tambien).
        try {
            Material mat = Material.matchMaterial(namespace + ":" + key);
            if (mat != null && !mat.isAir()) return mat;
            mat = Material.matchMaterial(key);
            if (mat != null && !mat.isAir()) return mat;
        } catch (Exception ignored) {
        }

        // Estrategia 3: Material.getMaterial(NOMBRE_EN_MAYUSCULAS), por si el mod
        // registro el item usando el mismo nombre que su constante de enum.
        try {
            Material mat = Material.getMaterial(key.toUpperCase());
            if (mat != null && !mat.isAir()) return mat;
        } catch (Exception ignored) {
        }

        return null;
    }
}
