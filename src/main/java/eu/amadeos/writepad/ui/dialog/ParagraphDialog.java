package eu.amadeos.writepad.ui.dialog;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.writepad.domain.MeasurementUnit;
import eu.amadeos.writepad.ui.editor.TextFormatting.Indents;
import eu.amadeos.writepad.ui.editor.TextFormatting.Spacing;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.StyleConstants;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Optional;

/**
 * Format → Paragraph, as in WordPad: indents in the ruler's unit, spacing before and after in points, line
 * spacing and alignment.
 */
public final class ParagraphDialog {
    /** Line spacings offered: single, 1.15 (Word's default), one and a half, double, triple. */
    public static final List<Float> LINE_SPACINGS = List.of(1f, 1.15f, 1.5f, 2f, 3f);
    private static final int[] ALIGNMENTS = {StyleConstants.ALIGN_LEFT, StyleConstants.ALIGN_CENTER,
            StyleConstants.ALIGN_RIGHT, StyleConstants.ALIGN_JUSTIFIED};

    /** What the dialog edits. */
    public record Values(Indents indents, Spacing spacing, int alignment) {
    }

    private final Messages msg;
    private final MeasurementUnit unit;
    private final JSpinner left;
    private final JSpinner right;
    private final JSpinner firstLine;
    private final JSpinner before = points();
    private final JSpinner after = points();
    private final JComboBox<String> lines = new JComboBox<>();
    private final JComboBox<String> alignment = new JComboBox<>();
    private final JPanel panel = new JPanel(new GridBagLayout());

    public ParagraphDialog(Messages msg, MeasurementUnit unit, Values current) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.unit = unit;
        left = length(0);
        right = length(0);
        firstLine = length(-unit.toUnit(144 * 10));
        left.setName("left");
        right.setName("right");
        firstLine.setName("first-line");
        before.setName("before");
        after.setName("after");
        lines.setName("line-spacing");
        alignment.setName("alignment");
        for (float spacing : LINE_SPACINGS) lines.addItem(lineLabel(spacing));
        for (String key : new String[]{"align.left", "align.center", "align.right", "align.justify"}) {
            alignment.addItem(msg.get(key));
        }

        JPanel indents = section("dialog.paragraph.indentation");
        row(indents, 0, msg.format("dialog.paragraph.left", unit.symbol()), left);
        row(indents, 1, msg.format("dialog.paragraph.right", unit.symbol()), right);
        row(indents, 2, msg.format("dialog.paragraph.first-line", unit.symbol()), firstLine);
        JPanel spacing = section("dialog.paragraph.spacing");
        row(spacing, 0, msg.get("dialog.paragraph.before"), before);
        row(spacing, 1, msg.get("dialog.paragraph.after"), after);
        row(spacing, 2, msg.get("dialog.paragraph.line-spacing"), lines);
        GridBagConstraints c = constraints(0, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(indents, c);
        c = constraints(0, 1);
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(spacing, c);
        JPanel align = new JPanel(new GridBagLayout());
        row(align, 0, msg.get("dialog.paragraph.alignment"), alignment);
        c = constraints(0, 2);
        c.anchor = GridBagConstraints.LINE_START;
        panel.add(align, c);
        show(current);
    }

    public Optional<Values> showDialog(Component parent) {
        int answer = JOptionPane.showConfirmDialog(parent, panel, msg.get("dialog.paragraph.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return answer == JOptionPane.OK_OPTION ? Optional.of(values()) : Optional.empty();
    }

    /** The values entered; the first line's indent is relative to the left indent, as in WordPad. */
    public Values values() {
        float leftPoints = (float) unit.toPoints(number(left));
        float firstPoints = leftPoints + (float) unit.toPoints(number(firstLine));
        Indents indents = new Indents(leftPoints, Math.max(0, firstPoints), (float) unit.toPoints(number(right)));
        Spacing spacing = new Spacing((float) number(before), (float) number(after),
                LINE_SPACINGS.get(Math.max(0, lines.getSelectedIndex())));
        return new Values(indents, spacing, ALIGNMENTS[Math.max(0, alignment.getSelectedIndex())]);
    }

    JPanel panel() {
        return panel;
    }

    private void show(Values values) {
        Indents indents = values.indents();
        left.setValue(round(unit.toUnit(indents.left())));
        right.setValue(round(unit.toUnit(indents.right())));
        firstLine.setValue(round(unit.toUnit(indents.firstLineOffset())));
        before.setValue((double) Math.round(values.spacing().before()));
        after.setValue((double) Math.round(values.spacing().after()));
        lines.setSelectedIndex(nearest(values.spacing().lines()));
        for (int i = 0; i < ALIGNMENTS.length; i++) {
            if (ALIGNMENTS[i] == values.alignment()) alignment.setSelectedIndex(i);
        }
    }

    private String lineLabel(float spacing) {
        if (spacing == 1f) return msg.get("dialog.paragraph.single");
        if (spacing == 2f) return msg.get("dialog.paragraph.double");
        return msg.format("dialog.paragraph.lines", (double) spacing);
    }

    private static int nearest(float spacing) {
        int best = 0;
        for (int i = 1; i < LINE_SPACINGS.size(); i++) {
            if (Math.abs(LINE_SPACINGS.get(i) - spacing) < Math.abs(LINE_SPACINGS.get(best) - spacing)) best = i;
        }
        return best;
    }

    private double round(double value) {
        double factor = Math.pow(10, unit.decimals());
        return Math.round(value * factor) / factor;
    }

    private JSpinner length(double minimum) {
        double step = unit == MeasurementUnit.INCH ? 0.125 : 0.25;
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(0.0, minimum, unit.toUnit(144 * 10), step));
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
