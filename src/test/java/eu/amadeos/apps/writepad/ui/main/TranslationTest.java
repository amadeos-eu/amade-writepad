package eu.amadeos.apps.writepad.ui.main;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.TranslationCheck;
import eu.amadeos.apps.writepad.bootstrap.WritepadModule;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationTest {
    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(WritepadModule.class, WritepadModule.MESSAGES));
    }

    @Test
    void textsAreFormattedInTheLanguage() {
        Messages german = Messages.load(WritepadModule.class, WritepadModule.MESSAGES, Locale.GERMANY);
        assertEquals("Seite 2 von 12", german.format("status.page", 2, 12));
        assertEquals("Querformat", german.get("orientation.landscape"));
        Messages french = Messages.load(WritepadModule.class, WritepadModule.MESSAGES, Locale.FRANCE);
        assertEquals("À propos d'amade-writepad", french.get("menu.help.about"));
        assertEquals("L'impression a échoué : erreur", french.format("error.print", "erreur"));
        Messages italian = Messages.load(WritepadModule.class, WritepadModule.MESSAGES, Locale.ITALIAN);
        assertEquals("Pagina 1 di 3", italian.format("status.page", 1, 3));
    }
}
