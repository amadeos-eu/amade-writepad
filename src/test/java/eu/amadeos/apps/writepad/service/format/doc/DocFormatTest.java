package eu.amadeos.apps.writepad.service.format.doc;

import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.service.format.DocumentContent;
import eu.amadeos.apps.writepad.service.format.LibreOffice;
import eu.amadeos.apps.writepad.service.format.WritepadDocument;
import eu.amadeos.apps.writepad.service.format.docx.DocxFormat;
import org.junit.jupiter.api.Test;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DocFormatTest {
    private static final Optional<LibreOffice> LIBRE_OFFICE = LibreOffice.find();

    private static byte[] fixture() throws IOException {
        try (InputStream in = DocFormatTest.class.getResourceAsStream("/doc/libreoffice.doc")) {
            if (in == null) throw new IOException("Missing fixture libreoffice.doc");
            return in.readAllBytes();
        }
    }

    private static String text(DocumentContent content) throws BadLocationException {
        StyledDocument doc = content.document();
        return doc.getText(0, doc.getLength());
    }

    private static AttributeSet charAt(DocumentContent content, String text) throws BadLocationException {
        int offset = text(content).indexOf(text);
        assertTrue(offset >= 0, "no '" + text + "' in " + text(content));
        return content.document().getCharacterElement(offset).getAttributes();
    }

    @Test
    void recognisesWordFilesButNotOtherFormats() throws IOException, BadLocationException {
        DocFormat format = new DocFormat(Optional.empty());
        assertTrue(format.recognizes(fixture()));
        WritepadDocument doc = new WritepadDocument();
        doc.insertString(0, "x", null);
        byte[] docx = new DocxFormat().write(new DocumentContent(doc, DocumentSettings.DEFAULT));
        assertFalse(format.recognizes(docx));
        assertFalse(new DocxFormat().recognizes(fixture()));
    }

    @Test
    void withoutLibreOfficeItSaysSo() throws IOException {
        DocFormat format = new DocFormat(Optional.empty());
        assertFalse(format.canWrite());
        byte[] doc = fixture();
        assertThrows(LibreOffice.MissingException.class, () -> format.read(doc));
        assertThrows(LibreOffice.MissingException.class,
                () -> format.write(new DocumentContent(new WritepadDocument(), DocumentSettings.DEFAULT)));
    }

    @Test
    void readsLibreOfficeFile() throws IOException, BadLocationException {
        assumeTrue(LIBRE_OFFICE.isPresent(), "LibreOffice is not installed");
        DocumentContent c = new DocFormat(LIBRE_OFFICE).read(fixture());
        assertEquals("Titel\nEin Absatz mit kursivem und blauem fetten Text. Euro: € und „Zitat“. Grüße, weg, Привет.",
                text(c));
        assertTrue(StyleConstants.isBold(charAt(c, "Titel")));
        assertEquals("Liberation Sans", StyleConstants.getFontFamily(charAt(c, "Titel")), "without the alternative");
        assertEquals("Liberation Serif", StyleConstants.getFontFamily(charAt(c, "Absatz")));
        assertTrue(StyleConstants.isItalic(charAt(c, "kursivem")));
        assertEquals(Color.BLUE, StyleConstants.getForeground(charAt(c, "blauem")));
    }

    @Test
    void writesWhatItReads() throws IOException, BadLocationException {
        assumeTrue(LIBRE_OFFICE.isPresent(), "LibreOffice is not installed");
        DocFormat format = new DocFormat(LIBRE_OFFICE);
        assertTrue(format.canWrite());
        WritepadDocument doc = new WritepadDocument();
        SimpleAttributeSet bold = new SimpleAttributeSet();
        StyleConstants.setBold(bold, true);
        doc.insertString(0, "Fett", bold);
        doc.insertString(4, " und normal\nZweiter Absatz", null);
        byte[] written = format.finish(format.write(new DocumentContent(doc, DocumentSettings.DEFAULT)));
        assertTrue(format.recognizes(written));
        DocumentContent back = format.read(written);
        assertEquals("Fett und normal\nZweiter Absatz", text(back));
        assertTrue(StyleConstants.isBold(charAt(back, "Fett")));
        assertFalse(StyleConstants.isBold(charAt(back, "normal")));
        assertEquals(DocumentSettings.DEFAULT.paper(), back.settings().paper());
    }
}
