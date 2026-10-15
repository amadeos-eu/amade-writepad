package org.datazuul.amadeos.writepad.ui.dialog;

import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.commons.swing.EdtExecutor;
import org.datazuul.amadeos.writepad.domain.MeasurementUnit;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.TabStop;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Format → Tabs, as in WordPad: the stops of the paragraph listed by position, a position and alignment to
 * set, and Clear / Clear All.
 */
public final class TabsDialog {
    private static final int[] ALIGNMENTS = {TabStop.ALIGN_LEFT, TabStop.ALIGN_CENTER, TabStop.ALIGN_RIGHT,
            TabStop.ALIGN_DECIMAL};
    private static final String[] ALIGNMENT_KEYS = {"tab.left", "tab.center", "tab.right", "tab.decimal"};

    private final Messages msg;
    private final MeasurementUnit unit;
    private final List<TabStop> tabs = new ArrayList<>();
    private final DefaultListModel<String> listed = new DefaultListModel<>();
    private final JList<String> list = new JList<>(listed);
    private final JSpinner position;
    private final JRadioButton[] alignments = new JRadioButton[ALIGNMENTS.length];
    private final JPanel panel = new JPanel(new BorderLayout(8, 8));
    private final NumberFormat format;

    /**
     * @param maximum the widest position, the text width, in points
     */
    public TabsDialog(Messages msg, MeasurementUnit unit, List<TabStop> current, float maximum) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.unit = unit;
        this.format = NumberFormat.getNumberInstance(msg.locale());
        format.setMinimumFractionDigits(unit.decimals());
        format.setMaximumFractionDigits(unit.decimals());
        tabs.addAll(current);
        double max = Math.floor(unit.toUnit(maximum) * 100) / 100;
        position = new JSpinner(new SpinnerNumberModel(0.0, 0.0, max, unit == MeasurementUnit.INCH ? 0.125 : 0.25));
        position.setEditor(new JSpinner.NumberEditor(position, "0.00"));
        position.setName("position");
        list.setName("tabs");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(6);
        list.addListSelectionListener(e -> {
            int i = list.getSelectedIndex();
            if (i < 0) return;
            TabStop tab = tabs.get(i);
            position.setValue(unit.toUnit(tab.getPosition()));
            for (int k = 0; k < ALIGNMENTS.length; k++) alignments[k].setSelected(ALIGNMENTS[k] == tab.getAlignment());
        });

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(new JLabel(msg.format("dialog.tabs.position", unit.symbol())), BorderLayout.NORTH);
        JPanel field = new JPanel(new BorderLayout(0, 4));
        field.add(position, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(140, 120));
        field.add(scroll, BorderLayout.CENTER);
        left.add(field, BorderLayout.CENTER);

        JPanel alignment = new JPanel(new GridLayout(0, 1));
        alignment.setBorder(BorderFactory.createTitledBorder(msg.get("dialog.tabs.alignment")));
        ButtonGroup group = new ButtonGroup();
        for (int k = 0; k < ALIGNMENTS.length; k++) {
            alignments[k] = new JRadioButton(msg.get(ALIGNMENT_KEYS[k]), k == 0);
            alignments[k].setName("align-" + k);
            group.add(alignments[k]);
            alignment.add(alignments[k]);
        }
        JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 4));
        JButton set = new JButton(msg.get("dialog.tabs.set"));
        set.setName("set");
        set.addActionListener(e -> set());
        JButton clear = new JButton(msg.get("dialog.tabs.clear"));
        clear.setName("clear");
        clear.addActionListener(e -> clear());
        JButton clearAll = new JButton(msg.get("dialog.tabs.clear-all"));
        clearAll.setName("clear-all");
        clearAll.addActionListener(e -> {
            tabs.clear();
            refresh();
        });
        buttons.add(set);
        buttons.add(clear);
        buttons.add(clearAll);
        JPanel right = new JPanel(new BorderLayout(4, 8));
        right.add(alignment, BorderLayout.NORTH);
        right.add(buttons, BorderLayout.SOUTH);
        panel.add(left, BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);
        refresh();
    }

    public Optional<List<TabStop>> showDialog(Component parent) {
        int answer = JOptionPane.showConfirmDialog(parent, panel, msg.get("dialog.tabs.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return answer == JOptionPane.OK_OPTION ? Optional.of(tabs()) : Optional.empty();
    }

    /** The stops, sorted by position. */
    public List<TabStop> tabs() {
        return List.copyOf(tabs);
    }

    /** Adds the stop at the position, or changes the alignment of one already there. */
    void set() {
        float at = (float) unit.toPoints(((Number) position.getValue()).doubleValue());
        int alignment = TabStop.ALIGN_LEFT;
        for (int k = 0; k < ALIGNMENTS.length; k++) if (alignments[k].isSelected()) alignment = ALIGNMENTS[k];
        tabs.removeIf(tab -> Math.abs(tab.getPosition() - at) < 0.5f);
        tabs.add(new TabStop(at, alignment, TabStop.LEAD_NONE));
        refresh();
    }

    /** Removes the stop selected in the list, else the one at the position. */
    void clear() {
        int selected = list.getSelectedIndex();
        if (selected >= 0) {
            tabs.remove(selected);
        } else {
            float at = (float) unit.toPoints(((Number) position.getValue()).doubleValue());
            tabs.removeIf(tab -> Math.abs(tab.getPosition() - at) < 0.5f);
        }
        refresh();
    }

    JSpinner position() {
        return position;
    }

    JList<String> list() {
        return list;
    }

    void selectAlignment(int index) {
        alignments[index].setSelected(true);
    }

    private void refresh() {
        tabs.sort(Comparator.comparingDouble(TabStop::getPosition));
        listed.clear();
        for (TabStop tab : tabs) {
            int k = 0;
            while (k < ALIGNMENTS.length - 1 && ALIGNMENTS[k] != tab.getAlignment()) k++;
            listed.addElement(format.format(unit.toUnit(tab.getPosition())) + " " + unit.symbol() + "  "
                    + msg.get(ALIGNMENT_KEYS[k]));
        }
    }
}
