package eu.amadeos.apps.writepad.service.list;

import javax.swing.text.AttributeSet;
import javax.swing.text.MutableAttributeSet;

/**
 * List membership as paragraph attributes: which list (an id, 0 for none), the level (0 to 8) and how the
 * item is marked. Paragraphs with the same id are numbered together, also across paragraphs between them
 * that are not in the list. Indents are ordinary paragraph indents: the label sits at the first line's
 * indent, the text at the left indent.
 */
public final class ListAttributes {
    /** Indent per level and width of the space for the label, 0.635 cm as in Word. */
    public static final float STEP = 18f;
    public static final int MAX_LEVEL = 8;

    public static final Object LIST = new Key("list");
    public static final Object LEVEL = new Key("list-level");
    public static final Object FORMAT = new Key("list-format");

    private ListAttributes() {
    }

    /** The list a paragraph belongs to, 0 if none. */
    public static int list(AttributeSet paragraph) {
        return paragraph.getAttribute(LIST) instanceof Integer id ? id : 0;
    }

    public static boolean isListItem(AttributeSet paragraph) {
        return list(paragraph) != 0;
    }

    public static int level(AttributeSet paragraph) {
        return paragraph.getAttribute(LEVEL) instanceof Integer level ? Math.max(0, Math.min(MAX_LEVEL, level)) : 0;
    }

    public static ListFormat format(AttributeSet paragraph) {
        return paragraph.getAttribute(FORMAT) instanceof ListFormat format ? format : ListFormat.BULLET;
    }

    /** Puts a paragraph into a list (id not 0) or takes it out (id 0). */
    public static void set(MutableAttributeSet attributes, int list, int level, ListFormat format) {
        attributes.addAttribute(LIST, list);
        attributes.addAttribute(LEVEL, Math.max(0, Math.min(MAX_LEVEL, level)));
        attributes.addAttribute(FORMAT, format);
    }

    /** Left indent of the text at a level: one step per level plus the label's space. */
    public static float leftIndent(int level) {
        return STEP * (level + 1);
    }

    /** Attribute keys that print readably and are not mistaken for Swing's own. */
    private record Key(String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
