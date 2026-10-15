package org.datazuul.amadeos.writepad.ui.main;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import java.awt.Insets;

/** Square Motif buttons for the toolbars: icon only, tooltip from the action, never take the focus. */
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
        button.setMargin(new Insets(3, 3, 3, 3));
        button.setName(String.valueOf(action.getValue(Action.ACTION_COMMAND_KEY)));
        return button;
    }
}
