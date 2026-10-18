package eu.amadeos.writepad.ui.editor;

import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;

/**
 * A few pages of formatted placeholder text, to try page breaks, zoom and printing before files can be
 * opened.
 */
public final class SampleText {
    private static final String[] SENTENCES = {
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
            "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.",
            "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.",
            "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.",
            "Curabitur pretium tincidunt lacus, nulla gravida orci a odio.",
            "Nullam varius, turpis et commodo pharetra, est eros bibendum elit, nec luctus magna felis sollicitudin mauris.",
            "Integer in mauris eu nibh euismod gravida.",
            "Duis ac tellus et risus vulputate vehicula, donec lobortis risus a elit."
    };

    private SampleText() {
    }

    /** Appends the sample: a title, sections with headings and justified paragraphs, a centred quote. */
    public static void appendTo(StyledDocument doc, int sections) {
        try {
            append(doc, "amade-writepad\n", heading(24), centred());
            append(doc, "Pages, zoom and printing\n", italic(14), centred());
            for (int section = 1; section <= sections; section++) {
                append(doc, section + ". Section " + section + "\n", heading(16), spaced(left(), 12, 4));
                for (int paragraph = 0; paragraph < 4; paragraph++) {
                    append(doc, paragraph(section * 7 + paragraph) + "\n", body(), spaced(justified(), 0, 6));
                }
                if (section % 2 == 0) {
                    append(doc, "Digital Sovereignty starts on your desktop.\n", italic(13), spaced(centred(), 6, 10));
                }
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String paragraph(int seed) {
        StringBuilder text = new StringBuilder();
        int count = 4 + seed % 5;
        for (int i = 0; i < count; i++) {
            if (i > 0) text.append(' ');
            text.append(SENTENCES[(seed + i) % SENTENCES.length]);
        }
        return text.toString();
    }

    private static void append(StyledDocument doc, String text, SimpleAttributeSet character,
                               SimpleAttributeSet paragraph) throws BadLocationException {
        int start = doc.getLength();
        doc.insertString(start, text, character);
        doc.setParagraphAttributes(start, text.length(), paragraph, false);
    }

    private static SimpleAttributeSet body() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setFontFamily(a, "Serif");
        StyleConstants.setFontSize(a, 12);
        return a;
    }

    private static SimpleAttributeSet heading(int size) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setFontFamily(a, "SansSerif");
        StyleConstants.setFontSize(a, size);
        StyleConstants.setBold(a, true);
        StyleConstants.setForeground(a, new Color(0x1F3A5F));
        return a;
    }

    private static SimpleAttributeSet italic(int size) {
        SimpleAttributeSet a = body();
        StyleConstants.setFontSize(a, size);
        StyleConstants.setItalic(a, true);
        return a;
    }

    private static SimpleAttributeSet left() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setAlignment(a, StyleConstants.ALIGN_LEFT);
        return a;
    }

    private static SimpleAttributeSet centred() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setAlignment(a, StyleConstants.ALIGN_CENTER);
        return a;
    }

    private static SimpleAttributeSet justified() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setAlignment(a, StyleConstants.ALIGN_JUSTIFIED);
        StyleConstants.setFirstLineIndent(a, 14);
        return a;
    }

    private static SimpleAttributeSet spaced(SimpleAttributeSet a, float above, float below) {
        StyleConstants.setSpaceAbove(a, above);
        StyleConstants.setSpaceBelow(a, below);
        return a;
    }
}
