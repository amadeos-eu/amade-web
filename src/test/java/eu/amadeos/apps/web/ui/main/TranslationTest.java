package eu.amadeos.apps.web.ui.main;

import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.i18n.TranslationCheck;
import eu.amadeos.apps.web.bootstrap.WebModule;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationTest {
    private static final Map<String, List<String>> MENUS = Map.of(
            "", List.of("menu.file", "menu.view", "menu.go", "menu.options", "menu.help"),
            "menu.file", List.of("menu.file.open", "menu.file.open-location", "menu.file.exit"),
            "menu.view", List.of("menu.view.reload", "menu.view.zoom-in", "menu.view.zoom-out",
                    "menu.view.zoom-reset"),
            "menu.go", List.of("menu.go.back", "menu.go.forward", "menu.go.home"));

    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(WebModule.class, WebModule.MESSAGES));
    }

    @Test
    void mnemonicsAreUniqueInEveryMenu() {
        for (Language language : Language.values()) {
            Messages msg = Messages.load(WebModule.class, WebModule.MESSAGES, language.locale());
            MENUS.forEach((menu, keys) -> {
                Map<Character, String> used = new HashMap<>();
                for (String key : keys) {
                    Mnemonics.Text text = Mnemonics.parse(msg.get(key));
                    assertTrue(text.index() >= 0, language + " " + key + " has no mnemonic");
                    char letter = Character.toLowerCase(text.text().charAt(text.index()));
                    assertNull(used.put(letter, key), language + " " + key + " repeats the mnemonic " + letter);
                }
            });
        }
    }
}
