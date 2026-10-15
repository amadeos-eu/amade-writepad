package org.datazuul.amadeos.writepad.ui.main;

import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.writepad.bootstrap.WritepadModule;
import org.datazuul.amadeos.writepad.domain.DocumentSettings;
import org.datazuul.amadeos.writepad.domain.Margins;
import org.datazuul.amadeos.writepad.domain.Orientation;
import org.datazuul.amadeos.writepad.domain.PaperSize;
import org.datazuul.amadeos.writepad.service.layout.PageLayout;
import org.junit.jupiter.api.Test;

import java.awt.print.PrinterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainPresenterTest {
    private static final double SCREEN = 96 / 72.0;

    private final FakeView view = new FakeView();
    private final PageLayout layout = new PageLayout(DocumentSettings.DEFAULT, SCREEN);
    private final Messages msg = Messages.load(WritepadModule.class, WritepadModule.MESSAGES, Locale.ENGLISH);
    private final MainPresenter presenter = new MainPresenter(view, layout, msg, null, () -> SCREEN);

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
    }

    @Test
    void printingErrorsAreShown() {
        view.printFailure = new PrinterException("No printer");
        presenter.print();
        assertEquals(List.of("Printing failed: No printer"), view.errors);
    }

    @Test
    void sampleAndNewGoToTheView() {
        presenter.insertSampleText();
        presenter.newDocument();
        presenter.exit();
        assertEquals(List.of("sample", "clear", "dispose"), view.calls);
    }

    private static final class FakeView implements MainView {
        final List<String> calls = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        Optional<DocumentSettings> pageSetup = Optional.empty();
        DocumentSettings shownSettings;
        PrinterException printFailure;

        @Override
        public void clearDocument() {
            calls.add("clear");
        }

        @Override
        public void insertSampleText() {
            calls.add("sample");
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
