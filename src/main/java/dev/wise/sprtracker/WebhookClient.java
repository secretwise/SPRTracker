package dev.wise.sprtracker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WebhookClient {

    private static final Pattern RETRY = Pattern.compile("\"retry_after\"\\s*:\\s*([0-9.]+)");
    private static final int MAX_QUEUE = 250;

    private final SPRTracker plugin;
    private final TrackerConfig config;
    private final HttpClient http;
    private final Queue<String> queue = new ConcurrentLinkedQueue<>();
    private ScheduledExecutorService executor;
    private volatile Boolean runtimeEnabled;
    private volatile boolean ready;

    public WebhookClient(SPRTracker plugin, TrackerConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.webhookTimeout()))
                .build();
    }

    public void start() {
        stop();
        runtimeEnabled = null;
        ready = validUrl();
        if (!ready) {
            if (config.webhookEnabled()) {
                if (config.webhookUrl().isBlank()) {
                    plugin.getLogger().warning("webhook enabled but url is empty.");
                } else {
                    plugin.getLogger().warning("webhook url looks off, disabled.");
                }
            }
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "SPRTracker-webhook");
            thread.setDaemon(true);
            return thread;
        });
        long interval = config.webhookInterval();
        executor.scheduleWithFixedDelay(this::tick, 0, interval, TimeUnit.MILLISECONDS);
        if (enabled()) {
            plugin.getLogger().info("webhook on (" + interval + "ms).");
        }
    }

    public void reload() {
        start();
    }

    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        queue.clear();
        ready = false;
    }

    public boolean enabled() {
        if (!ready) {
            return false;
        }
        Boolean override = runtimeEnabled;
        return override != null ? override : config.webhookEnabled();
    }

    public boolean configured() {
        return ready;
    }

    public void runtimeEnabled(boolean value) {
        this.runtimeEnabled = value;
    }

    public void send(Alert alert, Map<String, String> placeholders) {
        if (!enabled()) {
            return;
        }
        String payload = build(alert, placeholders);
        if (queue.size() >= MAX_QUEUE) {
            queue.poll();
        }
        queue.add(payload);
    }

    private boolean validUrl() {
        String url = config.webhookUrl();
        if (url.isEmpty()) {
            return false;
        }
        if (!url.startsWith("https://discord.com/api/webhooks/")
                && !url.startsWith("https://discordapp.com/api/webhooks/")
                && !url.startsWith("https://canary.discord.com/api/webhooks/")) {
            return false;
        }
        try {
            URI.create(url);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void tick() {
        if (!enabled()) {
            return;
        }
        String payload = queue.poll();
        if (payload != null) {
            post(payload);
        }
    }

    private void post(String payload) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(config.webhookUrl()))
                    .timeout(Duration.ofMillis(config.webhookTimeout()))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "SPRTracker/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("webhook build: " + exception.getMessage());
            return;
        }
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> handle(response.statusCode(), response.body(), payload))
                .exceptionally(error -> {
                    plugin.getLogger().warning("webhook: " + root(error));
                    return null;
                });
    }

    private void handle(int code, String body, String payload) {
        if (code >= 200 && code < 300) {
            return;
        }
        if (code == 429) {
            long retry = 1500L;
            Matcher matcher = RETRY.matcher(body == null ? "" : body);
            if (matcher.find()) {
                try {
                    retry = (long) (Double.parseDouble(matcher.group(1)) * 1000.0);
                } catch (NumberFormatException ignored) {
                }
            }
            retry = Math.max(1000L, Math.min(15000L, retry));
            final long delay = retry;
            ScheduledExecutorService exec = executor;
            if (exec != null && !exec.isShutdown()) {
                try {
                    exec.schedule(() -> queue.add(payload), delay, TimeUnit.MILLISECONDS);
                } catch (RejectedExecutionException ignored) {
                }
            }
            plugin.getLogger().warning("webhook rate limit, retry in " + delay + "ms.");
            return;
        }
        plugin.getLogger().warning("webhook " + code + ": " + shortBody(body));
    }

    private String build(Alert alert, Map<String, String> placeholders) {
        String title = Text.strip(Text.fill(config.embedTitle(alert.type()), placeholders));
        String description = Text.strip(Text.fill(config.embedDescription(alert.type()), placeholders));
        String footer = Text.strip(Text.fill(config.webhookFooter(), placeholders));
        StringBuilder json = new StringBuilder(512);
        json.append("{\"username\":\"").append(escape(config.webhookUsername())).append('"');
        String avatar = config.webhookAvatar();
        if (!avatar.isBlank()) {
            json.append(",\"avatar_url\":\"").append(escape(avatar)).append('"');
        }
        json.append(",\"allowed_mentions\":{\"parse\":[]},\"embeds\":[{");
        json.append("\"title\":\"").append(escape(title)).append("\",");
        json.append("\"description\":\"").append(escape(description)).append("\",");
        json.append("\"color\":").append(config.embedColor(alert.type())).append(',');
        json.append("\"footer\":{\"text\":\"").append(escape(footer)).append("\"},");
        json.append("\"timestamp\":\"").append(Instant.now().truncatedTo(ChronoUnit.SECONDS)).append("\"}]}");
        return json.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static String shortBody(String body) {
        if (body == null) {
            return "";
        }
        String text = body.replace('\n', ' ').trim();
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    private static String root(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
