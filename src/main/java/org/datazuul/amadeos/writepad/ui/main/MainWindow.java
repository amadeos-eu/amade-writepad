package org.datazuul.amadeos.writepad.ui.main;

import com.google.inject.Inject;
import org.datazuul.amadeos.commons.i18n.LocaleSettings;
import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.commons.swing.EdtExecutor;
import org.datazuul.amadeos.writepad.domain.DocumentSettings;
import org.datazuul.amadeos.writepad.service.layout.PageLayout;
import org.datazuul.amadeos.writepad.service.layout.PagedSectionView;
import org.datazuul.amadeos.writepad.service.print.PrintService;
import org.datazuul.amadeos.writepad.ui.dialog.PageSetupDialog;
import org.datazuul.amadeos.writepad.ui.editor.PageEditor;
import org.datazuul.amadeos.writepad.ui.editor.SampleText;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.print.PrinterException;
import java.util.Optional;

/**
 * The main window: menu bar, the page editor on its desk, and a status bar with page, paper and zoom.
 */
public final class MainWindow extends JFrame implements MainView {
    private final PageLayout layout;
    private final Messages msg;
    private final LocaleSettings locales;
    private final PrintService printService;
    private final PageEditor editor;
    private final UndoManager undo = new UndoManager();
    private final JLabel pageStatus = new JLabel();
    private final JLabel paperStatus = new JLabel();
    private final JComboBox<String> zoom = new JComboBox<>();
    private MainMenuBar menuBar;
    private MainPresenter presenter;
    private boolean updatingZoom;

    @Inject
    public MainWindow(PageLayout layout, Messages msg, LocaleSettings locales, PrintService printService) {
        super(msg.get("window.title"));
        EdtExecutor.requireEdt();
        this.layout = layout;
        this.msg = msg;
        this.locales = locales;
        this.printService = printService;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        editor = new PageEditor(layout);
        editor.setFooter(page -> msg.format("footer.page", page + 1, editor.section().pageCount()));
        editor.addCaretListener(e -> SwingUtilities.invokeLater(this::updateStatus));
        installDocument(new DefaultStyledDocument());

        JScrollPane scroll = new JScrollPane(editor);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        add(scroll, BorderLayout.CENTER);
        add(statusBar(), BorderLayout.SOUTH);
        layout.addChangeListener(this::layoutChanged);
        setPreferredSize(new Dimension(900, 760));
        pack();
        setLocationRelativeTo(null);
    }

    public void bind(MainPresenter presenter) {
        EdtExecutor.requireEdt();
        this.presenter = presenter;
        menuBar = new MainMenuBar(presenter, msg, locales, this::undo, this::redo);
        setJMenuBar(menuBar);
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

    @Override
    public void clearDocument() {
        EdtExecutor.requireEdt();
        installDocument(new DefaultStyledDocument());
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
        Style base = document.getStyle(StyleContext.DEFAULT_STYLE);
        StyleConstants.setFontFamily(base, PageEditor.DEFAULT_FONT.getFamily());
        StyleConstants.setFontSize(base, PageEditor.DEFAULT_FONT.getSize());
        editor.setStyledDocument(document);
        undo.discardAllEdits();
        document.addUndoableEditListener(undo);
        // Document listeners run before the views have seen the change: update afterwards.
        document.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(MainWindow.this::updateStatus);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(MainWindow.this::updateStatus);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(MainWindow.this::updateStatus);
            }
        });
        updateStatus();
    }

    private void undo() {
        try {
            if (undo.canUndo()) undo.undo();
        } catch (CannotUndoException ignored) {
            // nothing left to undo
        }
    }

    private void redo() {
        try {
            if (undo.canRedo()) undo.redo();
        } catch (CannotRedoException ignored) {
            // nothing left to redo
        }
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
                msg.get("orientation." + settings.orientation().name().toLowerCase())));
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
