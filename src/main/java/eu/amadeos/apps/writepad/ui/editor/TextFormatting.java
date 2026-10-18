package eu.amadeos.apps.writepad.ui.editor;

import eu.amadeos.apps.writepad.service.list.ListAttributes;
import eu.amadeos.apps.writepad.service.list.ListFormat;

import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Character and paragraph formatting of the selection, or of the text typed next when nothing is selected,
 * as WordPad does it: buttons show the formatting at the caret (or at the start of the selection) and apply
 * to the whole selection.
 */
public final class TextFormatting {
    private final PageEditor editor;

    public TextFormatting(PageEditor editor) {
        this.editor = Objects.requireNonNull(editor, "editor");
    }

    /** What the format bar shows. */
    public record State(String family, int size, boolean bold, boolean italic, boolean underline,
                        boolean strikeThrough, Color foreground, int alignment) {
    }

    public State state() {
        AttributeSet chars = characterAttributes();
        AttributeSet paragraph = paragraphAttributes();
        return new State(StyleConstants.getFontFamily(chars), StyleConstants.getFontSize(chars),
                StyleConstants.isBold(chars), StyleConstants.isItalic(chars), StyleConstants.isUnderline(chars),
                StyleConstants.isStrikeThrough(chars), StyleConstants.getForeground(chars),
                StyleConstants.getAlignment(paragraph));
    }

    /**
     * Without a selection the attributes the next typed character gets (those of the character before the
     * caret, unless changed since); with a selection those of its first character.
     */
    public AttributeSet characterAttributes() {
        int start = editor.getSelectionStart();
        if (start == editor.getSelectionEnd()) return editor.getInputAttributes();
        StyledDocument doc = editor.getStyledDocument();
        return doc.getCharacterElement(start).getAttributes();
    }

    public AttributeSet paragraphAttributes() {
        StyledDocument doc = editor.getStyledDocument();
        Element paragraph = doc.getParagraphElement(editor.getSelectionStart());
        return paragraph.getAttributes();
    }

    public void toggleBold() {
        setCharacter(StyleConstants.Bold, !state().bold());
    }

    public void toggleItalic() {
        setCharacter(StyleConstants.Italic, !state().italic());
    }

    public void toggleUnderline() {
        setCharacter(StyleConstants.Underline, !state().underline());
    }

    public void toggleStrikeThrough() {
        setCharacter(StyleConstants.StrikeThrough, !state().strikeThrough());
    }

    public void setFontFamily(String family) {
        setCharacter(StyleConstants.FontFamily, Objects.requireNonNull(family, "family"));
    }

    public void setFontSize(int size) {
        if (size < 1 || size > 1638) throw new IllegalArgumentException("Font size out of range: " + size);
        setCharacter(StyleConstants.FontSize, size);
    }

    public void setForeground(Color color) {
        setCharacter(StyleConstants.Foreground, Objects.requireNonNull(color, "color"));
    }

    /**
     * Indents of the paragraph at the caret, in points: the left indent, the first line's start relative to
     * the left margin (as the ruler shows it) and the right indent.
     */
    public record Indents(float left, float firstLine, float right) {
        /** The first line's indent relative to the left indent, as Swing and RTF store it. */
        public float firstLineOffset() {
            return firstLine - left;
        }
    }

    public Indents indents() {
        AttributeSet p = paragraphAttributes();
        float left = StyleConstants.getLeftIndent(p);
        return new Indents(left, left + StyleConstants.getFirstLineIndent(p), StyleConstants.getRightIndent(p));
    }

    /** Sets the indents of every selected paragraph. */
    public void setIndents(Indents indents) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setLeftIndent(attributes, indents.left());
        StyleConstants.setFirstLineIndent(attributes, indents.firstLineOffset());
        StyleConstants.setRightIndent(attributes, indents.right());
        editor.setParagraphAttributes(attributes, false);
    }

    /** Spacing of the paragraph at the caret: before and after in points, line spacing as 1.0, 1.5, 2.0, .... */
    public record Spacing(float before, float after, float lines) {
    }

    public Spacing spacing() {
        AttributeSet p = paragraphAttributes();
        return new Spacing(StyleConstants.getSpaceAbove(p), StyleConstants.getSpaceBelow(p),
                1 + StyleConstants.getLineSpacing(p));
    }

    public void setSpacing(Spacing spacing) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setSpaceAbove(attributes, Math.max(0, spacing.before()));
        StyleConstants.setSpaceBelow(attributes, Math.max(0, spacing.after()));
        // Swing adds the line spacing as a fraction of the line height.
        StyleConstants.setLineSpacing(attributes, Math.max(0, spacing.lines() - 1));
        editor.setParagraphAttributes(attributes, false);
    }

    /** Tab stops of the paragraph at the caret, in points from the left margin, sorted; empty if none. */
    public List<TabStop> tabs() {
        TabSet set = StyleConstants.getTabSet(paragraphAttributes());
        List<TabStop> tabs = new ArrayList<>();
        if (set != null) {
            for (int i = 0; i < set.getTabCount(); i++) tabs.add(set.getTab(i));
        }
        return tabs;
    }

    /** Sets the tab stops of every selected paragraph; stops at the same position are merged. */
    public void setTabs(List<TabStop> tabs) {
        Map<Float, TabStop> byPosition = new TreeMap<>();
        for (TabStop tab : tabs) {
            if (tab.getPosition() >= 0) byPosition.put(Math.round(tab.getPosition() * 100) / 100f, tab);
        }
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setTabSet(attributes, new TabSet(byPosition.values().toArray(TabStop[]::new)));
        editor.setParagraphAttributes(attributes, false);
    }

    // ---------------------------------------------------------------- lists

    /** The list format of the paragraph at the caret; empty if it is not a list item. */
    public Optional<ListFormat> listFormat() {
        AttributeSet p = paragraphAttributes();
        return ListAttributes.isListItem(p) ? Optional.of(ListAttributes.format(p)) : Optional.empty();
    }

    public boolean isListItem() {
        return ListAttributes.isListItem(paragraphAttributes());
    }

    /**
     * Bullets on or off for the selected paragraphs, as the format bar button: off if they all have bullets,
     * else every one becomes a bullet item, joining a bulleted list right before or after the selection.
     */
    public void toggleBullets() {
        toggleList(true);
    }

    /** Numbering on or off, as {@link #toggleBullets()}; numbered items keep their style. */
    public void toggleNumbering() {
        toggleList(false);
    }

    private void toggleList(boolean bullets) {
        List<Element> paragraphs = selectedParagraphs();
        boolean all = paragraphs.stream().map(Element::getAttributes)
                .allMatch(a -> ListAttributes.isListItem(a) && ListAttributes.format(a).isBullet() == bullets);
        if (all) {
            for (Element p : paragraphs) removeList(p, false);
            return;
        }
        int list = neighbouringList(paragraphs, bullets);
        for (Element p : paragraphs) {
            AttributeSet a = p.getAttributes();
            boolean inList = ListAttributes.isListItem(a);
            int level = inList ? ListAttributes.level(a) : 0;
            ListFormat format = bullets ? ListFormat.BULLET
                    : inList && !ListAttributes.format(a).isBullet() ? ListAttributes.format(a)
                    : ListFormat.numberingFor(level);
            setListItem(p, list, level, format);
        }
    }

    /**
     * One level deeper (+1) or higher (-1) for the selected list items; numbering in the default style of
     * the level follows the level (1. becomes a.).
     *
     * @return false if no selected paragraph is a list item
     */
    public boolean changeLevel(int delta) {
        boolean changed = false;
        for (Element p : selectedParagraphs()) {
            AttributeSet a = p.getAttributes();
            if (!ListAttributes.isListItem(a)) continue;
            int old = ListAttributes.level(a);
            int level = Math.max(0, Math.min(ListAttributes.MAX_LEVEL, old + delta));
            ListFormat format = ListAttributes.format(a);
            if (format == ListFormat.numberingFor(old)) format = ListFormat.numberingFor(level);
            setListItem(p, ListAttributes.list(a), level, format);
            changed = true;
        }
        return changed;
    }

    /** Numbering starts again at 1 with the item at the caret: it and the rest of its list form a new list. */
    public boolean restartNumbering() {
        Element paragraph = caretParagraph();
        int old = ListAttributes.list(paragraph.getAttributes());
        if (old == 0) return false;
        moveRestOfList(paragraph, old, maxList() + 1);
        return true;
    }

    /** The item at the caret and the rest of its list continue the list before it. */
    public boolean continueNumbering() {
        Element paragraph = caretParagraph();
        int old = ListAttributes.list(paragraph.getAttributes());
        if (old == 0) return false;
        Element root = editor.getStyledDocument().getDefaultRootElement();
        for (int i = root.getElementIndex(paragraph.getStartOffset()) - 1; i >= 0; i--) {
            int list = ListAttributes.list(root.getElement(i).getAttributes());
            if (list != 0 && list != old) {
                moveRestOfList(paragraph, old, list);
                return true;
            }
        }
        return false;
    }

    /**
     * Takes the paragraph at the caret out of its list: Enter on an empty item and Backspace at the start of
     * an item do this. With {@code keepIndent} the text stays where it is (Backspace), else it goes back to
     * the margin.
     */
    public void endListAtCaret(boolean keepIndent) {
        removeList(caretParagraph(), keepIndent);
    }

    private void moveRestOfList(Element from, int oldList, int newList) {
        Element root = editor.getStyledDocument().getDefaultRootElement();
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        attributes.addAttribute(ListAttributes.LIST, newList);
        for (int i = root.getElementIndex(from.getStartOffset()); i < root.getElementCount(); i++) {
            Element p = root.getElement(i);
            if (ListAttributes.list(p.getAttributes()) == oldList) setParagraph(p, attributes);
        }
    }

    /** The list before (or else after) the paragraphs if it has the same kind of marks, else a new one. */
    private int neighbouringList(List<Element> paragraphs, boolean bullets) {
        Element root = editor.getStyledDocument().getDefaultRootElement();
        int first = root.getElementIndex(paragraphs.get(0).getStartOffset());
        int last = root.getElementIndex(paragraphs.get(paragraphs.size() - 1).getStartOffset());
        for (int i : new int[]{first - 1, last + 1}) {
            if (i < 0 || i >= root.getElementCount()) continue;
            AttributeSet a = root.getElement(i).getAttributes();
            if (ListAttributes.isListItem(a) && ListAttributes.format(a).isBullet() == bullets) {
                return ListAttributes.list(a);
            }
        }
        for (Element p : paragraphs) {
            AttributeSet a = p.getAttributes();
            if (ListAttributes.isListItem(a) && ListAttributes.format(a).isBullet() == bullets) {
                return ListAttributes.list(a);
            }
        }
        return maxList() + 1;
    }

    private int maxList() {
        Element root = editor.getStyledDocument().getDefaultRootElement();
        int max = 0;
        for (int i = 0; i < root.getElementCount(); i++) {
            max = Math.max(max, ListAttributes.list(root.getElement(i).getAttributes()));
        }
        return max;
    }

    private void setListItem(Element paragraph, int list, int level, ListFormat format) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        ListAttributes.set(attributes, list, level, format);
        StyleConstants.setLeftIndent(attributes, ListAttributes.leftIndent(level));
        StyleConstants.setFirstLineIndent(attributes, -ListAttributes.STEP);
        setParagraph(paragraph, attributes);
    }

    private void removeList(Element paragraph, boolean keepIndent) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        ListAttributes.set(attributes, 0, 0, ListFormat.BULLET);
        if (!keepIndent) StyleConstants.setLeftIndent(attributes, 0);
        StyleConstants.setFirstLineIndent(attributes, 0);
        setParagraph(paragraph, attributes);
    }

    private void setParagraph(Element paragraph, AttributeSet attributes) {
        int start = paragraph.getStartOffset();
        // Swing changes the paragraphs from offset to offset + length - 1: the paragraph's own line break.
        editor.getStyledDocument().setParagraphAttributes(start, Math.max(1, paragraph.getEndOffset() - start),
                attributes, false);
    }

    private Element caretParagraph() {
        return editor.getStyledDocument().getParagraphElement(editor.getCaretPosition());
    }

    /**
     * The paragraphs the selection touches; a selection that ends right at the start of a paragraph does not
     * include it.
     */
    public List<Element> selectedParagraphs() {
        StyledDocument doc = editor.getStyledDocument();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (end > start) end--;
        Element root = doc.getDefaultRootElement();
        List<Element> paragraphs = new ArrayList<>();
        for (int i = root.getElementIndex(start); i <= root.getElementIndex(end); i++) {
            paragraphs.add(root.getElement(i));
        }
        return paragraphs;
    }

    /** {@link StyleConstants#ALIGN_LEFT}, {@code ALIGN_CENTER}, {@code ALIGN_RIGHT} or {@code ALIGN_JUSTIFIED}. */
    public void setAlignment(int alignment) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setAlignment(attributes, alignment);
        editor.setParagraphAttributes(attributes, false);
    }

    private void setCharacter(Object key, Object value) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        attributes.addAttribute(key, value);
        // With a selection the document changes (and can be undone); without, the input attributes do.
        editor.setCharacterAttributes(attributes, false);
    }
}
