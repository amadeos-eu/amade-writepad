package eu.amadeos.apps.writepad.ui.dialog;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.JOptionPane;
import java.awt.Color;
import java.awt.Component;
import java.util.Optional;

/** Table → Cell Background: a colour, or none, for the selected cells. */
public final class CellBackgroundDialog {
    /** The choice: a colour, or null for none. */
    public record Choice(Color color) {
    }

    private final Messages msg;
    private final ColorPalette palette;

    public CellBackgroundDialog(Messages msg, Color current) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.palette = new ColorPalette(msg, true, current);
    }

    public Optional<Choice> showDialog(Component parent) {
        int answer = JOptionPane.showConfirmDialog(parent, palette, msg.get("dialog.cell-background.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return answer == JOptionPane.OK_OPTION ? Optional.of(new Choice(palette.color())) : Optional.empty();
    }

    ColorPalette palette() {
        return palette;
    }
}
