package org.datazuul.amadeos.writepad.service.format.txt;

import org.datazuul.amadeos.writepad.domain.DocumentSettings;
import org.datazuul.amadeos.writepad.service.format.DocumentContent;
import org.datazuul.amadeos.writepad.service.format.DocumentFormat;
import org.datazuul.amadeos.writepad.service.format.Documents;

import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Plain text, as amade-pad reads it: a byte order mark (UTF-8, UTF-16) wins; without one the bytes are
 * UTF-8 if they are valid UTF-8, else Windows-1252, else ISO-8859-1, which maps every byte. CR LF and CR
 * become line breaks. Saving writes UTF-8 with LF line ends and loses all formatting.
 */
public final class PlainTextFormat implements DocumentFormat {
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF16BE_BOM = {(byte) 0xFE, (byte) 0xFF};
    private static final byte[] UTF16LE_BOM = {(byte) 0xFF, (byte) 0xFE};
    private static final int BINARY_PROBE_LENGTH = 8192;
    private static final List<Charset> LEGACY = List.of(Charset.forName("windows-1252"), StandardCharsets.ISO_8859_1);

    @Override
    public String name() {
        return "Text";
    }

    @Override
    public List<String> extensions() {
        return List.of("txt", "text");
    }

    @Override
    public boolean keepsFormatting() {
        return false;
    }

    @Override
    public DocumentContent read(byte[] bytes) throws IOException {
        String text = decode(bytes).replace("\r\n", "\n").replace('\r', '\n');
        StyledDocument document = Documents.newDocument();
        try {
            document.insertString(0, text, null);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        return new DocumentContent(document, DocumentSettings.DEFAULT);
    }

    @Override
    public byte[] write(DocumentContent content) throws IOException {
        StyledDocument document = content.document();
        try {
            return document.getText(0, document.getLength()).getBytes(StandardCharsets.UTF_8);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    static String decode(byte[] bytes) throws IOException {
        if (startsWith(bytes, UTF8_BOM)) return strict(StandardCharsets.UTF_8, bytes, 3);
        if (startsWith(bytes, UTF16BE_BOM)) return strict(StandardCharsets.UTF_16BE, bytes, 2);
        if (startsWith(bytes, UTF16LE_BOM)) return strict(StandardCharsets.UTF_16LE, bytes, 2);
        int limit = Math.min(bytes.length, BINARY_PROBE_LENGTH);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) throw new IOException("Not a text file (contains NUL bytes)");
        }
        try {
            return strict(StandardCharsets.UTF_8, bytes, 0);
        } catch (CharacterCodingException notUtf8) {
            for (Charset charset : LEGACY) {
                try {
                    return strict(charset, bytes, 0);
                } catch (CharacterCodingException next) {
                    // ISO-8859-1 maps every byte, so the loop always returns.
                }
            }
            throw notUtf8;
        }
    }

    private static String strict(Charset charset, byte[] bytes, int offset) throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                .toString();
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }
}
