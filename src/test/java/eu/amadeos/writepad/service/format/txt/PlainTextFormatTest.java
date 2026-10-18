package eu.amadeos.writepad.service.format.txt;

import eu.amadeos.writepad.domain.DocumentSettings;
import eu.amadeos.writepad.service.format.DocumentContent;
import eu.amadeos.writepad.service.format.Documents;
import org.junit.jupiter.api.Test;

import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlainTextFormatTest {
    private final PlainTextFormat format = new PlainTextFormat();

    private String read(byte[] bytes) throws IOException, BadLocationException {
        StyledDocument doc = format.read(bytes).document();
        return doc.getText(0, doc.getLength());
    }

    @Test
    void detectsEncodings() throws Exception {
        assertEquals("Grüße", read("Grüße".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Grüße", read("Grüße".getBytes(Charset.forName("windows-1252"))));
        assertEquals("Grüße", read(concat(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                "Grüße".getBytes(StandardCharsets.UTF_8))));
        assertEquals("Grüße", read(concat(new byte[]{(byte) 0xFF, (byte) 0xFE},
                "Grüße".getBytes(StandardCharsets.UTF_16LE))));
        assertEquals("\u0081", read(new byte[]{(byte) 0x81}));
    }

    @Test
    void normalisesLineEnds() throws Exception {
        assertEquals("a\nb\nc\n", read("a\r\nb\rc\n".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void lineBreaksBecomeLineEnds() throws Exception {
        StyledDocument doc = Documents.newDocument();
        doc.insertString(0, "a\u2028b\nc", null);
        assertEquals("a\nb\nc", new String(format.write(new DocumentContent(doc, DocumentSettings.DEFAULT)),
                StandardCharsets.UTF_8));
    }

    @Test
    void refusesBinaryFiles() {
        assertThrows(IOException.class, () -> format.read(new byte[]{'a', 0, 'b'}));
    }

    @Test
    void writesUtf8WithoutFormatting() throws Exception {
        StyledDocument doc = Documents.newDocument();
        SimpleAttributeSet bold = new SimpleAttributeSet();
        StyleConstants.setBold(bold, true);
        doc.insertString(0, "Grüße\n", bold);
        doc.insertString(doc.getLength(), "€", null);
        assertArrayEquals("Grüße\n€".getBytes(StandardCharsets.UTF_8),
                format.write(new DocumentContent(doc, DocumentSettings.DEFAULT)));
        assertFalse(format.keepsFormatting());
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
