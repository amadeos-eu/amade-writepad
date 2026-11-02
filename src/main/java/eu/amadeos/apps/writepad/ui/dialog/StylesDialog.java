package eu.amadeos.apps.writepad.ui.dialog;

import eu.amadeos.apps.writepad.domain.MeasurementUnit;
import eu.amadeos.apps.writepad.service.style.ParagraphStyle;
import eu.amadeos.apps.writepad.service.style.ParagraphStyles;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.Icon;

/**
 * Format → Styles: the built-in paragraph styles on the left, the look of the chosen one on the right (font,
 * size, bold, italic, underline, colour; alignment, spacing, indents) with a preview. "Match Selection" takes
 * the formatting at the caret, "Reset to Default" the built-in look. OK changes every paragraph in the
 * changed styles; formatting set by hand on them stays.
 */
public final class StylesDialog {
    private static final int[] ALIGNMENTS = {StyleConstants.ALIGN_LEFT, StyleConstants.ALIGN_CENTER,
            StyleConstants.ALIGN_RIGHT, StyleConstants.ALIGN_JUSTIFIED};

    private final Messages msg;
    private final MeasurementUnit unit;
    private final Map<ParagraphStyle, AttributeSet> original;
    private final Map<ParagraphStyle, SimpleAttributeSet> edited = new EnumMap<>(ParagraphStyle.class);
    private final AttributeSet caretLook;

    private final JList<ParagraphStyle> list = new JList<>(ParagraphStyle.values());
    private final JComboBox<String> family;
    private final JComboBox<String> size = new JComboBox<>();
    private final JCheckBox bold;
    private final JCheckBox italic;
    private final JCheckBox underline;
    private final JButton color;
    private Color chosenColor = Color.BLACK;
    private final JComboBox<String> alignment = new JComboBox<>();
    private final JComboBox<String> lines = new JComboBox<>();
    private final JSpinner before = points();
    private final JSpinner after = points();
    private final JSpinner left;
    private final JSpinner right;
    private final JLabel preview = new JLabel();
    private final JPanel panel = new JPanel(new BorderLayout(12, 8));
    private ParagraphStyle shown;
    private boolean loading;

    /**
     * @param current   the definitions of all styles in the document
     * @param selected  the style shown first: the one at the caret
     * @param caretLook the formatting at the caret, for Match Selection
     * @param families  the font families the font box offers
     * @param sizes     the sizes the size box offers
     */
    public StylesDialog(Messages msg, MeasurementUnit unit, Map<ParagraphStyle, AttributeSet> current,
                        ParagraphStyle selected, AttributeSet caretLook, String[] families, List<Integer> sizes) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.unit = unit;
        this.original = new EnumMap<>(current);
        this.caretLook = caretLook;
        for (ParagraphStyle s : ParagraphStyle.values()) {
            edited.put(s, ParagraphStyles.complete(current.getOrDefault(s, s.builtIn())));
        }
        family = new JComboBox<>(families);
        family.setName("style-font-family");
        family.setMaximumRowCount(20);
        family.setPrototypeDisplayValue("DejaVu Sans Condensed");
        for (int s : sizes) size.addItem(String.valueOf(s));
        size.setName("style-font-size");
        size.setEditable(true);
        size.setPrototypeDisplayValue("888");
        bold = new JCheckBox(msg.get("dialog.styles.bold"));
        bold.setName("style-bold");
        italic = new JCheckBox(msg.get("dialog.styles.italic"));
        italic.setName("style-italic");
        underline = new JCheckBox(msg.get("dialog.styles.underline"));
        underline.setName("style-underline");
        color = new JButton(msg.get("dialog.styles.color"));
        color.setName("style-color");
        color.setIcon(new Swatch());
        color.addActionListener(e -> {
            Color c = JColorChooser.showDialog(panel, msg.get("dialog.text-color.title"), chosenColor);
            if (c != null) {
                chosenColor = c;
                changed();
            }
        });
        for (String key : new String[]{"align.left", "align.center", "align.right", "align.justify"}) {
            alignment.addItem(msg.get(key));
        }
        alignment.setName("style-alignment");
        for (float spacing : ParagraphDialog.LINE_SPACINGS) lines.addItem(lineLabel(spacing));
        lines.setName("style-line-spacing");
        before.setName("style-before");
        after.setName("style-after");
        left = length();
        left.setName("style-left");
        right = length();
        right.setName("style-right");

        list.setName("styles");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(l, value, index, selected, focus);
                if (value instanceof ParagraphStyle s) setText(msg.get(s.messageKey()));
                return this;
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) show(list.getSelectedValue());
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(170, 260));

        JPanel font = section("dialog.styles.font");
        GridBagConstraints c = constraints(0, 0);
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.LINE_START;
        JPanel fontRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        fontRow.add(family);
        fontRow.add(size);
        fontRow.add(color);
        font.add(fontRow, c);
        c = constraints(0, 1);
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.LINE_START;
        JPanel switches = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        switches.add(bold);
        switches.add(italic);
        switches.add(underline);
        font.add(switches, c);

        JPanel paragraph = section("dialog.styles.paragraph");
        row(paragraph, 0, msg.get("dialog.paragraph.alignment"), alignment);
        row(paragraph, 1, msg.get("dialog.paragraph.line-spacing"), lines);
        row(paragraph, 2, msg.get("dialog.paragraph.before"), before);
        row(paragraph, 3, msg.get("dialog.paragraph.after"), after);
        row(paragraph, 4, msg.format("dialog.paragraph.left", unit.symbol()), left);
        row(paragraph, 5, msg.format("dialog.paragraph.right", unit.symbol()), right);

        preview.setName("style-preview");
        preview.setOpaque(true);
        preview.setBackground(Color.WHITE);
        preview.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        preview.setPreferredSize(new Dimension(360, 56));

        JPanel settings = new JPanel(new GridBagLayout());
        c = constraints(0, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        settings.add(font, c);
        c = constraints(0, 1);
        c.fill = GridBagConstraints.HORIZONTAL;
        settings.add(paragraph, c);
        c = constraints(0, 2);
        c.fill = GridBagConstraints.HORIZONTAL;
        settings.add(preview, c);

        JButton match = new JButton(msg.get("dialog.styles.match"));
        match.setName("style-match");
        match.setToolTipText(msg.get("tip.styles.match"));
        match.setEnabled(caretLook != null);
        match.addActionListener(e -> {
            if (shown != null && caretLook != null) {
                edited.put(shown, ParagraphStyles.complete(caretLook));
                show(shown);
            }
        });
        JButton reset = new JButton(msg.get("dialog.styles.reset"));
        reset.setName("style-reset");
        reset.addActionListener(e -> {
            if (shown != null) {
                edited.put(shown, ParagraphStyles.complete(shown.builtIn()));
                show(shown);
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(match);
        buttons.add(reset);

        JLabel hint = new JLabel(msg.get("dialog.styles.hint"));
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN));
        panel.add(scroll, BorderLayout.WEST);
        panel.add(settings, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.add(buttons, BorderLayout.NORTH);
        south.add(hint, BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);

        family.addActionListener(e -> changed());
        size.addActionListener(e -> changed());
        bold.addActionListener(e -> changed());
        italic.addActionListener(e -> changed());
        underline.addActionListener(e -> changed());
        alignment.addActionListener(e -> changed());
        lines.addActionListener(e -> changed());
        before.addChangeListener(e -> changed());
        after.addChangeListener(e -> changed());
        left.addChangeListener(e -> changed());
        right.addChangeListener(e -> changed());

        list.setSelectedValue(selected != null ? selected : ParagraphStyle.NORMAL, true);
    }

    /** Shows the dialog; the styles whose look changed, empty if none did or the dialog was cancelled. */
    public Optional<Map<ParagraphStyle, AttributeSet>> showDialog(Component parent) {
        int answer = JOptionPane.showConfirmDialog(parent, panel, msg.get("dialog.styles.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return Optional.empty();
        Map<ParagraphStyle, AttributeSet> changed = changes();
        return changed.isEmpty() ? Optional.empty() : Optional.of(changed);
    }

    /** The styles whose look differs from what the document has. */
    public Map<ParagraphStyle, AttributeSet> changes() {
        Map<ParagraphStyle, AttributeSet> changed = new EnumMap<>(ParagraphStyle.class);
        edited.forEach((style, values) -> {
            AttributeSet before = ParagraphStyles.complete(original.getOrDefault(style, style.builtIn()));
            if (!before.isEqual(values)) changed.put(style, values);
        });
        return changed;
    }

    /** Chooses the style shown, as a click in the list does. */
    public void select(ParagraphStyle style) {
        list.setSelectedValue(style, true);
    }

    JPanel panel() {
        return panel;
    }

    private void show(ParagraphStyle style) {
        shown = style;
        AttributeSet a = edited.get(style);
        loading = true;
        try {
            String f = StyleConstants.getFontFamily(a);
            if (Arrays.stream(familyItems()).noneMatch(f::equals)) family.insertItemAt(f, 0);
            family.setSelectedItem(f);
            size.setSelectedItem(String.valueOf(StyleConstants.getFontSize(a)));
            bold.setSelected(StyleConstants.isBold(a));
            italic.setSelected(StyleConstants.isItalic(a));
            underline.setSelected(StyleConstants.isUnderline(a));
            chosenColor = StyleConstants.getForeground(a);
            for (int i = 0; i < ALIGNMENTS.length; i++) {
                if (ALIGNMENTS[i] == StyleConstants.getAlignment(a)) alignment.setSelectedIndex(i);
            }
            lines.setSelectedIndex(nearest(1 + StyleConstants.getLineSpacing(a)));
            before.setValue((double) Math.round(StyleConstants.getSpaceAbove(a)));
            after.setValue((double) Math.round(StyleConstants.getSpaceBelow(a)));
            left.setValue(round(unit.toUnit(StyleConstants.getLeftIndent(a))));
            right.setValue(round(unit.toUnit(StyleConstants.getRightIndent(a))));
        } finally {
            loading = false;
        }
        updatePreview();
    }

    private String[] familyItems() {
        String[] items = new String[family.getItemCount()];
        for (int i = 0; i < items.length; i++) items[i] = family.getItemAt(i);
        return items;
    }

    /** Takes the fields into the style shown. */
    private void changed() {
        if (loading || shown == null) return;
        SimpleAttributeSet a = new SimpleAttributeSet(edited.get(shown));
        if (family.getSelectedItem() instanceof String f) StyleConstants.setFontFamily(a, f);
        Integer points = parseSize(size.getSelectedItem());
        if (points != null) StyleConstants.setFontSize(a, points);
        StyleConstants.setBold(a, bold.isSelected());
        StyleConstants.setItalic(a, italic.isSelected());
        StyleConstants.setUnderline(a, underline.isSelected());
        StyleConstants.setForeground(a, chosenColor);
        StyleConstants.setAlignment(a, ALIGNMENTS[Math.max(0, alignment.getSelectedIndex())]);
        StyleConstants.setLineSpacing(a, ParagraphDialog.LINE_SPACINGS.get(Math.max(0, lines.getSelectedIndex())) - 1);
        StyleConstants.setSpaceAbove(a, (float) number(before));
        StyleConstants.setSpaceBelow(a, (float) number(after));
        StyleConstants.setLeftIndent(a, (float) unit.toPoints(number(left)));
        StyleConstants.setRightIndent(a, (float) unit.toPoints(number(right)));
        edited.put(shown, ParagraphStyles.complete(a));
        updatePreview();
    }

    private void updatePreview() {
        AttributeSet a = edited.get(shown);
        int style = (StyleConstants.isBold(a) ? Font.BOLD : 0) | (StyleConstants.isItalic(a) ? Font.ITALIC : 0);
        preview.setFont(new Font(StyleConstants.getFontFamily(a), style, Math.min(28, StyleConstants.getFontSize(a))));
        preview.setForeground(StyleConstants.getForeground(a));
        preview.setHorizontalAlignment(switch (StyleConstants.getAlignment(a)) {
            case StyleConstants.ALIGN_CENTER -> SwingConstants.CENTER;
            case StyleConstants.ALIGN_RIGHT -> SwingConstants.RIGHT;
            default -> SwingConstants.LEFT;
        });
        String name = msg.get(shown.messageKey());
        preview.setText(StyleConstants.isUnderline(a) ? "<html><u>" + escape(name) + "</u></html>" : name);
        color.repaint();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }

    /** The colour on the Colour button. */
    private final class Swatch implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(chosenColor);
            g.fillRect(x, y, 14, 14);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(x, y, 13, 13);
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }

    private String lineLabel(float spacing) {
        if (spacing == 1f) return msg.get("dialog.paragraph.single");
        if (spacing == 2f) return msg.get("dialog.paragraph.double");
        return msg.format("dialog.paragraph.lines", (double) spacing);
    }

    private static int nearest(float spacing) {
        List<Float> all = new ArrayList<>(ParagraphDialog.LINE_SPACINGS);
        int best = 0;
        for (int i = 1; i < all.size(); i++) {
            if (Math.abs(all.get(i) - spacing) < Math.abs(all.get(best) - spacing)) best = i;
        }
        return best;
    }

    private static Integer parseSize(Object value) {
        if (value == null) return null;
        try {
            int points = Integer.parseInt(value.toString().strip());
            return points >= 1 && points <= 1638 ? points : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double round(double value) {
        double factor = Math.pow(10, unit.decimals());
        return Math.round(value * factor) / factor;
    }

    private JSpinner length() {
        double step = unit == MeasurementUnit.INCH ? 0.125 : 0.25;
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, unit.toUnit(144 * 10), step));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.00"));
        return spinner;
    }

    private static JSpinner points() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1584.0, 1.0));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0 'pt'"));
        return spinner;
    }

    private static double number(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    private JPanel section(String key) {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(BorderFactory.createTitledBorder(msg.get(key)));
        return section;
    }

    private static void row(JPanel target, int y, String text, JComponent field) {
        JLabel label = new JLabel(text);
        label.setLabelFor(field);
        GridBagConstraints c = constraints(0, y);
        c.anchor = GridBagConstraints.LINE_START;
        c.weightx = 1;
        target.add(label, c);
        c = constraints(1, y);
        c.anchor = GridBagConstraints.LINE_END;
        target.add(field, c);
    }

    private static GridBagConstraints constraints(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.insets = new Insets(3, 4, 3, 4);
        return c;
    }
}
