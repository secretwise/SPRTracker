package dev.wise.sprtracker;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PrefsStore {

    private final SPRTracker plugin;
    private final File file;
    private final Map<UUID, PlayerPrefs> cache = new HashMap<>();
    private YamlConfiguration data;

    public PrefsStore(SPRTracker plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        loadFile();
    }

    private void loadFile() {
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        cache.clear();
        loadFile();
    }

    public PlayerPrefs get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::read);
    }

    private PlayerPrefs read(UUID uuid) {
        PlayerPrefs prefs = new PlayerPrefs(uuid);
        ConfigurationSection section = data.getConfigurationSection("players." + uuid);
        if (section == null) {
            return prefs;
        }
        prefs.alerts(section.getBoolean("alerts", true));
        prefs.sound(section.getBoolean("sound", true));
        prefs.actionbar(section.getBoolean("actionbar", true));
        ConfigurationSection types = section.getConfigurationSection("types");
        if (types != null) {
            for (AlertType type : AlertType.values()) {
                prefs.enabled(type, types.getBoolean(type.key(), true));
            }
        }
        return prefs;
    }

    public void save(PlayerPrefs prefs) {
        write(prefs);
        saveFile();
    }

    public void saveAll() {
        if (cache.isEmpty()) {
            return;
        }
        for (PlayerPrefs prefs : cache.values()) {
            write(prefs);
        }
        saveFile();
    }

    private void write(PlayerPrefs prefs) {
        String base = "players." + prefs.uuid() + ".";
        data.set(base + "alerts", prefs.alerts());
        data.set(base + "sound", prefs.sound());
        data.set(base + "actionbar", prefs.actionbar());
        for (AlertType type : AlertType.values()) {
            data.set(base + "types." + type.key(), prefs.enabled(type));
        }
    }

    private void saveFile() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("players.yml: " + exception.getMessage());
        }
    }
}
