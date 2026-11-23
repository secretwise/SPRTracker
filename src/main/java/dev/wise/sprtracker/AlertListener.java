package dev.wise.sprtracker;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AlertListener implements Listener {

    private final SPRTracker plugin;
    private final TrackerConfig config;
    private final AlertService alerts;
    private final CombatTracker combat;
    private final Map<UUID, PlacedCrystal> crystals = new ConcurrentHashMap<>();

    public AlertListener(SPRTracker plugin, TrackerConfig config, AlertService alerts) {
        this.plugin = plugin;
        this.config = config;
        this.alerts = alerts;
        this.combat = new CombatTracker(config);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Entity damager = event.getDamager();
        Player attacker = null;
        String weapon = "?";
        String enchants = "-";
        boolean fireAspect = false;
        boolean windCharge = false;

        if (damager instanceof Player player) {
            attacker = player;
            ItemStack hand = player.getInventory().getItemInMainHand();
            weapon = weaponKey(hand);
            enchants = enchants(hand);
            fireAspect = hasEnchant(hand, "fire_aspect");
        } else if (damager instanceof AbstractWindCharge charge && charge.getShooter() instanceof Player player) {
            attacker = player;
            weapon = "wind_charge";
            windCharge = true;
        } else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            attacker = player;
            weapon = projectile.getType().getKey().getKey();
            ItemStack hand = player.getInventory().getItemInMainHand();
            enchants = enchants(hand);
            if (hand.getType() == Material.BOW && hasEnchant(hand, "flame")) {
                fireAspect = true;
            }
        }

        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        combat.record(
                victim.getUniqueId(),
                attacker.getUniqueId(),
                attacker.getName(),
                weapon,
                enchants,
                fireAspect,
                windCharge,
                event.getFinalDamage(),
                damageType(event),
                victim.getLocation()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.isCancelled() || event.getHand() == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        int popped = alerts.incrementPops(uuid);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Location location = player.getLocation();
            Map<String, String> placeholders = base(player, location);
            String cause = damageType(player.getLastDamageCause());
            String damager = damager(player);
            String weapon = damagerWeapon(player);
            CombatTracker.Hit hit = combat.peek(uuid);
            if (hit != null && config.attributeEnvironmental() && environmental(player.getLastDamageCause())) {
                damager = hit.attackerName();
                weapon = hit.weapon();
            }
            placeholders.put("totems", String.valueOf(countTotems(player)));
            placeholders.put("pops", String.valueOf(popped));
            placeholders.put("damager", damager);
            placeholders.put("weapon", weapon);
            placeholders.put("cause", config.cause(cause));
            placeholders.put("cause_key", cause);
            placeholders.put("health", formatHealth(player.getHealth()));
            alerts.dispatch(new Alert(AlertType.TOTEM, uuid, placeholders, Instant.now()));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        crystals.put(crystal.getUniqueId(), new PlacedCrystal(player.getUniqueId(), player.getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        PlacedCrystal placed = crystals.remove(crystal.getUniqueId());
        if (placed == null && config.onlyPlayerCrystals()) {
            return;
        }
        Location location = event.getLocation();
        List<String> victims = new ArrayList<>();
        double radius = config.crystalRadius();
        double radiusSquared = radius * radius;
        if (location.getWorld() != null) {
            for (Player player : location.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(location) <= radiusSquared) {
                    victims.add(player.getName());
                }
            }
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", placed != null ? placed.name() : config.unknownPlayer());
        placeholders.put("world", location.getWorld() == null ? "?" : location.getWorld().getName());
        placeholders.put("x", String.valueOf(location.getBlockX()));
        placeholders.put("y", String.valueOf(location.getBlockY()));
        placeholders.put("z", String.valueOf(location.getBlockZ()));
        placeholders.put("victims", String.valueOf(victims.size()));
        placeholders.put("victim_list", victims.isEmpty() ? "-" : String.join(", ", victims));
        placeholders.put("cause", config.cause("explosion"));
        alerts.dispatch(new Alert(AlertType.CRYSTAL, placed != null ? placed.owner() : null, placeholders, Instant.now()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimId = victim.getUniqueId();
        Location location = victim.getLocation();
        String cause = damageType(event);
        CombatTracker.Credit credit = combat.consume(victimId);
        CombatTracker.Hit hit = credit == null ? null : credit.hit();

        Player direct = victim.getKiller();
        if (direct != null && direct.getUniqueId().equals(victimId)) {
            direct = null;
        }
        String causeEnum = event.getEntity().getLastDamageCause() == null
                ? "unknown"
                : event.getEntity().getLastDamageCause().getCause().name().toLowerCase(Locale.ROOT);
        boolean attributed = direct == null
                && hit != null
                && config.attributeEnvironmental()
                && (config.environmentalCauses().contains(cause) || config.environmentalCauses().contains(causeEnum));

        if (direct == null && !attributed) {
            alerts.resetPops(victimId);
            return;
        }

        String killerName;
        UUID actor;
        String weapon;
        String enchants;
        String killerHealth;
        String distance;

        if (direct != null) {
            killerName = direct.getName();
            actor = direct.getUniqueId();
            ItemStack hand = direct.getInventory().getItemInMainHand();
            weapon = weaponKey(hand);
            enchants = enchants(hand);
            killerHealth = formatHealth(direct.getHealth());
            distance = distance(direct.getLocation(), location);
        } else {
            killerName = hit.attackerName();
            actor = hit.attacker();
            weapon = hit.weapon();
            enchants = hit.enchants();
            Player online = Bukkit.getPlayer(hit.attacker());
            killerHealth = online == null ? "-" : formatHealth(online.getHealth());
            distance = online == null ? distance(hit.location(), location) : distance(online.getLocation(), location);
        }

        Map<String, String> placeholders = base(victim, location);
        placeholders.put("killer", killerName);
        placeholders.put("weapon", weapon);
        placeholders.put("enchants", enchants);
        placeholders.put("cause", config.cause(cause));
        placeholders.put("cause_key", cause);
        placeholders.put("health", formatHealth(victim.getHealth()));
        placeholders.put("killer_health", killerHealth);
        placeholders.put("distance", distance);
        placeholders.put("totems", String.valueOf(countTotems(victim)));
        placeholders.put("assists", credit == null ? "-" : assists(credit, killerName));
        placeholders.put("tag", direct != null ? "direct" : tag(hit));
        alerts.dispatch(new Alert(AlertType.KILL, actor, placeholders, Instant.now()));
        alerts.resetPops(victimId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        alerts.resetPops(uuid);
        combat.clear(uuid);
    }

    private Map<String, String> base(Player player, Location location) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("world", location.getWorld() == null ? "?" : location.getWorld().getName());
        placeholders.put("x", String.valueOf(location.getBlockX()));
        placeholders.put("y", String.valueOf(location.getBlockY()));
        placeholders.put("z", String.valueOf(location.getBlockZ()));
        return placeholders;
    }

    private String weaponKey(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "кулак";
        }
        return item.getType().getKey().getKey();
    }

    private String enchants(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getEnchantments().isEmpty()) {
            return "-";
        }
        List<String> list = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            list.add(entry.getKey().getKey().getKey() + " " + entry.getValue());
        }
        return String.join(", ", list);
    }

    private boolean hasEnchant(ItemStack item, String key) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        for (Enchantment enchantment : item.getEnchantments().keySet()) {
            if (enchantment.getKey().getKey().equals(key)) {
                return true;
            }
        }
        return false;
    }

    private String damager(Player player) {
        EntityDamageEvent cause = player.getLastDamageCause();
        if (cause instanceof EntityDamageByEntityEvent byEntity) {
            Entity entity = byEntity.getDamager();
            if (entity instanceof Player other) {
                return other.getName();
            }
            if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
                return shooter.getName();
            }
            return entity.getType().getKey().getKey();
        }
        return cause == null ? "неизвестно" : cause.getCause().name().toLowerCase(Locale.ROOT);
    }

    private String damagerWeapon(Player player) {
        EntityDamageEvent cause = player.getLastDamageCause();
        if (cause instanceof EntityDamageByEntityEvent byEntity) {
            Entity entity = byEntity.getDamager();
            if (entity instanceof Player other) {
                return weaponKey(other.getInventory().getItemInMainHand());
            }
            if (entity instanceof AbstractWindCharge) {
                return "wind_charge";
            }
            if (entity instanceof Projectile projectile) {
                return projectile.getType().getKey().getKey();
            }
            return entity.getType().getKey().getKey();
        }
        return cause == null ? "?" : cause.getCause().name().toLowerCase(Locale.ROOT);
    }

    private boolean environmental(EntityDamageEvent event) {
        if (event == null || event instanceof EntityDamageByEntityEvent) {
            return false;
        }
        String type = damageType(event);
        String cause = event.getCause().name().toLowerCase(Locale.ROOT);
        return config.environmentalCauses().contains(type) || config.environmentalCauses().contains(cause);
    }

    private String damageType(EntityDamageEvent event) {
        if (event == null) {
            return "unknown";
        }
        try {
            return event.getDamageSource().getDamageType().getKey().getKey();
        } catch (RuntimeException exception) {
            return event.getCause().name().toLowerCase(Locale.ROOT);
        }
    }

    private String damageType(EntityDeathEvent event) {
        try {
            return event.getDamageSource().getDamageType().getKey().getKey();
        } catch (RuntimeException exception) {
            return "unknown";
        }
    }

    private String tag(CombatTracker.Hit hit) {
        if (hit == null) {
            return "environment";
        }
        if (hit.fireAspect()) {
            return "fire_aspect";
        }
        if (hit.windCharge()) {
            return "wind_charge";
        }
        if ("mace".equals(hit.weapon())) {
            return "mace";
        }
        return "environment";
    }

    private String assists(CombatTracker.Credit credit, String killer) {
        List<String> names = new ArrayList<>();
        for (String name : credit.assists()) {
            if (!name.equalsIgnoreCase(killer) && !names.contains(name)) {
                names.add(name);
            }
        }
        return names.isEmpty() ? "-" : String.join(", ", names);
    }

    private String distance(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            return "-";
        }
        if (!first.getWorld().equals(second.getWorld())) {
            return "-";
        }
        return String.format(Locale.US, "%.1f", first.distance(second));
    }

    private String formatHealth(double health) {
        return String.format(Locale.US, "%.1f", Math.max(0.0, health));
    }

    private int countTotems(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private record PlacedCrystal(UUID owner, String name) {
    }
}
