package dev.wise.sprtracker;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Alert(AlertType type, UUID actor, Map<String, String> placeholders, Instant time) {

    public Alert {
        placeholders = Map.copyOf(placeholders);
    }

    public String cooldownKey() {
        if (actor != null) {
            return actor.toString();
        }
        return placeholders.getOrDefault("player", "-");
    }
}
