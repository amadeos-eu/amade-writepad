package eu.amadeos.writepad.ui.main;

import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import java.awt.Insets;

/**
 * Square Motif buttons for the toolbars, raised in 3D like the buttons of the Front Panel: icon only,
 * tooltip from the action, never take the focus. The bevel turns sunken while a button is pressed or a
 * switch (bold, alignment, ...) is on.
 */
final class ToolButtons {
    private ToolButtons() {
    }

    static JButton button(Action action) {
        return configure(new JButton(action), action);
    }

    /** For actions with {@link Action#SELECTED_KEY}: pressed in while the switch is on. */
    static JToggleButton toggle(Action action) {
        return configure(new JToggleButton(action), action);
    }

    private static <B extends AbstractButton> B configure(B button, Action action) {
        button.setHideActionText(true);
        button.setFocusable(false);
        button.setMargin(new Insets(2, 2, 2, 2));
        // Not a UIResource, so the toolbar's look and feel does not swap in its flat rollover border.
        button.setBorder(BorderFactory.createCompoundBorder(new AmadeUI.MotifButtonBorder(2),
                BorderFactory.createEmptyBorder()));
        button.setName(String.valueOf(action.getValue(Action.ACTION_COMMAND_KEY)));
        return button;
    }
}
