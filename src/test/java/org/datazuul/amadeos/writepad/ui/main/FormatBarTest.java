package org.datazuul.amadeos.writepad.ui.main;

import org.datazuul.amadeos.writepad.OnEdt;
import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.writepad.bootstrap.WritepadModule;
import org.datazuul.amadeos.writepad.domain.DocumentSettings;
import org.datazuul.amadeos.writepad.repository.WritepadPreferences;
import org.datazuul.amadeos.writepad.service.layout.PageLayout;
import org.datazuul.amadeos.writepad.ui.editor.PageEditor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FormatBarTest {
    @TempDir
    Path config;

    @Test
    void showsAndSetsFontAndSize() throws Exception {
        OnEdt.run(() -> {
            Messages msg = Messages.load(WritepadModule.class, WritepadModule.MESSAGES, Locale.ENGLISH);
            PageEditor editor = new PageEditor(new PageLayout(DocumentSettings.DEFAULT, 1));
            MainPresenter presenter = new MainPresenter(null, new PageLayout(DocumentSettings.DEFAULT, 1), msg, null,
                    () -> 1, new WritepadPreferences(config.resolve("p.properties")));
            EditorActions actions = new EditorActions(editor, presenter, msg);
            FormatBar bar = new FormatBar(actions, editor, msg);
            DefaultStyledDocument doc = new DefaultStyledDocument();
            editor.setStyledDocument(doc);
            actions.attach(doc);
            SimpleAttributeSet big = new SimpleAttributeSet();
            StyleConstants.setFontFamily(big, "Some Font Not Installed");
            StyleConstants.setFontSize(big, 30);
            try {
                doc.insertString(0, "small ", null);
                doc.insertString(6, "big", big);
            } catch (BadLocationException e) {
                throw new AssertionError(e);
            }

            editor.select(7, 8);
            actions.update();
            assertEquals("Some Font Not Installed", bar.familyBox().getSelectedItem());
            assertEquals("30", bar.sizeBox().getSelectedItem());

            editor.select(0, 5);
            actions.update();
            assertEquals("12", bar.sizeBox().getSelectedItem());
            bar.sizeBox().setSelectedItem("18");
            assertEquals(18, StyleConstants.getFontSize(doc.getCharacterElement(2).getAttributes()));
            bar.familyBox().setSelectedItem("Monospaced");
            assertEquals("Monospaced", StyleConstants.getFontFamily(doc.getCharacterElement(2).getAttributes()));

            // Nonsense in the size box is rejected and the real size shown again.
            bar.sizeBox().setSelectedItem("huge");
            assertEquals("18", bar.sizeBox().getSelectedItem());
            assertEquals(18, StyleConstants.getFontSize(doc.getCharacterElement(2).getAttributes()));
        });
    }

    @Test
    void parsesSizes() {
        assertEquals(Integer.valueOf(11), FormatBar.parseSize(" 11 "));
        assertNull(FormatBar.parseSize("0"));
        assertNull(FormatBar.parseSize("2000"));
        assertNull(FormatBar.parseSize("x"));
        assertNull(FormatBar.parseSize(null));
    }
}
