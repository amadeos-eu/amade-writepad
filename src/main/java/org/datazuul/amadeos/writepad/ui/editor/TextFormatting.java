package org.datazuul.amadeos.writepad.ui.editor;

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
