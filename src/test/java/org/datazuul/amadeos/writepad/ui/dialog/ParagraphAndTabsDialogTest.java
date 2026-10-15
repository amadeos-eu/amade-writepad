package org.datazuul.amadeos.writepad.ui.dialog;

import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.writepad.OnEdt;
import org.datazuul.amadeos.writepad.bootstrap.WritepadModule;
import org.datazuul.amadeos.writepad.domain.MeasurementUnit;
import org.datazuul.amadeos.writepad.ui.editor.TextFormatting.Indents;
import org.datazuul.amadeos.writepad.ui.editor.TextFormatting.Spacing;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.text.StyleConstants;
import javax.swing.text.TabStop;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParagraphAndTabsDialogTest {
    private static final Messages MSG = Messages.load(WritepadModule.class, WritepadModule.MESSAGES, Locale.ENGLISH);

    @Test
    void paragraphDialogShowsAndReturnsTheValues() throws Exception {
        OnEdt.run(() -> {
            ParagraphDialog.Values values = new ParagraphDialog.Values(new Indents(56.69f, 28.35f, 14.17f),
                    new Spacing(6, 12, 1.5f), StyleConstants.ALIGN_JUSTIFIED);
            ParagraphDialog dialog = new ParagraphDialog(MSG, MeasurementUnit.CENTIMETRE, values);
            assertEquals(2.0, number(dialog, "left"), 0.001);
            assertEquals(-1.0, number(dialog, "first-line"), 0.001, "relative to the left indent, as in WordPad");
            assertEquals("1.5 lines", ((JComboBox<?>) find(dialog.panel(), "line-spacing")).getSelectedItem());
            ParagraphDialog.Values back = dialog.values();
            assertEquals(values.indents().left(), back.indents().left(), 0.1f);
            assertEquals(values.indents().firstLine(), back.indents().firstLine(), 0.1f);
            assertEquals(values.indents().right(), back.indents().right(), 0.1f);
            assertEquals(values.spacing(), back.spacing());
            assertEquals(StyleConstants.ALIGN_JUSTIFIED, back.alignment());

            ((JSpinner) find(dialog.panel(), "first-line")).setValue(-3.0);
            assertEquals(0f, dialog.values().indents().firstLine(), "the first line stays on the page");
        });
    }

    @Test
    void tabsDialogSetsChangesAndClears() throws Exception {
        OnEdt.run(() -> {
            TabsDialog dialog = new TabsDialog(MSG, MeasurementUnit.CENTIMETRE, List.of(new TabStop(56.69f)), 450);
            assertEquals("2.00 cm  Left tab", dialog.list().getModel().getElementAt(0));
            dialog.position().setValue(1.0);
            dialog.selectAlignment(2);
            dialog.set();
            assertEquals(2, dialog.tabs().size());
            assertEquals(28.35f, dialog.tabs().get(0).getPosition(), 0.01f);
            assertEquals(TabStop.ALIGN_RIGHT, dialog.tabs().get(0).getAlignment());
            dialog.position().setValue(2.0);
            dialog.selectAlignment(3);
            dialog.set();
            assertEquals(2, dialog.tabs().size(), "same position: changed, not added");
            assertEquals(TabStop.ALIGN_DECIMAL, dialog.tabs().get(1).getAlignment());
            dialog.list().setSelectedIndex(0);
            dialog.clear();
            assertEquals(1, dialog.tabs().size());
            assertEquals(56.69f, dialog.tabs().get(0).getPosition(), 0.01f, "the 1 cm stop was cleared");
        });
    }

    private static double number(ParagraphDialog dialog, String name) {
        return ((Number) ((JSpinner) find(dialog.panel(), name)).getValue()).doubleValue();
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
