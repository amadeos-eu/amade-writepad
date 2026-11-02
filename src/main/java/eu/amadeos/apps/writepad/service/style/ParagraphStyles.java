package eu.amadeos.apps.writepad.service.style;

import eu.amadeos.apps.writepad.service.list.ListAttributes;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.Position;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Named paragraph styles in a Swing document. Each {@link ParagraphStyle} is a {@link Style} of the document
 * that sets every key of {@link #KEYS}; a paragraph's logical style (its resolve parent) is one of them.
 * Direct formatting stays on the paragraph and on its runs and wins over the style, so changing a style's
 * definition changes every paragraph that uses it, except where something was set by hand.
 *
 * <p>Swing's own default style is not used as Normal: the text pane copies its font into that style when a
 * document is installed. Paragraphs whose logical style is not one of these count as Normal.
 */
public final class ParagraphStyles {
    /** The character formatting a style defines. */
    public static final List<Object> CHARACTER_KEYS = List.of(StyleConstants.FontFamily, StyleConstants.FontSize,
            StyleConstants.Bold, StyleConstants.Italic, StyleConstants.Underline, StyleConstants.StrikeThrough,
            StyleConstants.Superscript, StyleConstants.Subscript, StyleConstants.Foreground);
    /** The paragraph formatting a style defines. */
    public static final List<Object> PARAGRAPH_KEYS = List.of(StyleConstants.Alignment, StyleConstants.LeftIndent,
            StyleConstants.RightIndent, StyleConstants.FirstLineIndent, StyleConstants.SpaceAbove,
            StyleConstants.SpaceBelow, StyleConstants.LineSpacing);
    /** Everything a style defines. */
    public static final List<Object> KEYS;

    static {
        List<Object> keys = new ArrayList<>(CHARACTER_KEYS);
        keys.addAll(PARAGRAPH_KEYS);
        KEYS = Collections.unmodifiableList(keys);
    }

    /** A paragraph and the style it gets, for {@link #adopt}. */
    public record Assignment(Position paragraph, ParagraphStyle style) {
    }

    private ParagraphStyles() {
    }

    // ---------------------------------------------------------------- values

    /**
     * The value of a key as the attributes give it, Swing's default where they do not: lengths rounded to
     * hundredths of a point, so that values read from a file compare equal to those written.
     */
    public static Object value(AttributeSet a, Object key) {
        if (key == StyleConstants.FontFamily) return StyleConstants.getFontFamily(a);
        if (key == StyleConstants.FontSize) return StyleConstants.getFontSize(a);
        if (key == StyleConstants.Bold) return StyleConstants.isBold(a);
        if (key == StyleConstants.Italic) return StyleConstants.isItalic(a);
        if (key == StyleConstants.Underline) return StyleConstants.isUnderline(a);
        if (key == StyleConstants.StrikeThrough) return StyleConstants.isStrikeThrough(a);
        if (key == StyleConstants.Superscript) return StyleConstants.isSuperscript(a);
        if (key == StyleConstants.Subscript) return StyleConstants.isSubscript(a);
        if (key == StyleConstants.Foreground) return StyleConstants.getForeground(a).getRGB() | 0xFF000000;
        if (key == StyleConstants.Alignment) return StyleConstants.getAlignment(a);
        if (key == StyleConstants.LeftIndent) return round(StyleConstants.getLeftIndent(a));
        if (key == StyleConstants.RightIndent) return round(StyleConstants.getRightIndent(a));
        if (key == StyleConstants.FirstLineIndent) return round(StyleConstants.getFirstLineIndent(a));
        if (key == StyleConstants.SpaceAbove) return round(StyleConstants.getSpaceAbove(a));
        if (key == StyleConstants.SpaceBelow) return round(StyleConstants.getSpaceBelow(a));
        if (key == StyleConstants.LineSpacing) return round(StyleConstants.getLineSpacing(a));
        throw new IllegalArgumentException("Not a style key: " + key);
    }

    private static float round(float points) {
        return Math.round(points * 100) / 100f;
    }

    /** Puts a value as {@link #value} returns it back into attributes. */
    private static void put(MutableAttributeSet a, Object key, Object value) {
        if (key == StyleConstants.Foreground) a.addAttribute(key, new Color((Integer) value, true));
        else a.addAttribute(key, value);
    }

    /**
     * Every key of {@link #KEYS} with the value the attributes give it; black text stays without a colour,
     * as the automatic colour that files leave open.
     */
    public static SimpleAttributeSet complete(AttributeSet a) {
        SimpleAttributeSet full = new SimpleAttributeSet();
        for (Object key : KEYS) {
            Object v = value(a, key);
            if (key != StyleConstants.Foreground || !v.equals(BLACK)) put(full, key, v);
        }
        return full;
    }

    private static final Object BLACK = Color.BLACK.getRGB();

    // ---------------------------------------------------------------- styles of a document

    /** Adds the built-in styles to a document that lacks them, and makes its first paragraph Normal. */
    public static void install(StyledDocument document) {
        for (ParagraphStyle s : ParagraphStyle.values()) {
            if (document.getStyle(s.swingName()) == null) {
                document.addStyle(s.swingName(), null).addAttributes(s.builtIn());
            }
        }
        Element first = document.getDefaultRootElement().getElement(0);
        if (first != null && !isOurs(first.getAttributes().getResolveParent())) {
            document.setLogicalStyle(0, document.getStyle(ParagraphStyle.NORMAL.swingName()));
        }
    }

    /** The document's style, added with its built-in look if the document has none yet. */
    public static Style style(StyledDocument document, ParagraphStyle style) {
        Style s = document.getStyle(style.swingName());
        if (s == null) {
            s = document.addStyle(style.swingName(), null);
            s.addAttributes(style.builtIn());
        }
        return s;
    }

    private static boolean isOurs(AttributeSet parent) {
        if (parent instanceof Style s) {
            for (ParagraphStyle p : ParagraphStyle.values()) {
                if (p.swingName().equals(s.getName())) return true;
            }
        }
        return false;
    }

    /** The style of a paragraph; Normal for paragraphs without one of ours. */
    public static ParagraphStyle of(Element paragraph) {
        AttributeSet parent = paragraph.getAttributes().getResolveParent();
        if (parent instanceof Style s) {
            for (ParagraphStyle p : ParagraphStyle.values()) {
                if (p.swingName().equals(s.getName())) return p;
            }
        }
        return ParagraphStyle.NORMAL;
    }

    /** The current definition of a style in the document: every key set. */
    public static SimpleAttributeSet definition(StyledDocument document, ParagraphStyle style) {
        return complete(style(document, style));
    }

    /** The definitions of all styles in the document. */
    public static Map<ParagraphStyle, AttributeSet> definitions(StyledDocument document) {
        Map<ParagraphStyle, AttributeSet> all = new EnumMap<>(ParagraphStyle.class);
        for (ParagraphStyle s : ParagraphStyle.values()) all.put(s, definition(document, s));
        return all;
    }

    /**
     * Gives a style a new definition; keys the values leave open take Swing's default (not bold, black,
     * ...). Every paragraph in the style shows the change. Not an undoable edit by itself: see {@link StyleEdit}.
     */
    public static void define(StyledDocument document, ParagraphStyle style, AttributeSet values) {
        Style s = style(document, style);
        SimpleAttributeSet full = complete(values);
        if (complete(s).isEqual(full)) return;
        s.removeAttributes(java.util.Collections.enumeration(KEYS));
        s.addAttributes(full);
    }

    /** The styles the document's paragraphs use, in their order; Normal always. */
    public static Set<ParagraphStyle> used(StyledDocument document) {
        Set<ParagraphStyle> used = EnumSet.of(ParagraphStyle.NORMAL);
        Element root = document.getDefaultRootElement();
        for (int i = 0; i < root.getElementCount(); i++) used.add(of(root.getElement(i)));
        return used;
    }

    // ---------------------------------------------------------------- direct formatting

    /** The paragraph formatting set on the paragraph that differs from its style's. */
    public static SimpleAttributeSet paragraphDirect(StyledDocument document, Element paragraph) {
        Style style = style(document, of(paragraph));
        SimpleAttributeSet direct = new SimpleAttributeSet();
        for (Object key : PARAGRAPH_KEYS) {
            Object v = value(paragraph.getAttributes(), key);
            if (!v.equals(value(style, key))) put(direct, key, v);
        }
        return direct;
    }

    /**
     * The character formatting of a run that differs from that of its paragraph (its style, or what is set on
     * the paragraph itself).
     */
    public static SimpleAttributeSet characterDirect(AttributeSet run, AttributeSet paragraph) {
        SimpleAttributeSet direct = new SimpleAttributeSet();
        for (Object key : CHARACTER_KEYS) {
            Object v = value(run, key);
            if (!v.equals(value(paragraph, key))) put(direct, key, v);
        }
        return direct;
    }

    // ---------------------------------------------------------------- applying styles

    // --8<-- [start:apply]
    /**
     * Gives paragraphs a style, as Word does it: the paragraph formatting set by hand goes (except a list's
     * indents), and so does character formatting that covers a whole paragraph, so a heading made of bold
     * 16 pt text takes the heading's look; a bold word in it stays bold.
     */
    public static void apply(StyledDocument document, List<Element> paragraphs, ParagraphStyle style) {
        Style s = style(document, style);
        List<int[]> ranges = new ArrayList<>();
        for (Element p : paragraphs) ranges.add(new int[]{p.getStartOffset(), p.getEndOffset()});
        for (int[] range : ranges) {
            Element p = document.getParagraphElement(range[0]);
            List<Object> uniform = new ArrayList<>();
            for (Object key : CHARACTER_KEYS) {
                if (uniform(p, key)) uniform.add(key);
            }
            removeFromRuns(document, p, p.getStartOffset(), p.getEndOffset(), uniform);
            setParagraph(document, p, s, keysToClear(p));
        }
    }
    // --8<-- [end:apply]

    /**
     * Removes the formatting set by hand from the paragraphs the range touches and from the text in it: the
     * text takes its paragraph style's look again. An empty range clears the whole paragraph it is in. Lists
     * keep their indents.
     */
    public static void clearDirect(StyledDocument document, int start, int end) {
        Element root = document.getDefaultRootElement();
        int last = end > start ? end - 1 : start;
        List<int[]> ranges = new ArrayList<>();
        for (int i = root.getElementIndex(start); i <= root.getElementIndex(last); i++) {
            Element p = root.getElement(i);
            ranges.add(end > start
                    ? new int[]{p.getStartOffset(), Math.max(start, p.getStartOffset()), Math.min(end, p.getEndOffset())}
                    : new int[]{p.getStartOffset(), p.getStartOffset(), p.getEndOffset()});
        }
        for (int[] r : ranges) {
            Element p = document.getParagraphElement(r[0]);
            removeFromRuns(document, p, r[1], r[2], CHARACTER_KEYS);
            setParagraph(document, p, style(document, of(p)), keysToClear(p));
        }
    }

    /** Character formatting the next typed text should not carry once its paragraph takes a style. */
    public static void clearInput(MutableAttributeSet input) {
        for (Object key : CHARACTER_KEYS) input.removeAttribute(key);
    }

    /** Paragraph keys a style change removes: all, except the indents of a list item. */
    private static List<Object> keysToClear(Element paragraph) {
        if (!ListAttributes.isListItem(paragraph.getAttributes())) return PARAGRAPH_KEYS;
        List<Object> keys = new ArrayList<>(PARAGRAPH_KEYS);
        keys.remove(StyleConstants.LeftIndent);
        keys.remove(StyleConstants.FirstLineIndent);
        return keys;
    }

    private static boolean uniform(Element paragraph, Object key) {
        Object first = null;
        for (int i = 0; i < paragraph.getElementCount(); i++) {
            Object v = value(paragraph.getElement(i).getAttributes(), key);
            if (first == null) first = v;
            else if (!first.equals(v)) return false;
        }
        return true;
    }

    /** Takes keys off the runs of a paragraph between two offsets. */
    private static void removeFromRuns(StyledDocument document, Element paragraph, int from, int to,
                                       List<Object> keys) {
        if (keys.isEmpty() || to <= from) return;
        List<Object[]> changes = new ArrayList<>();
        for (int i = 0; i < paragraph.getElementCount(); i++) {
            Element run = paragraph.getElement(i);
            int start = Math.max(from, run.getStartOffset());
            int end = Math.min(to, run.getEndOffset());
            if (end <= start || keys.stream().noneMatch(run.getAttributes()::isDefined)) continue;
            SimpleAttributeSet kept = new SimpleAttributeSet(run.getAttributes().copyAttributes());
            for (Object key : keys) kept.removeAttribute(key);
            kept.removeAttribute(StyleConstants.ResolveAttribute);
            changes.add(new Object[]{start, end, kept});
        }
        for (Object[] c : changes) {
            document.setCharacterAttributes((Integer) c[0], (Integer) c[1] - (Integer) c[0], (AttributeSet) c[2], true);
        }
    }

    /** Sets a paragraph's style and takes keys off the paragraph itself. */
    private static void setParagraph(StyledDocument document, Element paragraph, Style style, List<Object> keys) {
        SimpleAttributeSet kept = new SimpleAttributeSet(paragraph.getAttributes().copyAttributes());
        for (Object key : keys) kept.removeAttribute(key);
        for (Object key : CHARACTER_KEYS) kept.removeAttribute(key);
        kept.setResolveParent(style);
        if (kept.isEqual(paragraph.getAttributes().copyAttributes())) return;
        int start = paragraph.getStartOffset();
        document.setParagraphAttributes(start, Math.max(1, paragraph.getEndOffset() - start), kept, true);
    }

    // ---------------------------------------------------------------- after reading a file

    // --8<-- [start:adopt]
    /**
     * Turns a document a reader filled with complete formatting on every paragraph and run into one with
     * styles: the styles get the file's definitions (the built-in look for those the file does not define),
     * each paragraph its style (Normal where none is given), and only what differs from the style stays set
     * on the paragraph and its runs. How the text looks does not change.
     *
     * @param assignments the paragraphs that have a style other than Normal
     * @param definitions the styles as the file defines them; may leave keys and styles out
     */
    public static void adopt(StyledDocument document, List<Assignment> assignments,
                             Map<ParagraphStyle, ? extends AttributeSet> definitions) {
        Element root = document.getDefaultRootElement();
        // What everything looks like now, before the styles change what the attributes resolve to.
        int count = root.getElementCount();
        List<Map<Object, Object>> paragraphLooks = new ArrayList<>(count);
        List<List<Map<Object, Object>>> runLooks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Element p = root.getElement(i);
            paragraphLooks.add(look(p.getAttributes(), PARAGRAPH_KEYS));
            List<Map<Object, Object>> runs = new ArrayList<>(p.getElementCount());
            for (int r = 0; r < p.getElementCount(); r++) runs.add(look(p.getElement(r).getAttributes(), CHARACTER_KEYS));
            runLooks.add(runs);
        }
        install(document);
        ParagraphStyle[] styles = new ParagraphStyle[count];
        java.util.Arrays.fill(styles, ParagraphStyle.NORMAL);
        for (Assignment a : assignments) {
            styles[root.getElementIndex(Math.min(a.paragraph().getOffset(), document.getLength()))] = a.style();
        }
        for (ParagraphStyle s : ParagraphStyle.values()) {
            AttributeSet defined = definitions.get(s);
            if (defined == null) continue;
            SimpleAttributeSet values = new SimpleAttributeSet(s.builtIn());
            for (Object key : KEYS) {
                if (defined.isDefined(key)) put(values, key, value(defined, key));
            }
            define(document, s, values);
        }
        for (int i = 0; i < count; i++) {
            Element p = root.getElement(i);
            Style style = style(document, styles[i]);
            SimpleAttributeSet own = new SimpleAttributeSet(p.getAttributes().copyAttributes());
            for (Object key : KEYS) own.removeAttribute(key);
            for (Object key : PARAGRAPH_KEYS) {
                Object v = paragraphLooks.get(i).get(key);
                if (!v.equals(value(style, key))) put(own, key, v);
            }
            own.setResolveParent(style);
            int start = p.getStartOffset();
            document.setParagraphAttributes(start, Math.max(1, p.getEndOffset() - start), own, true);
            p = root.getElement(i);
            List<Object[]> changes = new ArrayList<>();
            for (int r = 0; r < p.getElementCount(); r++) {
                Element run = p.getElement(r);
                SimpleAttributeSet kept = new SimpleAttributeSet(run.getAttributes().copyAttributes());
                for (Object key : CHARACTER_KEYS) kept.removeAttribute(key);
                kept.removeAttribute(StyleConstants.ResolveAttribute);
                Map<Object, Object> look = runLooks.get(i).get(r);
                for (Object key : CHARACTER_KEYS) {
                    Object v = look.get(key);
                    if (!v.equals(value(style, key))) put(kept, key, v);
                }
                changes.add(new Object[]{run.getStartOffset(), run.getEndOffset(), kept});
            }
            for (Object[] c : changes) {
                document.setCharacterAttributes((Integer) c[0], (Integer) c[1] - (Integer) c[0],
                        (AttributeSet) c[2], true);
            }
        }
    }
    // --8<-- [end:adopt]

    private static Map<Object, Object> look(AttributeSet a, List<Object> keys) {
        Map<Object, Object> look = new HashMap<>();
        for (Object key : keys) look.put(key, value(a, key));
        return look;
    }

    /** Where a paragraph starts, as a position that moves with later changes. */
    public static Position position(StyledDocument document, int offset) {
        try {
            return document.createPosition(offset);
        } catch (BadLocationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** The style of each paragraph as a reader found it, kept until {@link #adopt} runs. */
    public static final class Collector {
        private final StyledDocument document;
        private final List<Assignment> assignments = new ArrayList<>();

        public Collector(StyledDocument document) {
            this.document = Objects.requireNonNull(document, "document");
        }

        /**
         * Notes the style of the paragraph that starts at an offset, once the paragraph is in the document
         * (a position at the document's end would move with the text added there); Normal needs no note.
         */
        public void paragraph(int start, ParagraphStyle style) {
            if (style != null && style != ParagraphStyle.NORMAL) assignments.add(new Assignment(position(document, start), style));
        }

        public void adopt(Map<ParagraphStyle, ? extends AttributeSet> definitions) {
            ParagraphStyles.adopt(document, assignments, definitions);
        }
    }
}
