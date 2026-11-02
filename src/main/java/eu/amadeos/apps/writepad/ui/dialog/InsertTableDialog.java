package eu.amadeos.apps.writepad.ui.dialog;

import eu.amadeos.apps.writepad.domain.MeasurementUnit;
import eu.amadeos.apps.writepad.domain.TableFormat;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.ButtonGroup;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.Optional;

/**
 * Insert → Table (Ctrl+F12): columns and rows, whether the first row is a heading (and repeats on every page),
 * the lines, and the width: the whole text column or a fixed width.
 */
public final class InsertTableDialog {
    public static final int MAX_COLUMNS = 64;
    public static final int MAX_ROWS = 999;

    /** What the dialog asks for. {@code format} carries columns, width, lines and the repeat flag. */
    public record Values(TableFormat format, int rows, boolean header) {
    }

    private final Messages msg;
    private final MeasurementUnit unit;
    private final double contentWidth;
    private final JSpinner columns = new JSpinner(new SpinnerNumberModel(3, 1, MAX_COLUMNS, 1));
    private final JSpinner rows = new JSpinner(new SpinnerNumberModel(3, 1, MAX_ROWS, 1));
    private final JCheckBox header;
    private final JCheckBox repeat;
    private final JComboBox<String> lines = new JComboBox<>();
    private final JRadioButton fullWidth;
    private final JRadioButton fixedWidth;
    private final JSpinner width;
    private final JPanel panel = new JPanel();

    /** @param contentWidth width of the text column in points */
    public InsertTableDialog(Messages msg, MeasurementUnit unit, double contentWidth) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.unit = unit;
        this.contentWidth = contentWidth;
        columns.setName("columns");
        rows.setName("rows");
        header = new JCheckBox(msg.get("dialog.table.header"), true);
        header.setName("header");
        repeat = new JCheckBox(msg.get("dialog.table.repeat-header"), true);
        repeat.setName("repeat-header");
        header.addActionListener(e -> repeat.setEnabled(header.isSelected()));
        TablePropertiesDialog.fillLines(msg, lines);
        lines.setName("lines");
        fullWidth = new JRadioButton(msg.get("dialog.table.full-width"), true);
        fixedWidth = new JRadioButton(msg.get("dialog.table.fixed-width"));
        fullWidth.setName("full-width");
        fixedWidth.setName("fixed-width");
        ButtonGroup g = new ButtonGroup();
        g.add(fullWidth);
        g.add(fixedWidth);
        width = TablePropertiesDialog.lengthSpinner(unit, contentWidth, contentWidth);
        width.setName("width");
        width.setEnabled(false);
        fixedWidth.addItemListener(e -> width.setEnabled(fixedWidth.isSelected()));

        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(group("dialog.table.size",
                row(new JLabel(msg.get("dialog.table.columns")), columns, new JLabel(msg.get("dialog.table.rows")), rows)));
        panel.add(group("dialog.table.heading", row(header), row(repeat)));
        panel.add(group("dialog.table.look", row(new JLabel(msg.get("dialog.table.lines")), lines),
                row(fullWidth), row(fixedWidth, width, new JLabel(unit.symbol()))));
    }

    public Optional<Values> showDialog(Component parent) {
        int answer = JOptionPane.showConfirmDialog(parent, panel, msg.get("dialog.insert-table.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return answer == JOptionPane.OK_OPTION ? Optional.of(values()) : Optional.empty();
    }

    /** The values entered. */
    public Values values() {
        double total = fixedWidth.isSelected()
                ? Math.min(contentWidth, unit.toPoints(((Number) width.getValue()).doubleValue())) : contentWidth;
        int cols = (Integer) columns.getValue();
        TableFormat format = TableFormat.of(cols, total)
                .withLines(TableFormat.Lines.values()[Math.max(0, lines.getSelectedIndex())])
                .withRepeatHeader(header.isSelected() && repeat.isSelected());
        return new Values(format, (Integer) rows.getValue(), header.isSelected());
    }

    JPanel panel() {
        return panel;
    }

    private JPanel group(String key, JComponent... rowsOfGroup) {
        JPanel group = new JPanel();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setBorder(BorderFactory.createTitledBorder(msg.get(key)));
        for (JComponent r : rowsOfGroup) {
            r.setAlignmentX(0);
            group.add(r);
        }
        group.setAlignmentX(0);
        return group;
    }

    static JPanel row(Component... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 2));
        for (Component c : components) row.add(c);
        return row;
    }
}
