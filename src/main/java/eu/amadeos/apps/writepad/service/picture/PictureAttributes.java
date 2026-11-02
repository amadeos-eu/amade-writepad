package eu.amadeos.apps.writepad.service.picture;

import eu.amadeos.apps.writepad.domain.Picture;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;
import java.util.ArrayList;
import java.util.List;

/**
 * A picture in the document is one character, {@link #OBJECT}, whose character attributes hold the
 * {@link Picture} under {@link #PICTURE}. The character takes the picture's place in the line (or anchors a
 * floating picture to its paragraph); cut, copy, delete and undo treat it like any other character.
 */
public final class PictureAttributes {
    /** Unicode's OBJECT REPLACEMENT CHARACTER, the stand-in for a picture in the text. */
    public static final char OBJECT = '￼';
    public static final Object PICTURE = new Key("picture");

    private PictureAttributes() {
    }

    /** The picture of a character element, null if it is text. */
    public static Picture picture(AttributeSet attributes) {
        return attributes != null && attributes.getAttribute(PICTURE) instanceof Picture p ? p : null;
    }

    public static boolean isPicture(AttributeSet attributes) {
        return picture(attributes) != null;
    }

    /** Attributes for a picture's character: the text format around it (for the line height) plus the picture. */
    public static SimpleAttributeSet attributes(AttributeSet around, Picture picture) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        if (around != null) a.addAttributes(around);
        a.addAttribute(PICTURE, picture);
        return a;
    }

    public static void set(MutableAttributeSet attributes, Picture picture) {
        attributes.addAttribute(PICTURE, picture);
    }

    /** Inserts a picture at an offset, in the text format of the character before it. */
    public static void insert(StyledDocument document, int offset, Picture picture) throws BadLocationException {
        AttributeSet around = document.getCharacterElement(Math.max(0, offset - 1)).getAttributes().copyAttributes();
        SimpleAttributeSet text = new SimpleAttributeSet(around);
        text.removeAttribute(PICTURE);
        document.insertString(offset, String.valueOf(OBJECT), attributes(text, picture));
    }

    /** Replaces the picture at an offset by another (a new size, placement, ...), as one undoable edit. */
    public static void replace(StyledDocument document, int offset, Picture picture) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        a.addAttribute(PICTURE, picture);
        document.setCharacterAttributes(offset, 1, a, false);
    }

    /** The picture whose character is at the offset, null if there is none. */
    public static Picture at(StyledDocument document, int offset) {
        if (offset < 0 || offset >= document.getLength()) return null;
        Element e = document.getCharacterElement(offset);
        return e.getStartOffset() == offset ? picture(e.getAttributes()) : null;
    }

    /** A picture with its offset in the document. */
    public record Placed(int offset, Picture picture) {
    }

    /** The pictures in an element (a paragraph or the whole document), in text order. */
    public static List<Placed> in(Element element) {
        List<Placed> found = new ArrayList<>();
        collect(element, found);
        return found;
    }

    private static void collect(Element element, List<Placed> found) {
        if (element.isLeaf()) {
            Picture p = picture(element.getAttributes());
            if (p != null) found.add(new Placed(element.getStartOffset(), p));
            return;
        }
        for (int i = 0; i < element.getElementCount(); i++) collect(element.getElement(i), found);
    }

    private record Key(String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
