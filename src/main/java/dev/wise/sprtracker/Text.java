package dev.wise.sprtracker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;
import java.util.regex.Pattern;

public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final Pattern HEX = Pattern.compile("(?i)&#[0-9a-f]{6}");
    private static final Pattern CODE = Pattern.compile("(?i)&[0-9a-fk-or]");

    private Text() {
    }

    public static Component component(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    public static Component clickable(String text, String copy, String hover) {
        Component component = component(text);
        if (copy == null || copy.isBlank()) {
            return component;
        }
        return component
                .clickEvent(ClickEvent.copyToClipboard(copy))
                .hoverEvent(HoverEvent.showText(component(hover)));
    }

    public static String fill(String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        String out = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue();
            out = out.replace("%" + entry.getKey() + "%", value == null ? "" : value);
        }
        return out;
    }

    public static String strip(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return CODE.matcher(HEX.matcher(text).replaceAll("")).replaceAll("");
    }
}
