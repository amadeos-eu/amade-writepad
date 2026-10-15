package org.datazuul.amadeos.writepad.ui.main;

import com.google.inject.Inject;
import org.datazuul.amadeos.commons.i18n.Language;
import org.datazuul.amadeos.commons.i18n.LocaleSettings;
import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.writepad.domain.Orientation;
import org.datazuul.amadeos.writepad.domain.PaperSize;
import org.datazuul.amadeos.writepad.service.layout.PageLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.print.PrinterException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleSupplier;

/**
 * The actions of the main window, without Swing: page setup, zoom, printing, language.
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

    @Inject
    public MainPresenter(MainView view, PageLayout layout, Messages msg, LocaleSettings locales,
                         @ScreenScale DoubleSupplier screenScale) {
        this.view = view;
        this.layout = layout;
        this.msg = msg;
        this.locales = locales;
        this.screenScale = screenScale;
    }

    public void newDocument() {
        view.clearDocument();
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

    public void exit() {
        view.dispose();
    }
}
