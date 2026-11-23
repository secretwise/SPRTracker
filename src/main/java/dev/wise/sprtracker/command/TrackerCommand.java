package dev.wise.sprtracker.command;

import dev.wise.sprtracker.Alert;
import dev.wise.sprtracker.AlertService;
import dev.wise.sprtracker.AlertType;
import dev.wise.sprtracker.PlayerPrefs;
import dev.wise.sprtracker.PrefsStore;
import dev.wise.sprtracker.SPRTracker;
import dev.wise.sprtracker.Text;
import dev.wise.sprtracker.TrackerConfig;
import dev.wise.sprtracker.WebhookClient;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TrackerCommand implements TabExecutor {

    private final SPRTracker plugin;
    private final TrackerConfig config;
    private final PrefsStore prefs;
    private final AlertService alerts;
    private final WebhookClient webhook;

    public TrackerCommand(SPRTracker plugin, TrackerConfig config, PrefsStore prefs, AlertService alerts, WebhookClient webhook) {
        this.plugin = plugin;
        this.config = config;
        this.prefs = prefs;
        this.alerts = alerts;
        this.webhook = webhook;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                status(player);
            } else {
                help(sender);
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                if (sender instanceof Player player) {
                    status(player);
                } else {
                    help(sender);
                }
            }
            case "alerts" -> alerts(sender, args);
            case "sound" -> sound(sender);
            case "actionbar" -> actionbar(sender);
            case "history" -> history(sender, args);
            case "reload" -> reload(sender);
            case "test" -> test(sender, args);
            case "webhook" -> webhook(sender, args);
            case "help", "?" -> help(sender);
            default -> reply(sender, "unknown-command", Map.of());
        }
        return true;
    }

    private void status(Player player) {
        PlayerPrefs playerPrefs = prefs.get(player.getUniqueId());
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("alerts", onOff(playerPrefs.alerts()));
        placeholders.put("totem", onOff(playerPrefs.enabled(AlertType.TOTEM)));
        placeholders.put("crystal", onOff(playerPrefs.enabled(AlertType.CRYSTAL)));
        placeholders.put("kill", onOff(playerPrefs.enabled(AlertType.KILL)));
        placeholders.put("sound", onOff(playerPrefs.sound()));
        placeholders.put("actionbar", onOff(playerPrefs.actionbar()));
        placeholders.put("webhook", onOff(webhook.enabled()));
        placeholders.put("webhook_url", mask(config.webhookUrl()));
        placeholders.put("history", String.valueOf(alerts.historyCount()));
        List<String> lines = config.messages("status");
        if (lines.isEmpty()) {
            reply(player, "unknown-command", Map.of());
            return;
        }
        for (String line : lines) {
            player.sendMessage(Text.component(Text.fill(line, placeholders)));
        }
    }

    private void alerts(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tracker.alerts")) {
            noPerm(sender);
            return;
        }
        if (!(sender instanceof Player player)) {
            reply(sender, "player-only", Map.of());
            return;
        }
        PlayerPrefs playerPrefs = prefs.get(player.getUniqueId());
        if (args.length < 2) {
            playerPrefs.alerts(!playerPrefs.alerts());
            prefs.save(playerPrefs);
            reply(sender, playerPrefs.alerts() ? "alerts-on" : "alerts-off", Map.of());
            return;
        }
        AlertType type = AlertType.byKey(args[1]);
        if (type == null) {
            reply(sender, "unknown-type", Map.of());
            return;
        }
        playerPrefs.enabled(type, !playerPrefs.enabled(type));
        prefs.save(playerPrefs);
        reply(sender, playerPrefs.enabled(type) ? "type-on" : "type-off", Map.of("type", type.display()));
    }

    private void sound(CommandSender sender) {
        if (!sender.hasPermission("tracker.alerts")) {
            noPerm(sender);
            return;
        }
        if (!(sender instanceof Player player)) {
            reply(sender, "player-only", Map.of());
            return;
        }
        PlayerPrefs playerPrefs = prefs.get(player.getUniqueId());
        playerPrefs.sound(!playerPrefs.sound());
        prefs.save(playerPrefs);
        reply(sender, playerPrefs.sound() ? "sound-on" : "sound-off", Map.of());
    }

    private void actionbar(CommandSender sender) {
        if (!sender.hasPermission("tracker.alerts")) {
            noPerm(sender);
            return;
        }
        if (!(sender instanceof Player player)) {
            reply(sender, "player-only", Map.of());
            return;
        }
        PlayerPrefs playerPrefs = prefs.get(player.getUniqueId());
        playerPrefs.actionbar(!playerPrefs.actionbar());
        prefs.save(playerPrefs);
        reply(sender, playerPrefs.actionbar() ? "actionbar-on" : "actionbar-off", Map.of());
    }

    private void history(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tracker.history")) {
            noPerm(sender);
            return;
        }
        int count = 10;
        if (args.length > 1) {
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        count = Math.max(1, Math.min(count, config.historySize()));
        List<Component> lines = alerts.history(count);
        if (lines.isEmpty()) {
            reply(sender, "history-empty", Map.of());
            return;
        }
        reply(sender, "history-header", Map.of("n", String.valueOf(lines.size())));
        for (Component line : lines) {
            sender.sendMessage(line);
        }
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("tracker.admin")) {
            noPerm(sender);
            return;
        }
        long start = System.currentTimeMillis();
        plugin.reloadTracker();
        long took = System.currentTimeMillis() - start;
        reply(sender, "reloaded", Map.of("ms", String.valueOf(took)));
    }

    private void test(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tracker.admin")) {
            noPerm(sender);
            return;
        }
        AlertType type = AlertType.TOTEM;
        if (args.length > 1) {
            AlertType parsed = AlertType.byKey(args[1]);
            if (parsed == null) {
                reply(sender, "unknown-type", Map.of());
                return;
            }
            type = parsed;
        }
        Player player = sender instanceof Player online ? online : null;
        alerts.dispatch(new Alert(type, null, fake(type, player), Instant.now()), true);
        reply(sender, "test-sent", Map.of("type", type.display()));
    }

    private void webhook(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tracker.admin")) {
            noPerm(sender);
            return;
        }
        if (args.length < 2) {
            reply(sender, "webhook-status", Map.of(
                    "state", onOff(webhook.enabled()),
                    "url", mask(config.webhookUrl())
            ));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                if (!webhook.configured()) {
                    reply(sender, "webhook-no-url", Map.of());
                    return;
                }
                webhook.runtimeEnabled(true);
                reply(sender, "webhook-on", Map.of());
            }
            case "off" -> {
                webhook.runtimeEnabled(false);
                reply(sender, "webhook-off", Map.of());
            }
            case "test" -> {
                String[] testArgs = new String[]{"test", args.length > 2 ? args[2] : "totem"};
                test(sender, testArgs);
            }
            default -> reply(sender, "unknown-command", Map.of());
        }
    }

    private void help(CommandSender sender) {
        List<String> lines = config.messages("help");
        if (lines.isEmpty()) {
            reply(sender, "unknown-command", Map.of());
            return;
        }
        for (String line : lines) {
            sender.sendMessage(Text.component(line));
        }
    }

    private Map<String, String> fake(AlertType type, Player player) {
        String name = player != null ? player.getName() : "console";
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", name);
        placeholders.put("killer", name);
        placeholders.put("totems", "2");
        placeholders.put("pops", "3");
        placeholders.put("damager", "test");
        placeholders.put("world", player != null ? player.getWorld().getName() : "world");
        placeholders.put("x", player != null ? String.valueOf(player.getLocation().getBlockX()) : "0");
        placeholders.put("y", player != null ? String.valueOf(player.getLocation().getBlockY()) : "64");
        placeholders.put("z", player != null ? String.valueOf(player.getLocation().getBlockZ()) : "0");
        placeholders.put("victims", "2");
        placeholders.put("victim_list", "Steve, Alex");
        placeholders.put("weapon", "netherite_sword");
        placeholders.put("enchants", "fire_aspect 2, sharpness 5");
        placeholders.put("distance", "3.4");
        placeholders.put("cause", config.cause("player_attack"));
        placeholders.put("cause_key", "player_attack");
        placeholders.put("health", "0.0");
        placeholders.put("killer_health", "14.5");
        placeholders.put("assists", "Alex");
        placeholders.put("tag", "direct");
        if (type == AlertType.KILL) {
            placeholders.put("player", "test");
            placeholders.put("killer", name);
        }
        return placeholders;
    }

    private void reply(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(Text.component(config.message("prefix") + Text.fill(config.message(key), placeholders)));
    }

    private void noPerm(CommandSender sender) {
        reply(sender, "no-perm", Map.of());
    }

    private static String onOff(boolean value) {
        return value ? "&aвкл" : "&cвыкл";
    }

    private static String mask(String url) {
        if (url == null || url.isBlank()) {
            return "не задан";
        }
        String text = url.trim();
        int slash = text.lastIndexOf('/');
        String tail = slash >= 0 && slash + 1 < text.length() ? text.substring(slash + 1) : text;
        String last = tail.length() > 4 ? tail.substring(tail.length() - 4) : tail;
        return "…" + last;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("alerts", "sound", "actionbar", "history", "status", "help"));
            if (sender.hasPermission("tracker.admin")) {
                subs.add("test");
                subs.add("webhook");
                subs.add("reload");
            }
            return filter(subs, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("alerts") || sub.equals("test")) {
                return filter(List.of("totem", "crystal", "kill"), args[1]);
            }
            if (sub.equals("webhook")) {
                return filter(List.of("on", "off", "test"), args[1]);
            }
            if (sub.equals("history")) {
                return filter(List.of("5", "10", "20", "50"), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("webhook") && args[1].equalsIgnoreCase("test")) {
            return filter(List.of("totem", "crystal", "kill"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String text = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(text)) {
                result.add(value);
            }
        }
        return result;
    }
}
