package org.datazuul.amadeos.writepad.ui.dialog;

import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.writepad.bootstrap.WritepadModule;
import org.datazuul.amadeos.writepad.domain.DocumentSettings;
import org.datazuul.amadeos.writepad.domain.Margins;
import org.datazuul.amadeos.writepad.domain.Orientation;
import org.datazuul.amadeos.writepad.domain.PaperSize;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageSetupDialogTest {
    private final Messages msg = Messages.load(WritepadModule.class, WritepadModule.MESSAGES, Locale.ENGLISH);

    @Test
    void showsAndReturnsTheSettings() throws Exception {
        DocumentSettings settings = new DocumentSettings(PaperSize.A5, Orientation.LANDSCAPE,
                Margins.ofMm(10, 12.5, 15, 20));
        SwingUtilities.invokeAndWait(() -> {
            DocumentSettings result = new PageSetupDialog(msg, settings).settings().orElseThrow();
            assertEquals(PaperSize.A5, result.paper());
            assertEquals(Orientation.LANDSCAPE, result.orientation());
            assertEquals(settings.margins().left(), result.margins().left(), 0.01);
            assertEquals(settings.margins().right(), result.margins().right(), 0.01);
        });
    }
}
