package eu.amadeos.writepad.ui.main;

import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.writepad.domain.DocumentSettings;
import eu.amadeos.writepad.domain.Orientation;
import eu.amadeos.writepad.domain.PaperSize;
import eu.amadeos.writepad.repository.WritepadPreferences.Bar;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.border.BevelBorder;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * File, Edit, View, Insert, Format, Options and Help, in the order WordPad uses. Editing and formatting
 * items share their actions with the toolbars.
 */
public final class MainMenuBar extends JMenuBar {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;

    private final Messages msg;
    private final Map<PaperSize, JRadioButtonMenuItem> papers = new EnumMap<>(PaperSize.class);
    private final Map<Orientation, JRadioButtonMenuItem> orientations = new EnumMap<>(Orientation.class);

    public MainMenuBar(MainPresenter presenter, EditorActions actions, Messages msg, LocaleSettings locales) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

        JMenu file = menu("menu.file");
        file.add(item(actions.newDocument));
        file.add(item(actions.open));
        file.add(item(actions.save));
        file.add(item(actions.saveAs));
        file.addSeparator();
        item(file, "menu.file.page-setup", 0, 0, presenter::pageSetup);
        file.add(item(actions.printPreview));
        file.add(item(actions.print));
        file.addSeparator();
        item(file, "menu.file.exit", KeyEvent.VK_Q, CTRL, presenter::exit);

        JMenu edit = menu("menu.edit");
        edit.add(item(actions.undoAction));
        edit.add(item(actions.redoAction));
        edit.addSeparator();
        edit.add(item(actions.cut));
        edit.add(item(actions.copy));
        edit.add(item(actions.paste));
        edit.addSeparator();
        edit.add(item(actions.find));

        JMenu view = menu("menu.view");
        for (Bar bar : Bar.values()) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(msg.get(barKey(bar)), presenter.isBarVisible(bar));
            item.setName(barKey(bar));
            item.addActionListener(e -> presenter.setBarVisible(bar, item.isSelected()));
            view.add(item);
        }
        view.addSeparator();
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
        format.add(check(actions.bold));
        format.add(check(actions.italic));
        format.add(check(actions.underline));
        format.add(check(actions.strikeThrough));
        format.add(item(actions.textColor));
        format.addSeparator();
        format.add(radio(actions.alignLeft));
        format.add(radio(actions.alignCenter));
        format.add(radio(actions.alignRight));
        format.add(radio(actions.alignJustify));
        format.addSeparator();
        format.add(item(actions.paragraph));
        format.add(item(actions.tabs));
        format.addSeparator();
        format.add(check(actions.bullets));
        format.add(check(actions.numbering));
        format.add(item(actions.increaseLevel));
        format.add(item(actions.decreaseLevel));
        format.add(item(actions.restartNumbering));
        format.add(item(actions.continueNumbering));
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
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(
                    msg.get("orientation." + o.name().toLowerCase(Locale.ROOT)));
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

    static String barKey(Bar bar) {
        return switch (bar) {
            case TOOLBAR -> "menu.view.toolbar";
            case FORMAT_BAR -> "menu.view.format-bar";
            case RULER -> "menu.view.ruler";
            case STATUS_BAR -> "menu.view.status-bar";
        };
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

    private static JMenuItem item(Action action) {
        return named(new JMenuItem(action), action);
    }

    private static JMenuItem check(Action action) {
        return named(new JCheckBoxMenuItem(action), action);
    }

    private static JMenuItem radio(Action action) {
        return named(new JRadioButtonMenuItem(action), action);
    }

    private static JMenuItem named(JMenuItem item, Action action) {
        item.setName(String.valueOf(action.getValue(Action.ACTION_COMMAND_KEY)));
        return item;
    }
}
