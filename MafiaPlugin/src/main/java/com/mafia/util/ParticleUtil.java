package com.mafia.util;

import org.bukkit.Location;
import org.bukkit.Particle;

public class ParticleUtil {

    public static void spawnFootprints(Location center) {
        if (center == null || center.getWorld() == null) return;
        // Spawn footprint particles in a small area around the entrance
        for (int i = 0; i < 20; i++) {
            double offsetX = (Math.random() - 0.5) * 2;
            double offsetZ = (Math.random() - 0.5) * 2;
            Location loc = center.clone().add(offsetX, 0.1, offsetZ);
            center.getWorld().spawnParticle(Particle.ASH, loc, 5, 0.1, 0, 0.1, 0);
        }
        // Also spawn some dust particles to make it more visible
        center.getWorld().spawnParticle(Particle.REDSTONE, center, 30, 0.5, 0.5, 0.5, 0,
                new Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
    }
}
