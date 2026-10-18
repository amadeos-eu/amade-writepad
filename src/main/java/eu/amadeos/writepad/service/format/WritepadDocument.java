package eu.amadeos.writepad.service.format;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;

/**
 * The document of amade-writepad: a styled document in which a line break within a paragraph (Shift+Enter,
 * RTF's {@code \line}, ODT's {@code text:line-break}) is the character {@link #LINE_BREAK}, always in an
 * element of its own marked with {@link #BREAK}, so that the page view can end the line there. However text
 * comes in (typed, pasted, read from a file), this class keeps that so; the mark never spreads to the text
 * typed after a break.
 */
public final class WritepadDocument extends DefaultStyledDocument {
    /** Unicode's LINE SEPARATOR, the line break within a paragraph. */
    public static final char LINE_BREAK = ' ';
    /** Character attribute of a line break's element. */
    public static final Object BREAK = new Object() {
        @Override
        public String toString() {
            return "line-break";
        }
    };

    @Override
    public void insertString(int offset, String text, AttributeSet attributes) throws BadLocationException {
        if (text == null || text.isEmpty()) return;
        if (text.indexOf(LINE_BREAK) < 0 && (attributes == null || !attributes.isDefined(BREAK))) {
            super.insertString(offset, text, attributes);
            return;
        }
        SimpleAttributeSet plain = new SimpleAttributeSet();
        if (attributes != null) plain.addAttributes(attributes);
        plain.removeAttribute(BREAK);
        SimpleAttributeSet lineBreak = new SimpleAttributeSet(plain);
        lineBreak.addAttribute(BREAK, Boolean.TRUE);
        int at = offset;
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == LINE_BREAK) {
                if (i > start) {
                    super.insertString(at, text.substring(start, i), plain);
                    at += i - start;
                }
                if (i < text.length()) {
                    super.insertString(at, String.valueOf(LINE_BREAK), lineBreak);
                    at++;
                }
                start = i + 1;
            }
        }
    }

    public static boolean isLineBreak(AttributeSet attributes) {
        return attributes != null && attributes.isDefined(BREAK);
    }
}
