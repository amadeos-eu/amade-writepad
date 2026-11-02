package eu.amadeos.apps.writepad.ui.dialog;

import eu.amadeos.apps.writepad.domain.MeasurementUnit;
import eu.amadeos.apps.writepad.domain.TableFormat;
import eu.amadeos.apps.writepad.service.table.TableAttributes.VerticalAlignment;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Table → Table Properties: three tabs. Table: width, alignment, the heading row, the column widths
 * ("Equal Widths" shares the width out evenly). Lines: which lines, their width and colour, and the space
 * between the lines and the text. Cells: background and vertical alignment of the selected cells.
 */
public final class TablePropertiesDialog {
    /** The table's format and what the cells get; a cell value is null if it was not changed. */
    public record Values(TableFormat format, Optional<Color> background, boolean backgroundChanged,
                         VerticalAlignment verticalAlignment) {
    }

    /** What the dialog starts from: the table's format and the format of the cell at the caret. */
    public record Current(TableFormat format, Color background, VerticalAlignment verticalAlignment) {
    }

    private final Messages msg;
    private final MeasurementUnit unit;
    private final double contentWidth;
    private final Current current;
    private final JSpinner width;
    private final JComboBox<String> alignment = new JComboBox<>();
    private final JCheckBox repeat;
    private final List<JSpinner> columns = new ArrayList<>();
    private final JComboBox<String> lines = new JComboBox<>();
    private final JSpinner lineWidth = new JSpinner(new SpinnerNumberModel(0.5, 0.0, 9.0, 0.25));
    private final JButton lineColor = new JButton();
    private Color chosenLineColor;
    private final JSpinner padding;
    private final ColorPalette background;
    private boolean backgroundChanged;
    private final JComboBox<String> vertical = new JComboBox<>();
    private final JTabbedPane tabs = new JTabbedPane();
    private boolean updating;

    public TablePropertiesDialog(Messages msg, MeasurementUnit unit, Current current, double contentWidth) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.unit = unit;
        this.contentWidth = contentWidth;
        this.current = current;
        TableFormat f = current.format();

        // Table
        width = lengthSpinner(unit, Math.min(f.totalWidth(), contentWidth), contentWidth);
        width.setName("width");
        for (String key : new String[]{"align.left", "align.center", "align.right"}) alignment.addItem(msg.get(key));
        alignment.setSelectedIndex(f.alignment().ordinal());
        alignment.setName("alignment");
        repeat = new JCheckBox(msg.get("dialog.table.repeat-header"), f.repeatHeader());
        repeat.setName("repeat-header");
        JPanel columnRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 2));
        for (int c = 0; c < f.columns(); c++) {
            JSpinner s = lengthSpinner(unit, f.width(c), contentWidth);
            s.setName("column-" + (c + 1));
            columns.add(s);
            columnRow.add(new JLabel(Integer.toString(c + 1)));
            columnRow.add(s);
            s.addChangeListener(e -> columnsChanged());
        }
        JScrollPane columnScroll = new JScrollPane(columnRow, JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        columnScroll.setBorder(BorderFactory.createEmptyBorder());
        columnScroll.setPreferredSize(new Dimension(420, columnRow.getPreferredSize().height + 18));
        width.addChangeListener(e -> widthChanged());
        JButton equal = new JButton(msg.get("dialog.table.equal-widths"));
        equal.setName("equal-widths");
        equal.addActionListener(e -> equalWidths());
        JPanel table = page(
                group("dialog.table.size", InsertTableDialog.row(new JLabel(msg.format("dialog.table.width", unit.symbol())),
                        width, new JLabel(msg.get("dialog.table.alignment")), alignment)),
                group("dialog.table.heading", InsertTableDialog.row(repeat)),
                group("dialog.table.columns-group", columnScroll, InsertTableDialog.row(equal)));

        // Lines
        fillLines(msg, lines);
        lines.setSelectedIndex(f.lines().ordinal());
        lines.setName("lines");
        lineWidth.setValue((double) f.lineWidth());
        lineWidth.setName("line-width");
        JSpinner.NumberEditor widthEditor = new JSpinner.NumberEditor(lineWidth, "0.0#");
        widthEditor.getTextField().setColumns(4);
        lineWidth.setEditor(widthEditor);
        chosenLineColor = f.lineColor();
        lineColor.setName("line-color");
        showLineColor();
        lineColor.addActionListener(e -> chooseLineColor());
        padding = lengthSpinner(unit, f.padding(), 0, 72);
        padding.setName("padding");
        JPanel linesPage = page(
                group("dialog.table.lines-group", InsertTableDialog.row(new JLabel(msg.get("dialog.table.lines")), lines),
                        InsertTableDialog.row(new JLabel(msg.get("dialog.table.line-width")), lineWidth,
                                new JLabel(msg.get("dialog.table.line-color")), lineColor)),
                group("dialog.table.padding-group", InsertTableDialog.row(
                        new JLabel(msg.format("dialog.table.padding", unit.symbol())), padding)));

        // Cells
        background = new ColorPalette(msg, true, current.background());
        background.setName("background");
        background.onChoose(c -> backgroundChanged = true);
        for (String key : new String[]{"dialog.table.valign.top", "dialog.table.valign.center",
                "dialog.table.valign.bottom"}) {
            vertical.addItem(msg.get(key));
        }
        vertical.setSelectedIndex(current.verticalAlignment().ordinal());
        vertical.setName("vertical-alignment");
        JLabel hint = new JLabel(msg.get("dialog.table.cells-hint"));
        JPanel cells = page(group("dialog.table.background", background),
                group("dialog.table.vertical", InsertTableDialog.row(vertical)), InsertTableDialog.row(hint));

        tabs.addTab(msg.get("dialog.table.tab.table"), table);
        tabs.addTab(msg.get("dialog.table.tab.lines"), linesPage);
        tabs.addTab(msg.get("dialog.table.tab.cells"), cells);
        tabs.setName("tabs");
    }

    /** "Around every cell", "Around the table", "Between the rows", "None", in the order of the enum. */
    static void fillLines(Messages msg, JComboBox<String> box) {
        for (TableFormat.Lines l : TableFormat.Lines.values()) {
            box.addItem(msg.get("dialog.table.lines." + l.name().toLowerCase(java.util.Locale.ROOT)));
        }
    }

    /** A spinner for a length in the user's unit, starting at the given points (a column's width at least). */
    static JSpinner lengthSpinner(MeasurementUnit unit, double points, double maxPoints) {
        return lengthSpinner(unit, points, TableFormat.MIN_COLUMN / 4, maxPoints);
    }

    /** A spinner for a length in the user's unit, starting at the given points, kept within min and max. */
    static JSpinner lengthSpinner(MeasurementUnit unit, double points, double minPoints, double maxPoints) {
        double min = unit.toUnit(minPoints);
        double max = Math.max(unit.toUnit(maxPoints), unit.toUnit(points));
        double value = Math.max(min, Math.min(round(unit, unit.toUnit(points)), max));
        JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, unit.decimals() > 0 ? 0.1 : 1.0));
        s.setEditor(new JSpinner.NumberEditor(s, "0.0#"));
        return s;
    }

    private static double round(MeasurementUnit unit, double value) {
        double factor = Math.pow(10, Math.max(1, unit.decimals()));
        return Math.round(value * factor) / factor;
    }

    public Optional<Values> showDialog(Component parent) {
        int answer = JOptionPane.showConfirmDialog(parent, tabs, msg.get("dialog.table.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return answer == JOptionPane.OK_OPTION ? Optional.of(values()) : Optional.empty();
    }

    /** The values entered. */
    public Values values() {
        double[] w = new double[columns.size()];
        for (int i = 0; i < w.length; i++) w[i] = unit.toPoints(number(columns.get(i)));
        TableFormat format = current.format().withWidths(w)
                .withAlignment(TableFormat.Alignment.values()[Math.max(0, alignment.getSelectedIndex())])
                .withRepeatHeader(repeat.isSelected())
                .withLines(TableFormat.Lines.values()[Math.max(0, lines.getSelectedIndex())])
                .withLineWidth((float) number(lineWidth))
                .withLineColor(chosenLineColor)
                .withPadding(unit.toPoints(number(padding)));
        VerticalAlignment v = VerticalAlignment.values()[Math.max(0, vertical.getSelectedIndex())];
        return new Values(format, Optional.ofNullable(background.color()), backgroundChanged,
                v == current.verticalAlignment() ? null : v);
    }

    JTabbedPane panel() {
        return tabs;
    }

    /** The table width follows the columns. */
    private void columnsChanged() {
        if (updating) return;
        updating = true;
        try {
            double sum = 0;
            for (JSpinner s : columns) sum += number(s);
            width.setValue(round(unit, Math.min(sum, unit.toUnit(contentWidth))));
        } finally {
            updating = false;
        }
    }

    /** The columns keep their proportions when the table width changes. */
    private void widthChanged() {
        if (updating) return;
        double sum = 0;
        for (JSpinner s : columns) sum += number(s);
        double scale = number(width) / Math.max(1e-6, sum);
        updating = true;
        try {
            for (JSpinner s : columns) s.setValue(round(unit, number(s) * scale));
        } finally {
            updating = false;
        }
    }

    private void equalWidths() {
        double each = number(width) / columns.size();
        updating = true;
        try {
            for (JSpinner s : columns) s.setValue(round(unit, each));
        } finally {
            updating = false;
        }
    }

    private void chooseLineColor() {
        JPopupMenu popup = new JPopupMenu();
        ColorPalette palette = new ColorPalette(msg, false, chosenLineColor);
        palette.onChoose(c -> {
            chosenLineColor = c;
            showLineColor();
            popup.setVisible(false);
        });
        popup.add(palette);
        popup.show(lineColor, 0, lineColor.getHeight());
    }

    private void showLineColor() {
        lineColor.setIcon(new ColorPalette.Swatch(chosenLineColor, 16));
        lineColor.setText(msg.get("dialog.table.choose"));
    }

    private static double number(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    private JPanel group(String key, JComponent... rows) {
        JPanel group = new JPanel();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setBorder(BorderFactory.createTitledBorder(msg.get(key)));
        for (JComponent r : rows) {
            r.setAlignmentX(0);
            group.add(r);
        }
        group.setAlignmentX(0);
        return group;
    }

    private static JPanel page(JComponent... groups) {
        JPanel page = new JPanel();
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        for (JComponent g : groups) {
            g.setAlignmentX(0);
            page.add(g);
        }
        return page;
    }
}
