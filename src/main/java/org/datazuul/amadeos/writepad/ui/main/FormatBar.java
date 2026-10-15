package org.datazuul.amadeos.writepad.ui.main;

import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.commons.swing.EdtExecutor;
import org.datazuul.amadeos.writepad.ui.editor.PageEditor;
import org.datazuul.amadeos.writepad.ui.editor.TextFormatting;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JToolBar;
import javax.swing.text.JTextComponent;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.List;

/**
 * Font, size, bold/italic/underline/strike-through, text colour, alignment and lists. The fields show the
 * formatting at the caret; choosing a font or size applies it and returns to the text.
 */
public final class FormatBar extends JToolBar {
    /** The sizes the size box offers; any other size can be typed. */
    public static final List<Integer> SIZES = List.of(8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 28, 36, 48, 72);

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
        // An editable box asks for the width of a text field; three digits and the arrow are enough.
        Dimension sizeBox = new Dimension(64, family.getPreferredSize().height);
        size.setPreferredSize(sizeBox);
        size.setMaximumSize(sizeBox);

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
        } finally {
            updating = false;
        }
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

    /** Installed font families, the logical ones (Serif, SansSerif, Monospaced, ...) first. */
    private static String[] fontFamilies() {
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
