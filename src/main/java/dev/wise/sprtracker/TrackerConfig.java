package dev.wise.sprtracker;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TrackerConfig {

    private final SPRTracker plugin;
    private FileConfiguration raw;

    public TrackerConfig(SPRTracker plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        raw = plugin.getConfig();
    }

    public String string(String path, String def) {
        String value = raw.getString(path);
        return value == null ? def : value;
    }

    public boolean bool(String path, boolean def) {
        return raw.getBoolean(path, def);
    }

    public int integer(String path, int def) {
        return raw.getInt(path, def);
    }

    public long number(String path, long def) {
        return raw.getLong(path, def);
    }

    public double decimal(String path, double def) {
        return raw.getDouble(path, def);
    }

    public List<String> list(String path) {
        return raw.getStringList(path);
    }

    public Set<String> disabledWorlds() {
        return new HashSet<>(raw.getStringList("general.disabled-worlds"));
    }

    public boolean ignoreSelf() {
        return bool("general.ignore-self", false);
    }

    public boolean console() {
        return bool("general.console", true);
    }

    public int historySize() {
        return Math.max(1, integer("general.history-size", 50));
    }

    public String timeFormat() {
        return string("general.time-format", "HH:mm:ss");
    }

    public boolean chat() {
        return bool("display.chat", true);
    }

    public boolean copyCoords() {
        return bool("display.copy-coords", true);
    }

    public String coordsFormat() {
        return string("display.coords-format", "%world% %x% %y% %z%");
    }

    public String coordsCopyFormat() {
        return string("display.coords-copy-format", "%world% %x% %y% %z%");
    }

    public String coordsHover() {
        return string("display.coords-hover", "&7нажми, чтобы скопировать");
    }

    public int combatWindow() {
        return Math.max(1, integer("combat.window-seconds", 15));
    }

    public boolean attributeEnvironmental() {
        return bool("combat.attribute-environmental", true);
    }

    public Set<String> environmentalCauses() {
        List<String> list = raw.getStringList("combat.environmental-causes");
        if (list.isEmpty() && !raw.contains("combat.environmental-causes")) {
            return Set.of(
                    "fall", "in_fire", "on_fire", "fire_tick", "lava", "hot_floor", "campfire",
                    "explosion", "player_explosion", "wind_charge", "fly_into_wall", "drown",
                    "out_of_world", "cactus", "sweet_berry_bush", "freeze", "lightning_bolt",
                    "falling_block", "falling_anvil", "falling_stalactite"
            );
        }
        return new HashSet<>(list);
    }

    public boolean actionbar() {
        return bool("display.actionbar", false);
    }

    public boolean sound() {
        return bool("display.sound.enabled", true);
    }

    public String soundName() {
        return string("display.sound.name", "ui.button.click");
    }

    public float soundVolume() {
        return (float) decimal("display.sound.volume", 0.8);
    }

    public float soundPitch() {
        return (float) decimal("display.sound.pitch", 1.4);
    }

    public boolean titles() {
        return bool("display.title.enabled", false);
    }

    public String title() {
        return string("display.title.title", "&#F34242▶ &f%type% &#F34242◀");
    }

    public String subtitle() {
        return string("display.title.subtitle", "&f%player%");
    }

    public int titleFadeIn() {
        return integer("display.title.fade-in", 0);
    }

    public int titleStay() {
        return integer("display.title.stay", 20);
    }

    public int titleFadeOut() {
        return integer("display.title.fade-out", 10);
    }

    public boolean alertEnabled(AlertType type) {
        return bool("alerts." + type.key() + ".enabled", true);
    }

    public int cooldown(AlertType type) {
        return Math.max(0, integer("alerts." + type.key() + ".cooldown-seconds", 0));
    }

    public String alertMessage(AlertType type) {
        return string("alerts." + type.key() + ".message", "&c" + type.key());
    }

    public boolean alertWebhook(AlertType type) {
        return bool("alerts." + type.key() + ".webhook", true);
    }

    public boolean onlyPlayerCrystals() {
        return bool("alerts.crystal.only-player-placed", true);
    }

    public double crystalRadius() {
        return decimal("alerts.crystal.radius", 6.0);
    }

    public boolean webhookEnabled() {
        return bool("webhook.enabled", false);
    }

    public String webhookUrl() {
        return string("webhook.url", "").trim();
    }

    public String webhookUsername() {
        return string("webhook.username", "spr tracker");
    }

    public String webhookAvatar() {
        return string("webhook.avatar-url", "");
    }

    public String serverName() {
        return string("webhook.server-name", "spr");
    }

    public String webhookFooter() {
        return string("webhook.footer", "sprtracker · %server%");
    }

    public long webhookInterval() {
        return Math.max(500L, number("webhook.interval-ms", 1200L));
    }

    public int webhookTimeout() {
        return Math.max(1000, integer("webhook.timeout-ms", 5000));
    }

    public String embedTitle(AlertType type) {
        return string("webhook.embed." + type.key() + ".title", type.key());
    }

    public String embedDescription(AlertType type) {
        return string("webhook.embed." + type.key() + ".description", type.key());
    }

    public int embedColor(AlertType type) {
        return parseColor(string("webhook.embed." + type.key() + ".color", "#F34242"));
    }

    public String message(String key) {
        return string("messages." + key, key);
    }

    public String cause(String key) {
        if (key == null || key.isEmpty()) {
            return "?";
        }
        return string("messages.causes." + key, key);
    }

    public List<String> messages(String key) {
        return raw.getStringList("messages." + key);
    }

    public String unknownPlayer() {
        return string("messages.unknown-player", "кто-то");
    }

    private int parseColor(String value) {
        try {
            String text = value.trim();
            if (text.startsWith("#")) {
                return Integer.parseInt(text.substring(1), 16);
            }
            if (text.startsWith("0x")) {
                return Integer.parseInt(text.substring(2), 16);
            }
            return Integer.parseInt(text);
        } catch (RuntimeException ignored) {
            return 0xF34242;
        }
    }
}
