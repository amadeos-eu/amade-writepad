package eu.amadeos.apps.writepad.ui.main;

import eu.amadeos.apps.writepad.QueueExecutor;
import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.domain.Margins;
import eu.amadeos.apps.writepad.domain.Orientation;
import eu.amadeos.apps.writepad.repository.WritepadPreferences.Bar;
import eu.amadeos.apps.writepad.repository.WritepadPreferences;
import eu.amadeos.apps.writepad.service.format.DocumentFormat;
import eu.amadeos.apps.writepad.service.format.Documents;
import eu.amadeos.apps.writepad.service.format.odt.OdtFormat;
import eu.amadeos.apps.writepad.service.format.rtf.RtfFormat;
import eu.amadeos.apps.writepad.service.layout.PageLayout;
import eu.amadeos.commons.print.PaperSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import java.awt.print.PrinterException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainPresenterTest {
    private static final double SCREEN = 96 / 72.0;
    private static final String RTF = "{\\rtf1\\ansi\\paperw11906\\paperh8391\\landscape {\\b Hallo} Welt}";

    private final FakeView view = new FakeView();
    private final PageLayout layout = new PageLayout(DocumentSettings.DEFAULT, SCREEN);
    private final QueueExecutor executor = new QueueExecutor();
    @TempDir
    Path dir;
    private MainPresenter presenter;

    @BeforeEach
    void createPresenter() {
        presenter = TestPresenters.create(view, layout, dir, executor, SCREEN,
                new WritepadPreferences(dir.resolve("writepad.properties")));
        presenter.start(List.of());
        view.calls.clear();
    }

    private Path file(String name, String content) throws Exception {
        Path path = dir.resolve(name);
        Files.writeString(path, content, StandardCharsets.ISO_8859_1);
        return path;
    }

    private void type(String text) {
        try {
            view.document.insertString(view.document.getLength(), text, null);
        } catch (BadLocationException e) {
            throw new AssertionError(e);
        }
        presenter.documentChanged();
    }

    // ---------------------------------------------------------------- open and save

    @Test
    void newWindowIsAnUntitledCleanDocument() {
        assertEquals("Untitled - amade-writepad", view.title);
        assertFalse(presenter.isModified());
        assertEquals("odt", presenter.format().defaultExtension());
    }

    @Test
    void opensAFileWithItsPageSetup() throws Exception {
        Path brief = file("Brief.rtf", RTF);
        presenter.open(brief);
        assertEquals("", view.text(), "nothing shown before the file was read");
        executor.runAll();
        assertEquals("Hallo Welt", view.text());
        assertEquals("Brief.rtf - amade-writepad", view.title);
        assertEquals(PaperSize.A5, layout.settings().paper());
        assertEquals(Orientation.LANDSCAPE, layout.settings().orientation());
        assertFalse(presenter.isModified());
        assertEquals(Optional.of(brief), presenter.file());
    }

    @Test
    void contentDecidesTheFormat() throws Exception {
        presenter.open(file("looks-like-text.txt", RTF));
        executor.runAll();
        assertEquals("Hallo Welt", view.text());
        assertEquals("rtf", presenter.format().defaultExtension());
        presenter.open(file("notes.md", "# plain {\\b not rtf}"));
        executor.runAll();
        assertEquals("# plain {\\b not rtf}", view.text());
        assertFalse(presenter.format().keepsFormatting());
    }

    @Test
    void editsMarkTheDocumentAndSavingClearsTheMark() throws Exception {
        Path brief = file("Brief.rtf", RTF);
        presenter.open(brief);
        executor.runAll();
        type("!");
        assertTrue(presenter.isModified());
        assertEquals("*Brief.rtf - amade-writepad", view.title);
        presenter.save();
        executor.runAll();
        assertFalse(presenter.isModified());
        assertEquals("Brief.rtf - amade-writepad", view.title);
        StyledDocument saved = new RtfFormat().read(Files.readAllBytes(brief)).document();
        assertEquals("Hallo Welt!", saved.getText(0, saved.getLength()));
        assertTrue(view.saveTargets.isEmpty(), "a document with a file is saved without asking");
    }

    @Test
    void editsDuringSavingStayUnsaved() throws Exception {
        presenter.open(file("Brief.rtf", RTF));
        executor.runAll();
        type("1");
        presenter.save();
        type("2");
        executor.runAll();
        assertTrue(presenter.isModified());
        assertEquals("*Brief.rtf - amade-writepad", view.title);
    }

    // --8<-- [start:second-save]
    @Test
    void aSecondSaveWaitsUntilTheFirstIsWritten() throws Exception {
        Path brief = file("Brief.rtf", RTF);
        presenter.open(brief);
        executor.runAll();
        type("1");
        presenter.save();
        type("2");
        presenter.save();
        executor.runAll();
        assertEquals("Hallo Welt1", text(brief), "only the first save was written");
        assertTrue(presenter.isModified());
        presenter.save();
        executor.runAll();
        assertEquals("Hallo Welt12", text(brief));
        assertFalse(presenter.isModified());
    }
    // --8<-- [end:second-save]

    private static String text(Path rtf) throws Exception {
        StyledDocument saved = new RtfFormat().read(Files.readAllBytes(rtf)).document();
        return saved.getText(0, saved.getLength());
    }

    @Test
    void untitledDocumentsAskWhereToSave() throws Exception {
        type("Neu");
        Path target = dir.resolve("Neu.odt");
        view.saveTargets.add(new MainView.SaveTarget(target, TestPresenters.formats().defaultFormat()));
        presenter.save();
        executor.runAll();
        assertEquals(dir.resolve("Untitled.odt").getFileName(), view.suggested.getFileName());
        assertTrue(new OdtFormat().recognizes(Files.readAllBytes(target)));
        assertEquals("Neu.odt - amade-writepad", view.title);
    }

    @Test
    void savingAsTextWarnsAndSwitchesFormat() throws Exception {
        presenter.open(file("Brief.rtf", RTF));
        executor.runAll();
        DocumentFormat text = TestPresenters.formats().formats().get(2);
        Path txt = dir.resolve("Brief.txt");

        view.saveTargets.add(new MainView.SaveTarget(txt, text));
        view.loseFormatting = false;
        presenter.saveAs();
        executor.runAll();
        assertEquals(List.of("lose formatting? Text"), view.calls);
        assertFalse(Files.exists(txt));

        view.saveTargets.add(new MainView.SaveTarget(txt, text));
        view.loseFormatting = true;
        presenter.saveAs();
        executor.runAll();
        assertEquals("Hallo Welt", Files.readString(txt, StandardCharsets.UTF_8));
        assertEquals("Brief.txt - amade-writepad", view.title);
        assertFalse(presenter.format().keepsFormatting());
    }

    @Test
    void failuresAreShownAndChangeNothing() throws Exception {
        presenter.open(dir.resolve("Missing.rtf"));
        executor.runAll();
        assertEquals(1, view.errors.size());
        assertTrue(view.errors.get(0).startsWith("Missing.rtf could not be opened"), view.errors.get(0));
        assertEquals("Untitled - amade-writepad", view.title);

        Files.write(dir.resolve("binary.bin"), new byte[]{1, 0, 2});
        presenter.open(dir.resolve("binary.bin"));
        executor.runAll();
        assertEquals(2, view.errors.size());

        type("x");
        view.saveTargets.add(new MainView.SaveTarget(dir.resolve("no/such/dir/x.rtf"),
                TestPresenters.formats().defaultFormat()));
        presenter.save();
        executor.runAll();
        assertTrue(view.errors.get(2).startsWith("x.rtf could not be saved"), view.errors.get(2));
        assertTrue(presenter.isModified());
    }

    @Test
    void commandLineOpensTheFirstFile() throws Exception {
        Path brief = file("Brief.rtf", RTF);
        presenter.start(List.of(brief.toString(), "other.rtf"));
        executor.runAll();
        assertEquals("Hallo Welt", view.text());
    }

    // ---------------------------------------------------------------- unsaved changes

    @Test
    void newAndOpenAskAboutUnsavedChanges() throws Exception {
        type("draft");
        view.saveChoices.add(MainView.SaveChoice.CANCEL);
        presenter.newDocument();
        assertEquals("draft", view.text());
        assertEquals(List.of("save changes? Untitled"), view.calls);

        view.saveChoices.add(MainView.SaveChoice.DISCARD);
        presenter.open(file("Brief.rtf", RTF));
        executor.runAll();
        assertEquals("Hallo Welt", view.text());
    }

    @Test
    void savingFirstThenContinues() throws Exception {
        type("draft");
        Path target = dir.resolve("draft.rtf");
        view.saveChoices.add(MainView.SaveChoice.SAVE);
        view.saveTargets.add(new MainView.SaveTarget(target, TestPresenters.formats().defaultFormat()));
        presenter.newDocument();
        assertEquals("draft", view.text(), "the new document waits for the save");
        executor.runAll();
        assertTrue(Files.exists(target));
        assertEquals("", view.text());
        assertEquals("Untitled - amade-writepad", view.title);
    }

    @Test
    void cancellingTheSaveDialogKeepsTheDocument() throws Exception {
        type("draft");
        view.saveChoices.add(MainView.SaveChoice.SAVE);
        presenter.exit();
        executor.runAll();
        assertFalse(view.calls.contains("dispose"));
        assertEquals("draft", view.text());
    }

    @Test
    void exitAsksOnlyWithUnsavedChanges() {
        presenter.exit();
        assertEquals(List.of("dispose"), view.calls);
    }

    @Test
    void pageSetupIsAChangeButZoomIsNot() {
        presenter.setZoomPercent(200);
        assertFalse(presenter.isModified());
        presenter.setPaper(PaperSize.A5);
        assertTrue(presenter.isModified());
    }

    // ---------------------------------------------------------------- bars, zoom, page setup, printing

    @Test
    void barsAreShownUntilSwitchedOffAndRemembered() {
        assertTrue(presenter.isBarVisible(Bar.FORMAT_BAR));
        presenter.setBarVisible(Bar.FORMAT_BAR, false);
        assertEquals(List.of("hide FORMAT_BAR"), view.calls);
        assertFalse(new WritepadPreferences(dir.resolve("writepad.properties")).isVisible(Bar.FORMAT_BAR));
        assertTrue(presenter.isBarVisible(Bar.TOOLBAR));
    }

    @Test
    void barStillChangesWhenTheSettingCannotBeSaved() throws Exception {
        Path blocked = dir.resolve("blocked");
        Files.writeString(blocked, "a file where a directory should be");
        MainPresenter failing = TestPresenters.create(view, layout, dir, executor, SCREEN,
                new WritepadPreferences(blocked.resolve("writepad.properties")));
        failing.setBarVisible(Bar.STATUS_BAR, false);
        assertEquals(List.of("hide STATUS_BAR"), view.calls);
        assertEquals(1, view.errors.size());
        assertTrue(view.errors.get(0).startsWith("The settings could not be saved"), view.errors.get(0));
    }

    @Test
    void startsAtTrueSize() {
        assertEquals(100, presenter.zoomPercent());
    }

    @Test
    void zoomStepsThroughTheList() {
        presenter.zoomIn();
        assertEquals(125, presenter.zoomPercent());
        assertEquals(1.25 * SCREEN, layout.zoom(), 1e-9);
        presenter.zoomOut();
        presenter.zoomOut();
        assertEquals(75, presenter.zoomPercent());
        presenter.setZoomPercent(300);
        presenter.zoomIn();
        assertEquals(300, presenter.zoomPercent());
        presenter.setZoomPercent(50);
        presenter.zoomOut();
        assertEquals(50, presenter.zoomPercent());
    }

    @Test
    void paperAndOrientationChangeThePage() {
        presenter.setPaper(PaperSize.A5);
        presenter.setOrientation(Orientation.LANDSCAPE);
        assertEquals(PaperSize.A5, layout.settings().paper());
        assertEquals(Orientation.LANDSCAPE, layout.settings().orientation());
    }

    @Test
    void pageSetupAppliesTheChosenSettings() {
        DocumentSettings chosen = new DocumentSettings(PaperSize.LETTER, Orientation.PORTRAIT,
                Margins.ofMm(10, 10, 10, 10));
        view.pageSetup = Optional.of(chosen);
        presenter.pageSetup();
        assertEquals(chosen, layout.settings());
        assertEquals(DocumentSettings.DEFAULT, view.shownSettings);
    }

    @Test
    void cancelledPageSetupChangesNothing() {
        view.pageSetup = Optional.empty();
        presenter.pageSetup();
        assertEquals(DocumentSettings.DEFAULT, layout.settings());
        assertFalse(presenter.isModified());
    }

    @Test
    void printingErrorsAreShown() {
        view.printFailure = new PrinterException("No printer");
        presenter.print();
        assertEquals(List.of("Printing failed: No printer"), view.errors);
    }

    private static final class FakeView implements MainView {
        final List<String> calls = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final Deque<SaveTarget> saveTargets = new ArrayDeque<>();
        final Deque<SaveChoice> saveChoices = new ArrayDeque<>();
        StyledDocument document = Documents.newDocument();
        String title;
        Path suggested;
        boolean loseFormatting;
        Optional<DocumentSettings> pageSetup = Optional.empty();
        DocumentSettings shownSettings;
        PrinterException printFailure;

        String text() {
            try {
                return document.getText(0, document.getLength());
            } catch (BadLocationException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public void showDocument(StyledDocument newDocument) {
            document = newDocument;
        }

        @Override
        public StyledDocument document() {
            return document;
        }

        @Override
        public void insertSampleText() {
            calls.add("sample");
        }

        @Override
        public void showTitle(String newTitle) {
            title = newTitle;
        }

        @Override
        public Optional<Path> chooseOpenPath(List<DocumentFormat> formats) {
            return Optional.empty();
        }

        @Override
        public Optional<SaveTarget> chooseSavePath(Path suggestedPath, List<DocumentFormat> formats,
                                                   DocumentFormat selected) {
            suggested = suggestedPath;
            return Optional.ofNullable(saveTargets.poll());
        }

        @Override
        public SaveChoice confirmSave(String documentName) {
            calls.add("save changes? " + documentName);
            return saveChoices.isEmpty() ? SaveChoice.CANCEL : saveChoices.poll();
        }

        @Override
        public boolean confirmLoseFormatting(String formatName) {
            calls.add("lose formatting? " + formatName);
            return loseFormatting;
        }

        @Override
        public Optional<DocumentSettings> choosePageSetup(DocumentSettings current) {
            shownSettings = current;
            return pageSetup;
        }

        @Override
        public boolean print() throws PrinterException {
            if (printFailure != null) throw printFailure;
            calls.add("print");
            return true;
        }

        @Override
        public void showBar(Bar bar, boolean visible) {
            calls.add((visible ? "show " : "hide ") + bar);
        }

        @Override
        public void showError(String message) {
            errors.add(message);
        }

        @Override
        public void showInfo(String message) {
            calls.add("info " + message);
        }

        @Override
        public void showAbout() {
            calls.add("about");
        }

        @Override
        public void dispose() {
            calls.add("dispose");
        }
    }
}
