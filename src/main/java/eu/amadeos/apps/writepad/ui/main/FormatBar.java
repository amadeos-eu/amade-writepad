package eu.amadeos.apps.writepad.ui.main;

import eu.amadeos.apps.writepad.service.style.ParagraphStyle;
import eu.amadeos.apps.writepad.service.style.ParagraphStyles;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.apps.writepad.ui.editor.PageEditor;
import eu.amadeos.apps.writepad.ui.editor.TextFormatting;
import eu.amadeos.commons.swing.ToolButtons;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.JComboBox;
import javax.swing.JToolBar;
import javax.swing.text.AttributeSet;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleConstants;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.List;

/**
 * Paragraph style, font, size, bold/italic/underline/strike-through, text colour, alignment and lists. The fields show the
 * formatting at the caret; choosing a font or size applies it and returns to the text.
 */
public final class FormatBar extends JToolBar {
    /** The sizes the size box offers; any other size can be typed. */
    public static final List<Integer> SIZES = List.of(8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 28, 36, 48, 72);

    private final JComboBox<ParagraphStyle> style = new JComboBox<>(ParagraphStyle.values());
    private final JComboBox<String> family;
    private final JComboBox<String> size = new JComboBox<>();
    private final TextFormatting formatting;
    private final PageEditor editor;
    private boolean updating;

    public FormatBar(EditorActions actions, PageEditor editor, Messages msg) {
        EdtExecutor.requireEdt();
        this.formatting = actions.formatting();
        this.editor = editor;
        setName("formatbar");
        setFloatable(false);
        setRollover(false);

        // --8<-- [start:style-box]
        style.setName("paragraph-style");
        style.setToolTipText(msg.get("tip.paragraph-style"));
        style.setMaximumRowCount(ParagraphStyle.values().length);
        style.setFocusable(false);
        style.setRenderer(new StyleRenderer(msg, editor));
        style.addActionListener(e -> {
            if (!updating && style.getSelectedItem() instanceof ParagraphStyle chosen) {
                actions.applyStyle(chosen);
                backToText(actions);
            }
        });
        // --8<-- [end:style-box]

        family = new JComboBox<>(fontFamilies());
        family.setName("font-family");
        family.setToolTipText(msg.get("tip.font-family"));
        family.setMaximumRowCount(20);
        family.setPrototypeDisplayValue("DejaVu Sans Condensed");
        family.setFocusable(false);
        family.addActionListener(e -> {
            if (!updating && family.getSelectedItem() instanceof String name) {
                formatting.setFontFamily(name);
                backToText(actions);
            }
        });

        for (int s : SIZES) size.addItem(String.valueOf(s));
        size.setName("font-size");
        size.setToolTipText(msg.get("tip.font-size"));
        size.setEditable(true);
        size.setPrototypeDisplayValue("888");
        size.setMaximumRowCount(SIZES.size());
        size.addActionListener(e -> {
            if (updating) return;
            Integer points = parseSize(size.getSelectedItem());
            if (points == null) {
                showSize(formatting.state().size());
            } else {
                formatting.setFontSize(points);
                backToText(actions);
            }
        });
        family.setMaximumSize(family.getPreferredSize());
        Dimension styleBox = new Dimension(150, family.getPreferredSize().height);
        style.setPreferredSize(styleBox);
        style.setMaximumSize(styleBox);
        // An editable box asks for the width of a text field; three digits and the arrow are enough.
        Dimension sizeBox = new Dimension(64, family.getPreferredSize().height);
        size.setPreferredSize(sizeBox);
        size.setMaximumSize(sizeBox);

        add(style);
        addSeparator(new Dimension(4, 1));
        add(family);
        addSeparator(new Dimension(4, 1));
        add(size);
        addSeparator();
        add(ToolButtons.toggle(actions.bold));
        add(ToolButtons.toggle(actions.italic));
        add(ToolButtons.toggle(actions.underline));
        add(ToolButtons.toggle(actions.strikeThrough));
        add(ToolButtons.button(actions.textColor));
        addSeparator();
        add(ToolButtons.toggle(actions.alignLeft));
        add(ToolButtons.toggle(actions.alignCenter));
        add(ToolButtons.toggle(actions.alignRight));
        add(ToolButtons.toggle(actions.alignJustify));
        addSeparator();
        add(ToolButtons.toggle(actions.bullets));
        add(ToolButtons.toggle(actions.numbering));

        actions.addUpdateListener(this::showState);
    }

    /** Shows font and size at the caret. */
    public void showState() {
        TextFormatting.State state = formatting.state();
        updating = true;
        try {
            // A document may use a font this computer lacks; show its name anyway.
            if (((DefaultComboBoxModel<String>) family.getModel()).getIndexOf(state.family()) < 0) {
                family.insertItemAt(state.family(), 0);
            }
            family.setSelectedItem(state.family());
            showSize(state.size());
            style.setSelectedItem(formatting.style());
        } finally {
            updating = false;
        }
    }

    JComboBox<ParagraphStyle> styleBox() {
        return style;
    }

    JComboBox<String> familyBox() {
        return family;
    }

    JComboBox<String> sizeBox() {
        return size;
    }

    private void showSize(int points) {
        boolean was = updating;
        updating = true;
        try {
            size.setSelectedItem(String.valueOf(points));
        } finally {
            updating = was;
        }
    }

    private void backToText(EditorActions actions) {
        ((JTextComponent) editor).requestFocusInWindow();
        actions.update();
    }

    /** Whole points from 1 to 1638 (the RTF limit), else null. */
    static Integer parseSize(Object value) {
        if (value == null) return null;
        try {
            int points = Integer.parseInt(value.toString().strip());
            return points >= 1 && points <= 1638 ? points : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Shows each style in its look in the open list (font, bold, italic, colour, the size kept between 9 and
     * 18 points so the list stays usable); the closed box shows the name in the box's own font.
     */
    private static final class StyleRenderer extends DefaultListCellRenderer {
        private final Messages msg;
        private final PageEditor editor;

        StyleRenderer(Messages msg, PageEditor editor) {
            this.msg = msg;
            this.editor = editor;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                      boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (!(value instanceof ParagraphStyle s)) return this;
            setText(msg.get(s.messageKey()));
            if (index < 0) return this;
            AttributeSet look = ParagraphStyles.definition(editor.getStyledDocument(), s);
            int size = Math.max(9, Math.min(18, StyleConstants.getFontSize(look)));
            int fontStyle = (StyleConstants.isBold(look) ? Font.BOLD : 0) | (StyleConstants.isItalic(look) ? Font.ITALIC : 0);
            setFont(new Font(StyleConstants.getFontFamily(look), fontStyle, size));
            // The style's colour, unless it would vanish on the list (black text on a dark look).
            java.awt.Color color = StyleConstants.getForeground(look);
            if (!selected && Math.abs(luminance(color) - luminance(list.getBackground())) > 0.35) setForeground(color);
            return this;
        }

        private static double luminance(java.awt.Color c) {
            return (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255;
        }
    }

    /** Installed font families, the logical ones (Serif, SansSerif, Monospaced, ...) first. */
    public static String[] fontFamilies() {
        String[] installed = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        List<String> logical = List.of("Serif", "SansSerif", "Monospaced", "Dialog", "DialogInput");
        String[] result = new String[installed.length];
        int i = 0;
        for (String name : logical) {
            if (Arrays.asList(installed).contains(name)) result[i++] = name;
        }
        for (String name : installed) {
            if (!logical.contains(name)) result[i++] = name;
        }
        return Arrays.copyOf(result, i);
    }
}
