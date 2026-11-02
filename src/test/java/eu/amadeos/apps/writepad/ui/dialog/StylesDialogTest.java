package eu.amadeos.apps.writepad.ui.dialog;

import eu.amadeos.apps.writepad.OnEdt;
import eu.amadeos.apps.writepad.bootstrap.WritepadModule;
import eu.amadeos.apps.writepad.domain.MeasurementUnit;
import eu.amadeos.apps.writepad.service.format.Documents;
import eu.amadeos.apps.writepad.service.style.ParagraphStyle;
import eu.amadeos.apps.writepad.service.style.ParagraphStyles;
import eu.amadeos.commons.i18n.Messages;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.Component;
import java.awt.Container;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StylesDialogTest {
    private static final Messages MSG = Messages.load(WritepadModule.class, WritepadModule.MESSAGES, Locale.ENGLISH);

    private static StylesDialog dialog(Map<ParagraphStyle, AttributeSet> current, AttributeSet caretLook) {
        return new StylesDialog(MSG, MeasurementUnit.CENTIMETRE, current, ParagraphStyle.HEADING_1, caretLook,
                new String[]{"Serif", "SansSerif", "Monospaced"}, List.of(10, 12, 18, 24));
    }

    @Test
    void editsTheChosenStyleAndReportsOnlyChanges() throws Exception {
        OnEdt.run(() -> {
            Map<ParagraphStyle, AttributeSet> current = ParagraphStyles.definitions(Documents.newDocument());
            StylesDialog d = dialog(current, null);
            assertTrue(d.changes().isEmpty());
            assertEquals("SansSerif", ((JComboBox<?>) find(d.panel(), "style-font-family")).getSelectedItem());
            assertEquals("18", ((JComboBox<?>) find(d.panel(), "style-font-size")).getSelectedItem());
            ((JComboBox<?>) find(d.panel(), "style-font-size")).setSelectedItem("24");
            ((JCheckBox) find(d.panel(), "style-italic")).doClick();
            d.select(ParagraphStyle.QUOTE);
            assertTrue(((JCheckBox) find(d.panel(), "style-italic")).isSelected(), "the quote is italic");
            Map<ParagraphStyle, AttributeSet> changes = d.changes();
            assertEquals(List.of(ParagraphStyle.HEADING_1), List.copyOf(changes.keySet()));
            assertEquals(24, StyleConstants.getFontSize(changes.get(ParagraphStyle.HEADING_1)));
            assertTrue(StyleConstants.isItalic(changes.get(ParagraphStyle.HEADING_1)));
        });
    }

    @Test
    void matchAndReset() throws Exception {
        OnEdt.run(() -> {
            Map<ParagraphStyle, AttributeSet> current = new EnumMap<>(
                    ParagraphStyles.definitions(Documents.newDocument()));
            SimpleAttributeSet changed = new SimpleAttributeSet(current.get(ParagraphStyle.HEADING_1));
            StyleConstants.setFontSize(changed, 40);
            current.put(ParagraphStyle.HEADING_1, changed);
            SimpleAttributeSet caret = ParagraphStyles.complete(ParagraphStyle.CODE.builtIn());
            StylesDialog d = dialog(current, caret);

            ((AbstractButton) find(d.panel(), "style-reset")).doClick();
            assertEquals(18, StyleConstants.getFontSize(d.changes().get(ParagraphStyle.HEADING_1)));
            ((AbstractButton) find(d.panel(), "style-match")).doClick();
            assertEquals("Monospaced", StyleConstants.getFontFamily(d.changes().get(ParagraphStyle.HEADING_1)));
            assertFalse(StyleConstants.isBold(d.changes().get(ParagraphStyle.HEADING_1)));
        });
    }

    private static Component find(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (name.equals(c.getName())) return c;
            if (c instanceof Container container) {
                Component found = find(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
