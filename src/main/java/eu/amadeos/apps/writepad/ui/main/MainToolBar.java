package eu.amadeos.apps.writepad.ui.main;

import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.commons.swing.ToolButtons;

import javax.swing.JToolBar;

/** New, Open, Save | Print, Preview | Cut, Copy, Paste | Undo, Redo | Find, as in WordPad. */
public final class MainToolBar extends JToolBar {
    public MainToolBar(EditorActions actions) {
        EdtExecutor.requireEdt();
        setName("toolbar");
        setFloatable(false);
        setRollover(false);
        add(ToolButtons.button(actions.newDocument));
        add(ToolButtons.button(actions.open));
        add(ToolButtons.button(actions.save));
        addSeparator();
        add(ToolButtons.button(actions.print));
        add(ToolButtons.button(actions.printPreview));
        addSeparator();
        add(ToolButtons.button(actions.cut));
        add(ToolButtons.button(actions.copy));
        add(ToolButtons.button(actions.paste));
        addSeparator();
        add(ToolButtons.button(actions.undoAction));
        add(ToolButtons.button(actions.redoAction));
        addSeparator();
        add(ToolButtons.button(actions.find));
    }
}
