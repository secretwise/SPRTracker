package dev.wise.sprtracker;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CombatTracker {

    private static final int MAX_ATTACKERS = 10;

    private final TrackerConfig config;
    private final Map<UUID, Map<UUID, Hit>> logs = new HashMap<>();

    public CombatTracker(TrackerConfig config) {
        this.config = config;
    }

    public void record(UUID victim, UUID attacker, String attackerName, String weapon, String enchants,
                       boolean fireAspect, boolean windCharge, double damage, String damageType, Location location) {
        long now = System.currentTimeMillis();
        Map<UUID, Hit> hits = logs.computeIfAbsent(victim, key -> new LinkedHashMap<>());
        prune(hits, now);

        Hit old = hits.get(attacker);
        double total = Math.max(0.0, damage) + (old == null ? 0.0 : old.damage());
        hits.put(attacker, new Hit(
                attacker,
                attackerName == null ? "?" : attackerName,
                weapon == null ? "?" : weapon,
                enchants == null ? "-" : enchants,
                fireAspect,
                windCharge,
                total,
                damageType == null ? "unknown" : damageType,
                now,
                location == null ? null : location.clone()
        ));

        while (hits.size() > MAX_ATTACKERS) {
            UUID oldest = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<UUID, Hit> entry : hits.entrySet()) {
                if (entry.getValue().lastTime() < oldestTime) {
                    oldestTime = entry.getValue().lastTime();
                    oldest = entry.getKey();
                }
            }
            if (oldest == null) {
                break;
            }
            hits.remove(oldest);
        }
    }

    public Hit peek(UUID victim) {
        Map<UUID, Hit> hits = logs.get(victim);
        if (hits == null) {
            return null;
        }
        prune(hits, System.currentTimeMillis());
        Hit best = null;
        for (Hit hit : hits.values()) {
            if (best == null || hit.lastTime() > best.lastTime()) {
                best = hit;
            }
        }
        return best;
    }

    public Credit consume(UUID victim) {
        Map<UUID, Hit> hits = logs.remove(victim);
        if (hits == null) {
            return null;
        }
        prune(hits, System.currentTimeMillis());
        if (hits.isEmpty()) {
            return null;
        }

        Hit best = null;
        for (Hit hit : hits.values()) {
            if (best == null || hit.lastTime() > best.lastTime()) {
                best = hit;
            }
        }

        List<String> assists = new ArrayList<>();
        for (Hit hit : hits.values()) {
            if (hit.attacker().equals(best.attacker()) || assists.contains(hit.attackerName())) {
                continue;
            }
            assists.add(hit.attackerName());
        }
        return new Credit(best, List.copyOf(assists));
    }

    public void clear(UUID victim) {
        logs.remove(victim);
    }

    private void prune(Map<UUID, Hit> hits, long now) {
        long window = config.combatWindow() * 1000L;
        hits.entrySet().removeIf(entry -> now - entry.getValue().lastTime() > window);
    }

    public record Hit(UUID attacker, String attackerName, String weapon, String enchants, boolean fireAspect,
                      boolean windCharge, double damage, String damageType, long lastTime, Location location) {
    }

    public record Credit(Hit hit, List<String> assists) {
    }
}
