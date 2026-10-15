package org.datazuul.amadeos.writepad.ui.main;

import org.datazuul.amadeos.writepad.domain.DocumentSettings;
import org.datazuul.amadeos.writepad.repository.WritepadPreferences.Bar;

import java.awt.print.PrinterException;
import java.util.Optional;

/** What {@link MainPresenter} needs from the window; tests replace it. */
public interface MainView {
    /** Empties the editor. */
    void clearDocument();

    /** Appends a few pages of formatted demonstration text. */
    void insertSampleText();

    /** Shows the page setup dialog; empty if cancelled. */
    Optional<DocumentSettings> choosePageSetup(DocumentSettings current);

    /**
     * Shows the print dialog and prints.
     *
     * @return false if the user cancelled
     */
    boolean print() throws PrinterException;

    /** Shows or hides the toolbar, format bar or status bar. */
    void showBar(Bar bar, boolean visible);

    void showError(String message);

    void showInfo(String message);

    void showAbout();

    void dispose();
}
