package dev.wise.sprtracker;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerPrefs {

    private final UUID uuid;
    private final EnumMap<AlertType, Boolean> types = new EnumMap<>(AlertType.class);
    private boolean alerts = true;
    private boolean sound = true;
    private boolean actionbar = true;

    public PlayerPrefs(UUID uuid) {
        this.uuid = uuid;
        for (AlertType type : AlertType.values()) {
            types.put(type, true);
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public boolean alerts() {
        return alerts;
    }

    public void alerts(boolean value) {
        this.alerts = value;
    }

    public boolean sound() {
        return sound;
    }

    public void sound(boolean value) {
        this.sound = value;
    }

    public boolean actionbar() {
        return actionbar;
    }

    public void actionbar(boolean value) {
        this.actionbar = value;
    }

    public boolean enabled(AlertType type) {
        return types.getOrDefault(type, true);
    }

    public void enabled(AlertType type, boolean value) {
        types.put(type, value);
    }

    public Map<AlertType, Boolean> types() {
        return types;
    }
}
