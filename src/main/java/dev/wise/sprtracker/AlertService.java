package dev.wise.sprtracker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AlertService {

    private final SPRTracker plugin;
    private final TrackerConfig config;
    private final PrefsStore prefs;
    private final WebhookClient webhook;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Deque<Component> history = new ArrayDeque<>();
    private final Map<UUID, Integer> pops = new HashMap<>();
    private DateTimeFormatter timeFormat;

    public AlertService(SPRTracker plugin, TrackerConfig config, PrefsStore prefs, WebhookClient webhook) {
        this.plugin = plugin;
        this.config = config;
        this.prefs = prefs;
        this.webhook = webhook;
        this.timeFormat = formatter();
    }

    public void reload() {
        this.timeFormat = formatter();
        cooldowns.clear();
    }

    public void dispatch(Alert alert) {
        dispatch(alert, false);
    }

    public void dispatch(Alert alert, boolean force) {
        if (!config.alertEnabled(alert.type())) {
            return;
        }
        String world = alert.placeholders().getOrDefault("world", "");
        if (!world.isEmpty() && config.disabledWorlds().contains(world)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force) {
            int seconds = config.cooldown(alert.type());
            if (seconds > 0) {
                String key = alert.type().key() + ":" + alert.cooldownKey();
                Long last = cooldowns.get(key);
                if (last != null && now - last < seconds * 1000L) {
                    return;
                }
                cooldowns.put(key, now);
                if (cooldowns.size() > 3000) {
                    cooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 60_000L);
                }
            }
        }

        Map<String, String> placeholders = new HashMap<>(alert.placeholders());
        placeholders.put("type", alert.type().display());
        placeholders.put("server", config.serverName());
        placeholders.put("time", timeFormat.format(alert.time()));
        placeholders.put("coords", Text.fill(config.coordsFormat(), placeholders));
        placeholders.put("coords_copy", Text.fill(config.coordsCopyFormat(), placeholders));

        String template = config.alertMessage(alert.type());
        String plain = Text.strip(Text.fill(template.replace("%coords%", placeholders.get("coords")), placeholders));
        Component chat = render(template, placeholders);

        if (config.console()) {
            plugin.getLogger().info(plain);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("tracker.alerts")) {
                continue;
            }
            if (config.ignoreSelf() && alert.actor() != null && alert.actor().equals(player.getUniqueId())) {
                continue;
            }
            PlayerPrefs playerPrefs = prefs.get(player.getUniqueId());
            if (!playerPrefs.alerts() || !playerPrefs.enabled(alert.type())) {
                continue;
            }
            deliver(player, playerPrefs, chat, plain, placeholders);
        }

        if (config.alertWebhook(alert.type()) && webhook.enabled()) {
            webhook.send(alert, placeholders);
        }
        addHistory(chat, alert.time());
    }

    private Component render(String template, Map<String, String> placeholders) {
        String token = "%coords%";
        int index = template.indexOf(token);
        if (index < 0) {
            return Text.component(Text.fill(template, placeholders));
        }
        String before = Text.fill(template.substring(0, index), placeholders);
        String after = Text.fill(template.substring(index + token.length()), placeholders);
        String coords = placeholders.getOrDefault("coords", "");
        Component coordsComponent;
        if (config.copyCoords()) {
            coordsComponent = Text.clickable("&f" + coords, placeholders.getOrDefault("coords_copy", coords), config.coordsHover());
        } else {
            coordsComponent = Text.component("&f" + coords);
        }
        return Text.component(before).append(coordsComponent).append(Text.component(after));
    }

    private void deliver(Player player, PlayerPrefs playerPrefs, Component chat, String plain, Map<String, String> placeholders) {
        if (config.chat()) {
            player.sendMessage(chat);
        }
        if (config.actionbar() && playerPrefs.actionbar()) {
            player.sendActionBar(Text.component(plain));
        }
        if (config.titles()) {
            Title title = Title.title(
                    Text.component(Text.fill(config.title(), placeholders)),
                    Text.component(Text.fill(config.subtitle(), placeholders)),
                    Title.Times.times(ticks(config.titleFadeIn()), ticks(config.titleStay()), ticks(config.titleFadeOut()))
            );
            player.showTitle(title);
        }
        if (config.sound() && playerPrefs.sound()) {
            try {
                player.playSound(player.getLocation(), config.soundName(), config.soundVolume(), config.soundPitch());
            } catch (RuntimeException ignored) {
                // кривой звук в конфиге — не повод ронять алерт
            }
        }
    }

    private void addHistory(Component message, Instant time) {
        history.addLast(Text.component("&8[" + timeFormat.format(time) + "] &r").append(message));
        while (history.size() > config.historySize()) {
            history.removeFirst();
        }
    }

    public List<Component> history(int count) {
        List<Component> lines = new ArrayList<>();
        Iterator<Component> iterator = history.descendingIterator();
        while (iterator.hasNext() && lines.size() < count) {
            lines.add(iterator.next());
        }
        return lines;
    }

    public int historyCount() {
        return history.size();
    }

    public int incrementPops(UUID uuid) {
        return pops.merge(uuid, 1, Integer::sum);
    }

    public int pops(UUID uuid) {
        return pops.getOrDefault(uuid, 0);
    }

    public void resetPops(UUID uuid) {
        pops.remove(uuid);
    }

    private DateTimeFormatter formatter() {
        try {
            return DateTimeFormatter.ofPattern(config.timeFormat()).withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("time-format: " + exception.getMessage());
            return DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
        }
    }

    private static Duration ticks(int ticks) {
        return Duration.ofMillis(Math.max(0, ticks) * 50L);
    }
}
