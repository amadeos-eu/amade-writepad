package eu.amadeos.apps.writepad.ui.main;

import com.google.inject.Inject;
import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.domain.Orientation;
import eu.amadeos.apps.writepad.repository.WritepadPreferences.Bar;
import eu.amadeos.apps.writepad.repository.WritepadPreferences;
import eu.amadeos.apps.writepad.service.DocumentService;
import eu.amadeos.apps.writepad.service.format.DocumentContent;
import eu.amadeos.apps.writepad.service.format.DocumentFormat;
import eu.amadeos.apps.writepad.service.format.Documents;
import eu.amadeos.apps.writepad.service.format.LibreOffice;
import eu.amadeos.apps.writepad.service.layout.PageLayout;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.print.PaperSize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.text.StyledDocument;
import java.awt.print.PrinterException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.DoubleSupplier;

/**
 * The actions of the main window, without Swing: new, open, save, page setup, zoom, printing, bars,
 * language. Keeps track of the file, its format and unsaved changes; page setup changes count as changes,
 * zoom does not.
 *
 * <p>All methods run on the event dispatch thread. Opening and saving continue there through the
 * {@link Ui} executor once the background work is done.
 */
public final class MainPresenter {
    private static final Logger LOG = LoggerFactory.getLogger(MainPresenter.class);
    /** Zoom steps in percent of true size. */
    public static final List<Integer> ZOOM_STEPS = List.of(50, 75, 100, 125, 150, 200, 300);

    private final MainView view;
    private final PageLayout layout;
    private final Messages msg;
    private final LocaleSettings locales;
    private final DoubleSupplier screenScale;
    private final WritepadPreferences preferences;
    private final DocumentService documents;
    private final Executor ui;

    private Path file;
    private DocumentFormat format;
    private DocumentSettings knownSettings;
    private long revision;
    private long savedRevision;
    private boolean busy;

    @Inject
    public MainPresenter(MainView view, PageLayout layout, Messages msg, LocaleSettings locales,
                         @ScreenScale DoubleSupplier screenScale, WritepadPreferences preferences,
                         DocumentService documents, @Ui Executor ui) {
        this.view = view;
        this.layout = layout;
        this.msg = msg;
        this.locales = locales;
        this.screenScale = screenScale;
        this.preferences = preferences;
        this.documents = documents;
        this.ui = ui;
        this.format = documents.formats().defaultFormat();
        this.knownSettings = layout.settings();
        layout.addChangeListener(this::layoutChanged);
    }

    /**
     * After the window is shown: opens the first file named on the command line, or with {@code --sample}
     * shows the sample text, else starts with an empty document.
     */
    public void start(List<String> arguments) {
        updateTitle();
        if (arguments.contains("--sample")) {
            insertSampleText();
            return;
        }
        arguments.stream().filter(a -> !a.startsWith("--")).findFirst().map(Path::of).ifPresent(this::open);
    }

    // ---------------------------------------------------------------- document state

    /** The editor's text or formatting changed. */
    public void documentChanged() {
        revision++;
        updateTitle();
    }

    public boolean isModified() {
        return revision != savedRevision;
    }

    /** The file the document was opened from or last saved to; empty for a new document. */
    public Optional<Path> file() {
        return Optional.ofNullable(file);
    }

    public DocumentFormat format() {
        return format;
    }

    private void layoutChanged() {
        // Zoom changes the layout too, but not the document.
        if (!layout.settings().equals(knownSettings)) {
            knownSettings = layout.settings();
            documentChanged();
        }
    }

    private String documentName() {
        return file == null ? msg.get("document.untitled") : String.valueOf(file.getFileName());
    }

    private void updateTitle() {
        view.showTitle(msg.format("window.title.document", (isModified() ? "*" : "") + documentName()));
    }

    /** Shows a document as unchanged: after opening, saving or New. */
    private void showClean(StyledDocument document, DocumentSettings settings, Path path, DocumentFormat newFormat) {
        knownSettings = settings;
        layout.setSettings(settings);
        view.showDocument(document);
        file = path;
        format = newFormat;
        savedRevision = ++revision;
        updateTitle();
    }

    /**
     * Runs the action if the document has no unsaved changes, or after asking: saved first, discarded, or
     * not at all if the user cancels (or saving fails).
     */
    private void whenSafe(Runnable action) {
        if (busy) return;
        if (!isModified()) {
            action.run();
            return;
        }
        switch (view.confirmSave(documentName())) {
            case SAVE -> save(action);
            case DISCARD -> action.run();
            case CANCEL -> {
            }
        }
    }

    // ---------------------------------------------------------------- file commands

    public void newDocument() {
        whenSafe(() -> showClean(Documents.newDocument(), DocumentSettings.DEFAULT, null,
                documents.formats().defaultFormat()));
    }

    public void chooseOpen() {
        whenSafe(() -> view.chooseOpenPath(documents.formats().formats()).ifPresent(this::openNow));
    }

    /** Opens a file, asking first if there are unsaved changes. */
    public void open(Path path) {
        whenSafe(() -> openNow(path));
    }

    private void openNow(Path path) {
        busy = true;
        documents.open(path).whenCompleteAsync((opened, failure) -> {
            busy = false;
            if (failure != null) {
                Throwable cause = DocumentService.cause(failure);
                LOG.warn("Cannot open {}", path, cause);
                view.showError(msg.format("error.open", path.getFileName(), message(cause)));
                return;
            }
            DocumentContent content = opened.content();
            showClean(content.document(), content.settings(), opened.path(), opened.format());
        }, ui);
    }

    public void save() {
        save(() -> {
        });
    }

    public void saveAs() {
        saveAs(() -> {
        });
    }

    private void save(Runnable then) {
        // One write at a time: two writes of the same file could finish in either order, and the older
        // text would win. The title keeps its "*", so the user can save again when this one is done.
        if (busy) return;
        if (file == null) saveAs(then);
        else saveTo(file, format, then);
    }

    private void saveAs(Runnable then) {
        if (busy) return;
        Path suggested = file != null ? file
                : Path.of(System.getProperty("user.home"), msg.get("document.untitled") + "."
                + documents.formats().defaultFormat().defaultExtension());
        List<DocumentFormat> writable = documents.formats().formats().stream().filter(DocumentFormat::canWrite).toList();
        view.chooseSavePath(suggested, writable, format).ifPresent(target -> {
            if (!target.format().keepsFormatting() && !view.confirmLoseFormatting(target.format().name())) return;
            saveTo(target.path(), target.format(), then);
        });
    }

    private void saveTo(Path path, DocumentFormat target, Runnable then) {
        byte[] bytes;
        try {
            bytes = target.write(new DocumentContent(view.document(), layout.settings()));
        } catch (IOException | RuntimeException e) {
            LOG.warn("Cannot convert the document to {}", target.name(), e);
            view.showError(msg.format("error.save", path.getFileName(), message(e)));
            return;
        }
        long writing = revision;
        busy = true;
        documents.write(path, target, bytes).whenCompleteAsync((ignored, failure) -> {
            busy = false;
            if (failure != null) {
                Throwable cause = DocumentService.cause(failure);
                LOG.warn("Cannot save {}", path, cause);
                view.showError(msg.format("error.save", path.getFileName(), message(cause)));
                return;
            }
            file = path;
            format = target;
            // Edits made while the file was being written are still unsaved.
            savedRevision = writing;
            updateTitle();
            then.run();
        }, ui);
    }

    private String message(Throwable t) {
        if (t instanceof LibreOffice.MissingException) return msg.get("error.needs-libreoffice");
        if (t instanceof NoSuchFileException e) return e.getFile();
        if (t instanceof AccessDeniedException e) return e.getFile();
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }

    // ---------------------------------------------------------------- other commands

    public boolean isBarVisible(Bar bar) {
        return preferences.isVisible(bar);
    }

    /** Shows or hides a bar and remembers it; the window changes even if the setting cannot be saved. */
    public void setBarVisible(Bar bar, boolean visible) {
        view.showBar(bar, visible);
        try {
            preferences.setVisible(bar, visible);
        } catch (UncheckedIOException e) {
            LOG.warn("Could not save the preferences", e);
            view.showError(msg.format("error.save-preferences", e.getCause().getMessage()));
        }
    }

    public void insertSampleText() {
        view.insertSampleText();
    }

    public void pageSetup() {
        view.choosePageSetup(layout.settings()).ifPresent(layout::setSettings);
    }

    public void setPaper(PaperSize paper) {
        layout.setSettings(layout.settings().withPaper(paper));
    }

    public void setOrientation(Orientation orientation) {
        layout.setSettings(layout.settings().withOrientation(orientation));
    }

    /** Zoom in percent of true size, rounded. */
    public int zoomPercent() {
        return (int) Math.round(layout.zoom() / screenScale.getAsDouble() * 100);
    }

    public void setZoomPercent(int percent) {
        double zoom = percent / 100.0 * screenScale.getAsDouble();
        layout.setZoom(Math.max(PageLayout.MIN_ZOOM, Math.min(PageLayout.MAX_ZOOM, zoom)));
    }

    public void zoomIn() {
        int current = zoomPercent();
        ZOOM_STEPS.stream().filter(step -> step > current).findFirst().ifPresent(this::setZoomPercent);
    }

    public void zoomOut() {
        int current = zoomPercent();
        ZOOM_STEPS.reversed().stream().filter(step -> step < current).findFirst().ifPresent(this::setZoomPercent);
    }

    public void print() {
        try {
            view.print();
        } catch (PrinterException | RuntimeException e) {
            LOG.warn("Printing failed", e);
            view.showError(msg.format("error.print", e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    public void setLanguage(Optional<Language> language) {
        try {
            locales.setApplicationLanguage(language);
        } catch (IOException e) {
            LOG.warn("Could not save the language", e);
            view.showError(msg.format("error.save-language", e.getMessage()));
            return;
        }
        if (locales.restartRequired()) {
            view.showInfo(Messages.commons(msg.locale()).format("language.restart", "amade-writepad"));
        }
    }

    public void about() {
        view.showAbout();
    }

    /** Closes the window, asking first if there are unsaved changes. */
    public void exit() {
        whenSafe(view::dispose);
    }
}
