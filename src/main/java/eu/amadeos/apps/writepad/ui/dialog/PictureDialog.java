package eu.amadeos.apps.writepad.ui.dialog;

import eu.amadeos.apps.writepad.domain.MeasurementUnit;
import eu.amadeos.apps.writepad.domain.Picture;
import eu.amadeos.apps.writepad.ui.editor.PictureEditing.Layout;
import eu.amadeos.apps.writepad.ui.editor.PictureEditing.Position;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.text.StyleConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Format → Picture (or a double click on a picture): size with or without its proportions, where it sits
 * (in the line, on a line of its own, or at a margin with the text flowing beside it), a border and the
 * description that screen readers read and that shows when the picture cannot be displayed.
 */
public final class PictureDialog {
    private static final int THUMBNAIL = 180;
    private static final int[] LINE_ALIGNMENTS = {StyleConstants.ALIGN_LEFT, StyleConstants.ALIGN_CENTER,
            StyleConstants.ALIGN_RIGHT};
    private static final int[] FLOAT_SIDES = {StyleConstants.ALIGN_LEFT, StyleConstants.ALIGN_RIGHT};

    /** What the dialog edits: the picture (size, border, description, maybe another image) and its layout. */
    public record Values(Picture picture, Layout layout) {
    }

    private final Messages msg;
    private final MeasurementUnit unit;
    private final double maxWidth;
    private final Supplier<Optional<Picture>> replacement;
    private Picture picture;
    private boolean updating;

    private final JLabel thumbnail = new JLabel();
    private final JLabel fileName = new JLabel();
    private final JLabel fileInfo = new JLabel();
    private final JSpinner width;
    private final JSpinner height;
    private final JCheckBox keepProportions;
    private final JRadioButton inLine;
    private final JRadioButton ownLine;
    private final JRadioButton floating;
    private final JComboBox<String> lineAlignment = new JComboBox<>();
    private final JComboBox<String> floatSide = new JComboBox<>();
    private final JComboBox<String> border = new JComboBox<>();
    private final JTextField description = new JTextField(30);
    private final JPanel panel = new JPanel(new BorderLayout(10, 8));

    /**
     * @param maxWidth    width of the text column in points, for "Fit to Width"
     * @param replacement asks for another image file; empty if cancelled
     */
    public PictureDialog(Messages msg, MeasurementUnit unit, Picture current, Layout layout, double maxWidth,
                         Supplier<Optional<Picture>> replacement) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.unit = unit;
        this.maxWidth = maxWidth;
        this.replacement = replacement;
        this.picture = current;

        width = length();
        height = length();
        width.setName("width");
        height.setName("height");
        keepProportions = new JCheckBox(msg.get("dialog.picture.keep-proportions"), true);
        keepProportions.setName("keep-proportions");
        inLine = new JRadioButton(msg.get("dialog.picture.in-line"));
        ownLine = new JRadioButton(msg.get("dialog.picture.own-line"));
        floating = new JRadioButton(msg.get("dialog.picture.floating"));
        inLine.setName("in-line");
        ownLine.setName("own-line");
        floating.setName("floating");
        ButtonGroup positions = new ButtonGroup();
        positions.add(inLine);
        positions.add(ownLine);
        positions.add(floating);
        for (String key : new String[]{"align.left", "align.center", "align.right"}) lineAlignment.addItem(msg.get(key));
        for (String key : new String[]{"align.left", "align.right"}) floatSide.addItem(msg.get(key));
        for (String key : new String[]{"dialog.picture.border.none", "dialog.picture.border.thin",
                "dialog.picture.border.thick"}) border.addItem(msg.get(key));
        lineAlignment.setName("line-alignment");
        floatSide.setName("float-side");
        border.setName("border");
        description.setName("description");

        panel.add(imageColumn(), BorderLayout.WEST);
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        JButton original = new JButton(msg.get("dialog.picture.original-size"));
        original.addActionListener(e -> setSize(picture.naturalWidth(), picture.naturalHeight()));
        JButton fit = new JButton(msg.get("dialog.picture.fit-width"));
        fit.addActionListener(e -> setSize(maxWidth, maxWidth * picture.aspect()));
        right.add(group("dialog.picture.size",
                row(new JLabel(msg.format("dialog.picture.width", unit.symbol())), width, Box.createHorizontalStrut(8),
                        new JLabel(msg.format("dialog.picture.height", unit.symbol())), height),
                row(keepProportions, Box.createHorizontalStrut(8), original, fit)));
        right.add(group("dialog.picture.position", row(inLine), row(ownLine, lineAlignment), row(floating, floatSide)));
        right.add(group("dialog.picture.border", row(new JLabel(msg.get("dialog.picture.line")), border)));
        JLabel hint = new JLabel(msg.get("dialog.picture.description-hint"));
        hint.setForeground(AmadeUI.theme().textInactive());
        right.add(group("dialog.picture.description", row(description), row(hint)));
        panel.add(right, BorderLayout.CENTER);

        width.addChangeListener(e -> sizeChanged(true));
        height.addChangeListener(e -> sizeChanged(false));
        lineAlignment.addActionListener(e -> ownLine.setSelected(true));
        floatSide.addActionListener(e -> floating.setSelected(true));
        show(current, layout);
    }

    private JComponent imageColumn() {
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        thumbnail.setHorizontalAlignment(SwingConstants.CENTER);
        thumbnail.setPreferredSize(new Dimension(THUMBNAIL, THUMBNAIL * 2 / 3));
        thumbnail.setBorder(BorderFactory.createLineBorder(AmadeUI.theme().shadow()));
        for (JComponent c : new JComponent[]{thumbnail, fileName, fileInfo}) {
            c.setAlignmentX(0);
            left.add(c);
        }
        fileInfo.setForeground(AmadeUI.theme().textInactive());
        left.add(Box.createVerticalStrut(6));
        JButton replace = new JButton(msg.get("dialog.picture.replace"));
        replace.setName("replace");
        replace.setAlignmentX(0);
        replace.addActionListener(e -> replacement.get().ifPresent(other -> {
            picture = picture.replacedBy(other);
            showImage();
            setSize(picture.width(), picture.height());
        }));
        left.add(replace);
        return left;
    }

    public Optional<Values> showDialog(Component parent) {
        int answer = JOptionPane.showConfirmDialog(parent, panel, msg.get("dialog.picture.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return answer == JOptionPane.OK_OPTION ? Optional.of(values()) : Optional.empty();
    }

    /** The values entered. */
    public Values values() {
        Picture.Border[] borders = Picture.Border.values();
        Picture result = picture.withSize(unit.toPoints(number(width)), unit.toPoints(number(height)))
                .withBorder(borders[Math.max(0, border.getSelectedIndex())])
                .withDescription(description.getText().strip());
        Layout layout;
        if (floating.isSelected()) {
            layout = new Layout(Position.FLOATING, FLOAT_SIDES[Math.max(0, floatSide.getSelectedIndex())]);
        } else if (ownLine.isSelected()) {
            layout = new Layout(Position.OWN_LINE, LINE_ALIGNMENTS[Math.max(0, lineAlignment.getSelectedIndex())]);
        } else {
            layout = new Layout(Position.IN_LINE, StyleConstants.ALIGN_LEFT);
        }
        return new Values(result, layout);
    }

    JPanel panel() {
        return panel;
    }

    private void show(Picture p, Layout layout) {
        showImage();
        setSize(p.width(), p.height());
        switch (layout.position()) {
            case IN_LINE -> inLine.setSelected(true);
            case OWN_LINE -> {
                for (int i = 0; i < LINE_ALIGNMENTS.length; i++) {
                    if (LINE_ALIGNMENTS[i] == layout.alignment()) lineAlignment.setSelectedIndex(i);
                }
                ownLine.setSelected(true);
            }
            case FLOATING -> {
                floatSide.setSelectedIndex(layout.alignment() == StyleConstants.ALIGN_RIGHT ? 1 : 0);
                floating.setSelected(true);
            }
        }
        border.setSelectedIndex(p.border().ordinal());
        description.setText(p.description());
    }

    private void showImage() {
        BufferedImage image = picture.image();
        if (image != null) {
            double scale = Math.min((double) THUMBNAIL / image.getWidth(), THUMBNAIL * 2.0 / 3 / image.getHeight());
            int w = Math.max(1, (int) (image.getWidth() * scale));
            int h = Math.max(1, (int) (image.getHeight() * scale));
            thumbnail.setIcon(new ImageIcon(image.getScaledInstance(w, h, Image.SCALE_SMOOTH)));
        }
        fileName.setText(picture.name());
        fileInfo.setText(msg.format("dialog.picture.info", picture.pixelWidth(), picture.pixelHeight(),
                Math.max(1, Math.round(picture.data().length / 1024.0))));
    }

    private void setSize(double widthPoints, double heightPoints) {
        updating = true;
        try {
            width.setValue(round(unit.toUnit(widthPoints)));
            height.setValue(round(unit.toUnit(heightPoints)));
        } finally {
            updating = false;
        }
    }

    /** With proportions kept, the other side follows the one changed. */
    private void sizeChanged(boolean widthChanged) {
        if (updating || !keepProportions.isSelected()) return;
        double aspect = picture.aspect();
        updating = true;
        try {
            if (widthChanged) height.setValue(round(number(width) * aspect));
            else width.setValue(round(number(height) / aspect));
        } finally {
            updating = false;
        }
    }

    private double round(double value) {
        double factor = Math.pow(10, Math.max(1, unit.decimals()));
        return Math.round(value * factor) / factor;
    }

    private JSpinner length() {
        double step = 0.1;
        double max = unit.toUnit(144 * 10);
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(1.0, unit.toUnit(2), max, step));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.0#"));
        return spinner;
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

    private static JPanel row(Component... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 2));
        for (Component c : components) row.add(c);
        return row;
    }
}
