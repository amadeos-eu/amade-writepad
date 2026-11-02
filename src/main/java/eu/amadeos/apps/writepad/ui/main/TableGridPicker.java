package eu.amadeos.apps.writepad.ui.main;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;

/**
 * The drop-down of the toolbar's table button: a grid of cells; moving the mouse over it marks a table size
 * ("4 × 3"), a click inserts a table of that size. "More..." opens the Insert Table dialog.
 */
final class TableGridPicker extends JPopupMenu {
    static final int COLUMNS = 10;
    static final int ROWS = 8;
    private static final int CELL = 16;

    private final Grid grid = new Grid();
    private final JLabel size = new JLabel(" ", SwingConstants.CENTER);
    private final Messages msg;
    private int columns;
    private int rows;

    /**
     * @param insert gets columns and rows of the chosen table
     * @param more   opens the dialog
     */
    TableGridPicker(Messages msg, BiConsumer<Integer, Integer> insert, Runnable more) {
        this.msg = msg;
        setName("table-grid");
        add(grid);
        size.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(size);
        addSeparator();
        JMenuItem dialog = new JMenuItem(msg.get("menu.table.more"));
        dialog.setName("table-grid-more");
        dialog.addActionListener(e -> more.run());
        add(dialog);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mark(Math.min(COLUMNS, e.getX() / CELL + 1), Math.min(ROWS, e.getY() / CELL + 1));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mark(0, 0);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (columns > 0 && rows > 0) {
                    int c = columns;
                    int r = rows;
                    setVisible(false);
                    insert.accept(c, r);
                }
            }
        };
        grid.addMouseListener(mouse);
        grid.addMouseMotionListener(mouse);
    }

    /** Shows the grid below a component, nothing marked. */
    void showBelow(Component c) {
        mark(0, 0);
        show(c, 0, c.getHeight());
    }

    void mark(int c, int r) {
        columns = c;
        rows = r;
        size.setText(c > 0 ? msg.format("menu.table.size", c, r) : msg.get("menu.table.choose-size"));
        grid.repaint();
    }

    int markedColumns() {
        return columns;
    }

    int markedRows() {
        return rows;
    }

    private final class Grid extends JComponent {
        Grid() {
            setPreferredSize(new Dimension(COLUMNS * CELL + 1, ROWS * CELL + 1));
            setBorder(BorderFactory.createEmptyBorder());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Color selected = AmadeUI.theme().activeTitleBg();
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLUMNS; c++) {
                    boolean on = c < columns && r < rows;
                    g.setColor(on ? selected : Color.WHITE);
                    g.fillRect(c * CELL + 1, r * CELL + 1, CELL - 2, CELL - 2);
                    g.setColor(AmadeUI.theme().shadow());
                    g.drawRect(c * CELL + 1, r * CELL + 1, CELL - 2, CELL - 2);
                }
            }
        }
    }
}
