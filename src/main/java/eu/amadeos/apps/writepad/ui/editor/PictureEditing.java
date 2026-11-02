package eu.amadeos.apps.writepad.ui.editor;

import eu.amadeos.apps.writepad.domain.Picture;
import eu.amadeos.apps.writepad.service.layout.PageLayout;
import eu.amadeos.apps.writepad.service.picture.PictureAttributes;

import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Inserting pictures and changing where they sit. A picture "on its own line" is a picture in the line that
 * is alone in its paragraph; the paragraph's alignment puts it left, centred or right, which is how LibreOffice
 * and Word files hold such pictures too.
 */
public final class PictureEditing {
    /** Where a picture sits, as the Picture dialog offers it. */
    public enum Position {
        /** In the line of text, like a large letter. */
        IN_LINE,
        /** Alone in its paragraph, aligned left, centred or right. */
        OWN_LINE,
        /** At the left or right margin, the text flowing beside it. */
        FLOATING
    }

    /** A picture's position with its side: alignment for {@link Position#OWN_LINE}, left/right for floats. */
    public record Layout(Position position, int alignment) {
    }

    /** Part of the text height a new picture may take at most. */
    private static final double MAX_HEIGHT_SHARE = 0.8;

    private PictureEditing() {
    }

    /** A new picture made to fit the text column (and most of its height), keeping its proportions. */
    public static Picture fitted(Picture picture, PageLayout layout) {
        int textHeight = layout.contentBottom(0) - layout.contentTop(0);
        return picture.fittedInto(layout.contentWidth(), textHeight * MAX_HEIGHT_SHARE);
    }

    /**
     * Puts a new picture in place of the selection, on a line of its own and centred, and selects it.
     *
     * @return the picture's offset
     */
    public static int insert(JTextComponent editor, Picture picture) throws BadLocationException {
        StyledDocument doc = (StyledDocument) editor.getDocument();
        editor.replaceSelection("");
        int at = editor.getCaretPosition();
        PictureAttributes.insert(doc, at, picture);
        at = setLayout(doc, at, new Layout(Position.OWN_LINE, StyleConstants.ALIGN_CENTER));
        select(editor, at);
        return at;
    }

    /** Selects the picture at an offset, which shows its handles. */
    public static void select(JTextComponent editor, int offset) {
        editor.setCaretPosition(offset);
        editor.moveCaretPosition(offset + 1);
    }

    /** The offset of the selected picture: exactly one picture is selected. -1 otherwise. */
    public static int selectedPicture(JTextComponent editor) {
        int start = editor.getSelectionStart();
        if (editor.getSelectionEnd() - start != 1) return -1;
        return PictureAttributes.at((StyledDocument) editor.getDocument(), start) != null ? start : -1;
    }

    /** Where the picture at the offset sits now. */
    public static Layout layoutOf(StyledDocument doc, int offset) {
        Picture p = PictureAttributes.at(doc, offset);
        if (p == null) return new Layout(Position.IN_LINE, StyleConstants.ALIGN_LEFT);
        if (p.placement().floats()) {
            return new Layout(Position.FLOATING, p.placement() == Picture.Placement.FLOAT_LEFT
                    ? StyleConstants.ALIGN_LEFT : StyleConstants.ALIGN_RIGHT);
        }
        Element paragraph = doc.getParagraphElement(offset);
        if (aloneInParagraph(doc, paragraph, offset)) {
            int alignment = StyleConstants.getAlignment(paragraph.getAttributes());
            if (alignment == StyleConstants.ALIGN_JUSTIFIED) alignment = StyleConstants.ALIGN_LEFT;
            return new Layout(Position.OWN_LINE, alignment);
        }
        return new Layout(Position.IN_LINE, StyleConstants.ALIGN_LEFT);
    }

    private static boolean aloneInParagraph(StyledDocument doc, Element paragraph, int offset) {
        try {
            String before = doc.getText(paragraph.getStartOffset(), offset - paragraph.getStartOffset());
            int end = Math.min(paragraph.getEndOffset(), doc.getLength());
            String after = doc.getText(offset + 1, Math.max(0, end - offset - 1));
            return before.isBlank() && after.isBlank();
        } catch (BadLocationException e) {
            return false;
        }
    }

    /**
     * Moves the picture at an offset to a position. For a line of its own the text before and after it moves
     * into paragraphs of their own; the paragraph is then aligned.
     *
     * @return the picture's offset afterwards (it moves when a paragraph break goes in before it)
     */
    public static int setLayout(StyledDocument doc, int offset, Layout layout) throws BadLocationException {
        Picture p = PictureAttributes.at(doc, offset);
        if (p == null) return offset;
        Picture.Placement placement = switch (layout.position()) {
            case FLOATING -> layout.alignment() == StyleConstants.ALIGN_RIGHT
                    ? Picture.Placement.FLOAT_RIGHT : Picture.Placement.FLOAT_LEFT;
            default -> Picture.Placement.IN_LINE;
        };
        if (placement != p.placement()) PictureAttributes.replace(doc, offset, p.withPlacement(placement));
        if (layout.position() != Position.OWN_LINE) return offset;
        Element paragraph = doc.getParagraphElement(offset);
        if (!aloneInParagraph(doc, paragraph, offset)) {
            int end = Math.min(paragraph.getEndOffset(), doc.getLength());
            String after = doc.getText(offset + 1, Math.max(0, end - offset - 1));
            if (!after.isBlank()) doc.insertString(offset + 1, "\n", null);
            String before = doc.getText(paragraph.getStartOffset(), offset - paragraph.getStartOffset());
            if (!before.isBlank()) {
                doc.insertString(offset, "\n", null);
                offset++;
            }
        }
        SimpleAttributeSet align = new SimpleAttributeSet();
        StyleConstants.setAlignment(align, layout.alignment());
        StyleConstants.setFirstLineIndent(align, 0);
        doc.setParagraphAttributes(offset, 1, align, false);
        return offset;
    }
}
