package eu.amadeos.apps.writepad.ui.dialog;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.ButtonGroup;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A choice of colour: light shades that keep black text readable, as cell backgrounds want them, darker
 * ones for lines, "None" where no colour is a choice, and "Other Colour..." for anything else.
 */
public final class ColorPalette extends JPanel {
    /** Light colours first, then strong ones. */
    static final Color[] COLORS = {
            new Color(0xFFFFFF), new Color(0xF2F2F2), new Color(0xDDDDDD), new Color(0xFFF2CC),
            new Color(0xFCE4D6), new Color(0xF8D7DA), new Color(0xE2EFDA), new Color(0xDDEBF7),
            new Color(0xE4DFEC), new Color(0xD9F2F2), new Color(0x000000), new Color(0x595959),
            new Color(0xA6A6A6), new Color(0xC00000), new Color(0xED7D31), new Color(0xFFC000),
            new Color(0x70AD47), new Color(0x2F6FB8), new Color(0x7030A0), new Color(0x00A0A0)};

    private final List<JToggleButton> swatches = new ArrayList<>();
    private final ButtonGroup group = new ButtonGroup();
    private final JToggleButton none;
    private Color color;
    private Consumer<Color> listener = c -> {
    };

    /**
     * @param allowNone whether "None" (null) is a choice
     * @param current   the colour to mark; null for none
     */
    public ColorPalette(Messages msg, boolean allowNone, Color current) {
        super(new BorderLayout(0, 6));
        JPanel grid = new JPanel(new GridLayout(2, COLORS.length / 2, 3, 3));
        for (Color c : COLORS) {
            JToggleButton b = new JToggleButton(new Swatch(c));
            b.setMargin(new Insets(1, 1, 1, 1));
            b.setFocusPainted(false);
            b.setToolTipText(String.format("#%06X", c.getRGB() & 0xFFFFFF));
            b.setName(String.format("color-%06X", c.getRGB() & 0xFFFFFF));
            b.addActionListener(e -> choose(c));
            group.add(b);
            swatches.add(b);
            grid.add(b);
        }
        add(grid, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new GridLayout(1, 0, 6, 0));
        none = new JToggleButton(msg.get("dialog.color.none"));
        none.setName("color-none");
        none.addActionListener(e -> choose(null));
        group.add(none);
        if (allowNone) buttons.add(none);
        JButton other = new JButton(msg.get("dialog.color.other"));
        other.setName("color-other");
        other.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, msg.get("dialog.color.other-title"),
                    color == null ? Color.WHITE : color);
            if (chosen != null) choose(chosen);
        });
        buttons.add(other);
        add(buttons, BorderLayout.SOUTH);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        select(current);
    }

    /** Runs when a colour is chosen (null for "None"). */
    public void onChoose(Consumer<Color> listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public Color color() {
        return color;
    }

    /** Marks a colour without telling the listener. */
    public void select(Color c) {
        color = c;
        group.clearSelection();
        if (c == null) {
            none.setSelected(true);
            return;
        }
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i].equals(c)) swatches.get(i).setSelected(true);
        }
    }

    private void choose(Color c) {
        select(c);
        listener.accept(c);
    }

    /** A filled square with a frame. */
    static final class Swatch implements Icon {
        private final Color color;
        private final int size;

        Swatch(Color color) {
            this(color, 14);
        }

        Swatch(Color color, int size) {
            this.color = color;
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (color != null) {
                g.setColor(color);
                g.fillRect(x, y, size, size);
            } else {
                g.setColor(Color.WHITE);
                g.fillRect(x, y, size, size);
                g.setColor(new Color(0xC00000));
                g.drawLine(x, y + size - 1, x + size - 1, y);
            }
            g.setColor(AmadeUI.theme().shadow());
            g.drawRect(x, y, size - 1, size - 1);
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
