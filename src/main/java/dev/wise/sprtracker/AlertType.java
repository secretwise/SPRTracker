package dev.wise.sprtracker;

import java.util.Locale;

public enum AlertType {

    TOTEM("totem", "тотем"),
    CRYSTAL("crystal", "кристалл"),
    KILL("kill", "килл");

    private final String key;
    private final String display;

    AlertType(String key, String display) {
        this.key = key;
        this.display = display;
    }

    public String key() {
        return key;
    }

    public String display() {
        return display;
    }

    public static AlertType byKey(String input) {
        if (input == null) {
            return null;
        }
        String value = input.toLowerCase(Locale.ROOT);
        for (AlertType type : values()) {
            if (type.key.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
