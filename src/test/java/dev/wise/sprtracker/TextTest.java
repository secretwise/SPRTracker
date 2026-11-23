package dev.wise.sprtracker;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextTest {

    @Test
    void parsesHexAndLegacyCodes() {
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(Text.component("&#F34242▶ &fтотем &7у &fwise"));
        assertEquals("▶ тотем у wise", plain);
    }

    @Test
    void stripsColorsForWebhook() {
        assertEquals("▶ тотем у wise", Text.strip("&#F34242▶ &fтотем &7у &fwise"));
    }
}
