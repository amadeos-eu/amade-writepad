package eu.amadeos.apps.writepad.service.format;

import eu.amadeos.apps.writepad.service.picture.PictureAttributes;
import eu.amadeos.apps.writepad.service.table.TableAttributes;

import javax.swing.event.UndoableEditEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.undo.CompoundEdit;
import java.util.ArrayList;
import java.util.List;

/**
 * The document of amade-writepad: a styled document in which a line break within a paragraph (Shift+Enter,
 * RTF's {@code \line}, ODT's {@code text:line-break}) is the character {@link #LINE_BREAK}, always in an
 * element of its own marked with {@link #BREAK}, so that the page view can end the line there. However text
 * comes in (typed, pasted, read from a file), this class keeps that so; the mark never spreads to the text
 * typed after a break.
 *
 * <p>A picture is the character {@link PictureAttributes#OBJECT} with the picture among its attributes, alone
 * in its element. The picture attribute never spreads either: text typed or inserted next to a picture loses it.
 *
 * <p>Tables are paragraphs with table attributes (see {@link TableAttributes}). The line end between two cells,
 * and between a table and the paragraph before or after it, is protected: deleting text across it removes the
 * text on both sides and keeps the cells, as in LibreOffice and Word. Only the table commands remove such line
 * ends, inside {@link #structurally}.
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

    /** A change that may throw like the document's own methods. */
    @FunctionalInterface
    public interface Change {
        void run() throws BadLocationException;
    }

    /** Edits collected into one undo step, null when none is being collected. */
    private CompoundEdit group;
    private int grouped;
    /** Depth of {@link #structurally} calls; above 0, cell borders are not protected. */
    private int structural;

    /**
     * Runs a change in which line ends between cells may be removed: the table commands (delete a row, a
     * column, a table) change the structure this way. The whole change is one undo step.
     */
    public void structurally(Change change) throws BadLocationException {
        structural++;
        try {
            grouped(change);
        } finally {
            structural--;
        }
    }

    /** Runs a change as one undo step, also when it is made of several edits. */
    public void grouped(Change change) throws BadLocationException {
        if (group != null) {
            change.run();
            return;
        }
        group = new CompoundEdit();
        grouped = 0;
        try {
            change.run();
        } finally {
            CompoundEdit done = group;
            int count = grouped;
            group = null;
            done.end();
            if (count > 0) super.fireUndoableEditUpdate(new UndoableEditEvent(this, done));
        }
    }

    /** Hands an edit made outside the document's own methods (a style change) to Undo. */
    public void postEdit(javax.swing.undo.UndoableEdit edit) {
        fireUndoableEditUpdate(new UndoableEditEvent(this, edit));
    }

    @Override
    protected void fireUndoableEditUpdate(UndoableEditEvent e) {
        if (group != null) {
            group.addEdit(e.getEdit());
            grouped++;
        } else {
            super.fireUndoableEditUpdate(e);
        }
    }

    // --8<-- [start:protect]
    /**
     * Removes text, but never a protected line end (the end of a cell, or of the paragraph before or after a
     * table): the text between such line ends is removed piece by piece, as one undo step.
     */
    @Override
    public void remove(int offset, int length) throws BadLocationException {
        if (structural > 0 || length <= 0) {
            super.remove(offset, length);
            return;
        }
        List<int[]> pieces = removable(offset, offset + length);
        if (pieces.size() == 1 && pieces.getFirst()[0] == offset && pieces.getFirst()[1] == offset + length) {
            super.remove(offset, length);
            return;
        }
        grouped(() -> {
            for (int i = pieces.size() - 1; i >= 0; i--) {
                int[] p = pieces.get(i);
                super.remove(p[0], p[1] - p[0]);
            }
        });
    }

    /** The parts of [start, end) between protected line ends, in text order, empty parts left out. */
    private List<int[]> removable(int start, int end) {
        List<int[]> pieces = new ArrayList<>();
        Element root = getDefaultRootElement();
        int from = start;
        for (int i = root.getElementIndex(start); i < root.getElementCount() - 1; i++) {
            Element paragraph = root.getElement(i);
            int lineEnd = paragraph.getEndOffset() - 1;
            if (lineEnd >= end) break;
            if (lineEnd < start) continue;
            if (isProtected(paragraph, root.getElement(i + 1))) {
                if (lineEnd > from) pieces.add(new int[]{from, lineEnd});
                from = lineEnd + 1;
            }
        }
        if (end > from) pieces.add(new int[]{from, end});
        return pieces;
    }

    /** Whether the line end between two neighbouring paragraphs is a cell's or a table's edge. */
    public static boolean isProtected(Element paragraph, Element next) {
        AttributeSet a = paragraph.getAttributes();
        AttributeSet b = next.getAttributes();
        return (TableAttributes.inTable(a) || TableAttributes.inTable(b)) && !TableAttributes.sameCell(a, b);
    }
    // --8<-- [end:protect]

    @Override
    public void insertString(int offset, String text, AttributeSet attributes) throws BadLocationException {
        if (text == null || text.isEmpty()) return;
        if (attributes != null && attributes.isDefined(PictureAttributes.PICTURE)) {
            if (text.length() == 1 && text.charAt(0) == PictureAttributes.OBJECT
                    && PictureAttributes.isPicture(attributes)) {
                SimpleAttributeSet picture = new SimpleAttributeSet(attributes);
                picture.removeAttribute(BREAK);
                super.insertString(offset, text, picture);
                return;
            }
            SimpleAttributeSet plain = new SimpleAttributeSet(attributes);
            plain.removeAttribute(PictureAttributes.PICTURE);
            attributes = plain;
        }
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
