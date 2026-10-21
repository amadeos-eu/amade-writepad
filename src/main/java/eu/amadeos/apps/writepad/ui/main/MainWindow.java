package eu.amadeos.apps.writepad.ui.main;

import com.google.inject.Inject;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.domain.MeasurementUnit;
import eu.amadeos.apps.writepad.repository.WritepadPreferences.Bar;
import eu.amadeos.apps.writepad.service.format.DocumentFormat;
import eu.amadeos.apps.writepad.service.format.Documents;
import eu.amadeos.apps.writepad.service.format.FormatRegistry;
import eu.amadeos.apps.writepad.service.layout.PageLayout;
import eu.amadeos.apps.writepad.service.layout.PagedSectionView;
import eu.amadeos.apps.writepad.service.print.PrintService;
import eu.amadeos.apps.writepad.ui.dialog.PageSetupDialog;
import eu.amadeos.apps.writepad.ui.dialog.ParagraphDialog;
import eu.amadeos.apps.writepad.ui.dialog.TabsDialog;
import eu.amadeos.apps.writepad.ui.editor.PageEditor;
import eu.amadeos.apps.writepad.ui.editor.SampleText;
import eu.amadeos.apps.writepad.ui.editor.TextFormatting;
import eu.amadeos.apps.writepad.ui.ruler.Ruler;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabStop;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.print.PrinterException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The main window: menu bar, toolbar and format bar, the ruler, the page editor on its desk, and a status
 * bar with page, paper and zoom.
 */
public final class MainWindow extends JFrame implements MainView {
    private final PageLayout layout;
    private final Messages msg;
    private final LocaleSettings locales;
    private final PrintService printService;
    private final MeasurementUnit unit;
    private final PageEditor editor;
    private final JScrollPane scroll;
    private Ruler ruler;
    private final JLabel pageStatus = new JLabel();
    private final JLabel paperStatus = new JLabel();
    private final JComboBox<String> zoom = new JComboBox<>();
    private final JPanel statusBar;
    private final JPanel bars = new JPanel();
    private MainMenuBar menuBar;
    private MainToolBar toolBar;
    private FormatBar formatBar;
    private EditorActions actions;
    private MainPresenter presenter;
    private boolean updatingZoom;
    private File lastDirectory;

    @Inject
    public MainWindow(PageLayout layout, Messages msg, LocaleSettings locales, PrintService printService,
                      MeasurementUnit unit) {
        super(msg.get("window.title"));
        EdtExecutor.requireEdt();
        this.layout = layout;
        this.msg = msg;
        this.locales = locales;
        this.printService = printService;
        this.unit = unit;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        editor = new PageEditor(layout);
        editor.setFooter(page -> msg.format("footer.page", page + 1, editor.section().pageCount()));
        editor.addCaretListener(e -> SwingUtilities.invokeLater(this::caretMoved));

        scroll = new JScrollPane(editor);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        add(scroll, BorderLayout.CENTER);
        statusBar = statusBar();
        add(statusBar, BorderLayout.SOUTH);
        bars.setLayout(new BoxLayout(bars, BoxLayout.Y_AXIS));
        add(bars, BorderLayout.NORTH);
        layout.addChangeListener(this::layoutChanged);
        java.net.URL icon = getClass().getResource("/icons/amade-writepad.png");
        if (icon != null) setIconImage(new javax.swing.ImageIcon(icon).getImage());
        setPreferredSize(new Dimension(900, 760));
        pack();
        setLocationRelativeTo(null);
    }

    public void bind(MainPresenter presenter) {
        EdtExecutor.requireEdt();
        this.presenter = presenter;
        actions = new EditorActions(editor, presenter, msg);
        actions.setDialogs(new EditorActions.Dialogs() {
            @Override
            public Optional<ParagraphDialog.Values> paragraph(ParagraphDialog.Values current) {
                return new ParagraphDialog(msg, unit, current).showDialog(MainWindow.this);
            }

            @Override
            public Optional<List<TabStop>> tabs(List<TabStop> current) {
                return new TabsDialog(msg, unit, current, layout.contentWidth()).showDialog(MainWindow.this);
            }
        });
        ruler = new Ruler(editor, unit, msg);
        ruler.setListener(new Ruler.Listener() {
            @Override
            public void indentsChanged(TextFormatting.Indents indents) {
                actions.apply(() -> actions.formatting().setIndents(indents));
            }

            @Override
            public void tabsChanged(List<TabStop> tabs) {
                actions.apply(() -> actions.formatting().setTabs(tabs));
            }
        });
        actions.addUpdateListener(() -> ruler.show(actions.formatting().indents(), actions.formatting().tabs()));
        installDocument(Documents.newDocument());
        menuBar = new MainMenuBar(presenter, actions, msg, locales);
        setJMenuBar(menuBar);
        toolBar = new MainToolBar(actions);
        formatBar = new FormatBar(actions, editor, msg);
        for (JToolBar bar : new JToolBar[]{toolBar, formatBar}) {
            // Full width, so the bevel runs under the whole bar as in WordPad.
            bar.setAlignmentX(LEFT_ALIGNMENT);
            bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, bar.getPreferredSize().height));
        }
        bars.add(toolBar);
        bars.add(formatBar);
        for (Bar bar : Bar.values()) showBar(bar, presenter.isBarVisible(bar));
        actions.update();
        for (int percent : MainPresenter.ZOOM_STEPS) zoom.addItem(msg.format("zoom.percent", percent));
        zoom.addActionListener(e -> {
            if (!updatingZoom && zoom.getSelectedIndex() >= 0) {
                presenter.setZoomPercent(MainPresenter.ZOOM_STEPS.get(zoom.getSelectedIndex()));
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                presenter.exit();
            }
        });
        layoutChanged();
    }

    public PageEditor editor() {
        return editor;
    }

    /** The actions of menus and bars, once {@link #bind} has run. */
    public EditorActions actions() {
        return actions;
    }

    @Override
    public void showBar(Bar bar, boolean visible) {
        EdtExecutor.requireEdt();
        switch (bar) {
            case TOOLBAR -> toolBar.setVisible(visible);
            case FORMAT_BAR -> formatBar.setVisible(visible);
            case RULER -> scroll.setColumnHeaderView(visible ? ruler : null);
            case STATUS_BAR -> statusBar.setVisible(visible);
        }
        revalidate();
    }

    @Override
    public void showDocument(StyledDocument document) {
        EdtExecutor.requireEdt();
        installDocument(document);
        editor.setCaretPosition(0);
        editor.requestFocusInWindow();
    }

    @Override
    public StyledDocument document() {
        return editor.getStyledDocument();
    }

    @Override
    public void showTitle(String title) {
        EdtExecutor.requireEdt();
        setTitle(title);
    }

    @Override
    public Optional<Path> chooseOpenPath(List<DocumentFormat> formats) {
        EdtExecutor.requireEdt();
        JFileChooser chooser = chooser();
        List<String> all = formats.stream().flatMap(f -> f.extensions().stream()).toList();
        FileNameExtensionFilter supported = new FileNameExtensionFilter(msg.get("filter.all-documents"),
                all.toArray(String[]::new));
        chooser.addChoosableFileFilter(supported);
        for (DocumentFormat format : formats) chooser.addChoosableFileFilter(filter(format));
        chooser.setFileFilter(supported);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        lastDirectory = chooser.getCurrentDirectory();
        return Optional.of(chooser.getSelectedFile().toPath());
    }

    @Override
    public Optional<SaveTarget> chooseSavePath(Path suggested, List<DocumentFormat> formats,
                                               DocumentFormat selected) {
        EdtExecutor.requireEdt();
        JFileChooser chooser = chooser();
        chooser.setAcceptAllFileFilterUsed(false);
        Map<FileFilter, DocumentFormat> byFilter = new LinkedHashMap<>();
        for (DocumentFormat format : formats) {
            FileNameExtensionFilter filter = filter(format);
            byFilter.put(filter, format);
            chooser.addChoosableFileFilter(filter);
            if (format == selected) chooser.setFileFilter(filter);
        }
        chooser.setSelectedFile(suggested.toFile());
        // Switching the type changes the extension of the name, as in WordPad.
        chooser.addPropertyChangeListener(JFileChooser.FILE_FILTER_CHANGED_PROPERTY, e -> {
            DocumentFormat format = byFilter.get(chooser.getFileFilter());
            String name = fileName(chooser);
            if (format != null && name != null && !name.isBlank()) {
                chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), withExtension(name, format)));
            }
        });
        while (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            DocumentFormat format = byFilter.getOrDefault(chooser.getFileFilter(), selected);
            File chosen = chooser.getSelectedFile();
            Path path = chosen.toPath();
            if (FormatRegistry.extension(path).isEmpty()) {
                path = path.resolveSibling(path.getFileName() + "." + format.defaultExtension());
            }
            lastDirectory = chooser.getCurrentDirectory();
            if (!Files.exists(path) || confirm(msg.format("dialog.overwrite.message", path.getFileName()),
                    msg.get("dialog.overwrite.title"))) {
                return Optional.of(new SaveTarget(path, format));
            }
        }
        return Optional.empty();
    }

    @Override
    public SaveChoice confirmSave(String documentName) {
        EdtExecutor.requireEdt();
        int choice = JOptionPane.showConfirmDialog(this, plain(msg.format("dialog.save-changes.message",
                documentName)), msg.get("dialog.save-changes.title"), JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return switch (choice) {
            case JOptionPane.YES_OPTION -> SaveChoice.SAVE;
            case JOptionPane.NO_OPTION -> SaveChoice.DISCARD;
            default -> SaveChoice.CANCEL;
        };
    }

    @Override
    public boolean confirmLoseFormatting(String formatName) {
        EdtExecutor.requireEdt();
        return confirm(msg.format("dialog.lose-formatting.message", formatName),
                msg.get("dialog.lose-formatting.title"));
    }

    private boolean confirm(String message, String title) {
        return JOptionPane.showConfirmDialog(this, plain(message), title, JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private JFileChooser chooser() {
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        return chooser;
    }

    private FileNameExtensionFilter filter(DocumentFormat format) {
        String patterns = String.join(", ", format.extensions().stream().map(e -> "*." + e).toList());
        return new FileNameExtensionFilter(format.name() + " (" + patterns + ")",
                format.extensions().toArray(String[]::new));
    }

    /** The name in the chooser's text field, which getSelectedFile does not always reflect yet. */
    private static String fileName(JFileChooser chooser) {
        File selected = chooser.getSelectedFile();
        return selected == null ? null : selected.getName();
    }

    static String withExtension(String name, DocumentFormat format) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base + "." + format.defaultExtension();
    }

    @Override
    public void insertSampleText() {
        EdtExecutor.requireEdt();
        SampleText.appendTo(editor.getStyledDocument(), 6);
        editor.setCaretPosition(0);
    }

    @Override
    public Optional<DocumentSettings> choosePageSetup(DocumentSettings current) {
        EdtExecutor.requireEdt();
        return new PageSetupDialog(msg, current).showDialog(this);
    }

    @Override
    public boolean print() throws PrinterException {
        EdtExecutor.requireEdt();
        // The selection would be printed as well: clear it while printing.
        int dot = editor.getCaret().getDot();
        int mark = editor.getCaret().getMark();
        editor.setCaretPosition(dot);
        try {
            PagedSectionView section = editor.layOut();
            return printService.print(section, msg.get("window.title"));
        } finally {
            editor.setCaretPosition(mark);
            editor.moveCaretPosition(dot);
        }
    }

    @Override
    public void showError(String message) {
        EdtExecutor.requireEdt();
        JOptionPane.showMessageDialog(this, plain(message), msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showInfo(String message) {
        EdtExecutor.requireEdt();
        JOptionPane.showMessageDialog(this, plain(message), getTitle(), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void showAbout() {
        EdtExecutor.requireEdt();
        JOptionPane.showMessageDialog(this,
                "<html><center><h2>amade-writepad 0.1.0</h2>"
                        + "<p>" + msg.get("dialog.about.text") + "</p><br>"
                        + "<small>&copy; 2026 datazuul.com &bull; EUPL-1.2</small></center></html>",
                msg.get("dialog.about.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    /** A text component, so that messages are not read as Swing HTML. */
    private static JTextArea plain(String message) {
        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setOpaque(false);
        return text;
    }

    private void installDocument(StyledDocument document) {
        editor.setStyledDocument(document);
        actions.attach(document);
        // Installing the document copies the editor's font and colour into its default style, and Swing
        // announces that as a change of the whole text later on the event queue. Listening from after that
        // announcement keeps a file that was just opened from showing as changed.
        SwingUtilities.invokeLater(() -> {
            if (editor.getDocument() == document) listenTo(document);
        });
        updateStatus();
    }

    private void listenTo(StyledDocument document) {
        document.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changed();
            }

            private void changed() {
                presenter.documentChanged();
                // Document listeners run before the views have seen the change: update afterwards.
                SwingUtilities.invokeLater(MainWindow.this::caretMoved);
            }
        });
    }

    /** After caret moves and edits: page number, and the formatting the bars show. */
    private void caretMoved() {
        updateStatus();
        if (actions != null) actions.update();
    }

    private JPanel statusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        Font small = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        pageStatus.setFont(small);
        paperStatus.setFont(small);
        left.add(pageStatus);
        left.add(paperStatus);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        JLabel zoomLabel = new JLabel(msg.get("status.zoom"));
        zoomLabel.setFont(small);
        zoomLabel.setLabelFor(zoom);
        zoom.setName("zoom");
        right.add(zoomLabel);
        right.add(zoom);
        bar.add(left, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void layoutChanged() {
        DocumentSettings settings = layout.settings();
        paperStatus.setText(msg.format("status.paper", settings.paper().label(),
                msg.get("orientation." + settings.orientation().name().toLowerCase(java.util.Locale.ROOT))));
        if (menuBar != null) menuBar.showSettings(settings);
        if (presenter != null) {
            updatingZoom = true;
            try {
                int index = MainPresenter.ZOOM_STEPS.indexOf(presenter.zoomPercent());
                if (index >= 0) zoom.setSelectedIndex(index);
                else zoom.setSelectedIndex(-1);
            } finally {
                updatingZoom = false;
            }
        }
        updateStatus();
    }

    private void updateStatus() {
        int pages = editor.pageCount();
        int page = editor.pageOf(editor.getCaretPosition()) + 1;
        pageStatus.setText(msg.format("status.page", Math.min(page, pages), pages));
    }
}
