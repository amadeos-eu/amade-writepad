package org.datazuul.amadeos.writepad.ui.editor;

import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.util.Objects;

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
