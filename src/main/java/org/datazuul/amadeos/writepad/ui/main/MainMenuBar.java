package org.datazuul.amadeos.writepad.ui.main;

import org.datazuul.amadeos.commons.i18n.Language;
import org.datazuul.amadeos.commons.i18n.LocaleSettings;
import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.commons.i18n.Mnemonics;
import org.datazuul.amadeos.commons.swing.EdtExecutor;
import org.datazuul.amadeos.writepad.domain.DocumentSettings;
import org.datazuul.amadeos.writepad.domain.Orientation;
import org.datazuul.amadeos.writepad.domain.PaperSize;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.border.BevelBorder;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * File, Edit, View, Insert, Format, Options and Help, in the order WordPad uses.
 */
public final class MainMenuBar extends JMenuBar {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int[] FONT_SIZES = {8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36, 48, 72};

    private final Messages msg;
    private final Map<PaperSize, JRadioButtonMenuItem> papers = new EnumMap<>(PaperSize.class);
    private final Map<Orientation, JRadioButtonMenuItem> orientations = new EnumMap<>(Orientation.class);

    public MainMenuBar(MainPresenter presenter, Messages msg, LocaleSettings locales, Runnable undo, Runnable redo) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

        JMenu file = menu("menu.file");
        item(file, "menu.file.new", KeyEvent.VK_N, CTRL, presenter::newDocument);
        file.addSeparator();
        item(file, "menu.file.page-setup", 0, 0, presenter::pageSetup);
        item(file, "menu.file.print", KeyEvent.VK_P, CTRL, presenter::print);
        file.addSeparator();
        item(file, "menu.file.exit", KeyEvent.VK_Q, CTRL, presenter::exit);

        JMenu edit = menu("menu.edit");
        item(edit, "menu.edit.undo", KeyEvent.VK_Z, CTRL, undo);
        item(edit, "menu.edit.redo", KeyEvent.VK_Y, CTRL, redo);
        edit.addSeparator();
        action(edit, "menu.edit.cut", KeyEvent.VK_X, new DefaultEditorKit.CutAction());
        action(edit, "menu.edit.copy", KeyEvent.VK_C, new DefaultEditorKit.CopyAction());
        action(edit, "menu.edit.paste", KeyEvent.VK_V, new DefaultEditorKit.PasteAction());

        JMenu view = menu("menu.view");
        item(view, "menu.view.zoom-in", KeyEvent.VK_PLUS, CTRL, presenter::zoomIn);
        item(view, "menu.view.zoom-out", KeyEvent.VK_MINUS, CTRL, presenter::zoomOut);
        JMenu zoom = new JMenu(msg.get("menu.view.zoom"));
        for (int percent : MainPresenter.ZOOM_STEPS) {
            JMenuItem step = new JMenuItem(msg.format("zoom.percent", percent));
            step.addActionListener(e -> presenter.setZoomPercent(percent));
            zoom.add(step);
        }
        view.add(zoom);

        JMenu insert = menu("menu.insert");
        item(insert, "menu.insert.sample-text", 0, 0, presenter::insertSampleText);

        JMenu format = menu("menu.format");
        action(format, "menu.format.bold", KeyEvent.VK_B, new StyledEditorKit.BoldAction());
        action(format, "menu.format.italic", KeyEvent.VK_I, new StyledEditorKit.ItalicAction());
        action(format, "menu.format.underline", KeyEvent.VK_U, new StyledEditorKit.UnderlineAction());
        JMenu size = new JMenu(msg.get("menu.format.font-size"));
        for (int points : FONT_SIZES) {
            JMenuItem item = new JMenuItem(new StyledEditorKit.FontSizeAction(String.valueOf(points), points));
            size.add(item);
        }
        format.add(size);
        format.addSeparator();
        action(format, "menu.format.align-left", KeyEvent.VK_L,
                new StyledEditorKit.AlignmentAction("left", StyleConstants.ALIGN_LEFT));
        action(format, "menu.format.align-center", KeyEvent.VK_E,
                new StyledEditorKit.AlignmentAction("center", StyleConstants.ALIGN_CENTER));
        action(format, "menu.format.align-right", KeyEvent.VK_R,
                new StyledEditorKit.AlignmentAction("right", StyleConstants.ALIGN_RIGHT));
        action(format, "menu.format.align-justify", KeyEvent.VK_J,
                new StyledEditorKit.AlignmentAction("justify", StyleConstants.ALIGN_JUSTIFIED));
        format.addSeparator();
        JMenu paper = new JMenu(msg.get("menu.format.paper"));
        ButtonGroup paperGroup = new ButtonGroup();
        for (PaperSize choice : PaperSize.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(choice.label());
            item.setName("paper-" + choice.name());
            item.addActionListener(e -> presenter.setPaper(choice));
            paperGroup.add(item);
            paper.add(item);
            papers.put(choice, item);
        }
        format.add(paper);
        JMenu orientation = new JMenu(msg.get("menu.format.orientation"));
        ButtonGroup orientationGroup = new ButtonGroup();
        for (Orientation o : Orientation.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(msg.get("orientation." + o.name().toLowerCase()));
            item.setName("orientation-" + o.name());
            item.addActionListener(e -> presenter.setOrientation(o));
            orientationGroup.add(item);
            orientation.add(item);
            orientations.put(o, item);
        }
        format.add(orientation);

        JMenu options = menu("menu.options");
        options.add(languageMenu(presenter, locales));
        item(menu("menu.help"), "menu.help.about", 0, 0, presenter::about);
    }

    /** Marks the current paper and orientation. */
    public void showSettings(DocumentSettings settings) {
        papers.get(settings.paper()).setSelected(true);
        orientations.get(settings.orientation()).setSelected(true);
    }

    /** "Desktop setting (Deutsch)" and the five languages; the choice applies at the next start. */
    private JMenu languageMenu(MainPresenter presenter, LocaleSettings locales) {
        JMenu menu = new JMenu(msg.get("menu.options.language"));
        Messages commons = Messages.commons(msg.locale());
        List<Optional<Language>> choices = new ArrayList<>();
        choices.add(Optional.empty());
        for (Language language : Language.values()) choices.add(Optional.of(language));
        ButtonGroup group = new ButtonGroup();
        Optional<Language> current = locales.applicationLanguage();
        for (Optional<Language> choice : choices) {
            String label = choice.map(Language::nativeName).orElseGet(() -> commons.format("language.follow-desktop",
                    locales.desktopResolution().language().nativeName()));
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, choice.equals(current));
            item.setName(choice.map(Language::code).orElse(LocaleSettings.FOLLOW_DESKTOP));
            item.addActionListener(e -> presenter.setLanguage(choice));
            group.add(item);
            menu.add(item);
            if (choice.isEmpty()) menu.addSeparator();
        }
        return menu;
    }

    private JMenu menu(String key) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get(key));
        add(menu);
        return menu;
    }

    private void item(JMenu menu, String key, int keyCode, int modifiers, Runnable action) {
        JMenuItem item = new JMenuItem(msg.get(key));
        item.setName(key);
        if (keyCode != 0) item.setAccelerator(KeyStroke.getKeyStroke(keyCode, modifiers));
        item.addActionListener(e -> action.run());
        menu.add(item);
    }

    /** A Swing text action, which works on the editor that last had the focus. */
    private void action(JMenu menu, String key, int keyCode, Action action) {
        JMenuItem item = new JMenuItem(action);
        item.setText(msg.get(key));
        item.setName(key);
        item.setAccelerator(KeyStroke.getKeyStroke(keyCode, CTRL));
        menu.add(item);
    }
}
