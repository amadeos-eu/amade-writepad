package eu.amadeos.writepad.ui.dialog;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.writepad.domain.DocumentSettings;
import eu.amadeos.writepad.domain.Margins;
import eu.amadeos.writepad.domain.Orientation;
import eu.amadeos.writepad.domain.PaperSize;
import eu.amadeos.writepad.domain.Units;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Optional;

/**
 * Paper size, orientation and margins (in millimetres).
 */
public final class PageSetupDialog {
    private final JComboBox<PaperSize> paper = new JComboBox<>(PaperSize.values());
    private final JRadioButton portrait;
    private final JRadioButton landscape;
    private final JSpinner top = millimetres();
    private final JSpinner left = millimetres();
    private final JSpinner bottom = millimetres();
    private final JSpinner right = millimetres();
    private final JPanel panel = new JPanel(new GridBagLayout());
    private final Messages msg;

    public PageSetupDialog(Messages msg, DocumentSettings current) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        portrait = new JRadioButton(msg.get("orientation.portrait"));
        landscape = new JRadioButton(msg.get("orientation.landscape"));
        ButtonGroup group = new ButtonGroup();
        group.add(portrait);
        group.add(landscape);
        paper.setName("paper");
        portrait.setName("portrait");
        landscape.setName("landscape");
        show(current);

        JPanel orientation = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        orientation.add(portrait);
        orientation.add(landscape);
        JPanel margins = new JPanel(new GridBagLayout());
        margins.setBorder(BorderFactory.createTitledBorder(msg.get("dialog.page-setup.margins")));
        row(margins, 0, "dialog.page-setup.top", top);
        row(margins, 1, "dialog.page-setup.bottom", bottom);
        row(margins, 2, "dialog.page-setup.left", left);
        row(margins, 3, "dialog.page-setup.right", right);
        row(panel, 0, "dialog.page-setup.paper", paper);
        row(panel, 1, "dialog.page-setup.orientation", orientation);
        GridBagConstraints c = constraints(0, 2);
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(margins, c);
    }

    /** Shows the dialog until the input is valid or cancelled. */
    public Optional<DocumentSettings> showDialog(Component parent) {
        while (JOptionPane.showConfirmDialog(parent, panel, msg.get("dialog.page-setup.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            Optional<DocumentSettings> settings = settings();
            if (settings.isPresent()) return settings;
            JOptionPane.showMessageDialog(parent, msg.get("dialog.page-setup.invalid"),
                    msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
        }
        return Optional.empty();
    }

    /** The settings entered, empty if the margins leave no room for text. */
    public Optional<DocumentSettings> settings() {
        try {
            return Optional.of(new DocumentSettings((PaperSize) paper.getSelectedItem(),
                    landscape.isSelected() ? Orientation.LANDSCAPE : Orientation.PORTRAIT,
                    Margins.ofMm(mm(top), mm(left), mm(bottom), mm(right))));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** For tests: the panel with the fields. */
    JPanel panel() {
        return panel;
    }

    private void show(DocumentSettings settings) {
        paper.setSelectedItem(settings.paper());
        (settings.orientation() == Orientation.LANDSCAPE ? landscape : portrait).setSelected(true);
        Margins m = settings.margins();
        top.setValue(round(m.top()));
        left.setValue(round(m.left()));
        bottom.setValue(round(m.bottom()));
        right.setValue(round(m.right()));
    }

    private static double round(double points) {
        return Math.round(Units.pointsToMm(points) * 10) / 10.0;
    }

    private static double mm(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    private static JSpinner millimetres() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(25.0, 0.0, 200.0, 1.0));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.0"));
        return spinner;
    }

    private void row(JPanel target, int y, String key, JComponent field) {
        JLabel label = new JLabel(msg.get(key));
        label.setLabelFor(field);
        GridBagConstraints c = constraints(0, y);
        c.anchor = GridBagConstraints.LINE_START;
        target.add(label, c);
        c = constraints(1, y);
        c.anchor = GridBagConstraints.LINE_START;
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
