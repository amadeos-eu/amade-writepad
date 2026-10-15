package org.datazuul.amadeos.writepad.ui.main;

import org.datazuul.amadeos.writepad.domain.DocumentSettings;
import org.datazuul.amadeos.writepad.repository.WritepadPreferences.Bar;
import org.datazuul.amadeos.writepad.service.format.DocumentFormat;

import javax.swing.text.StyledDocument;
import java.awt.print.PrinterException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** What {@link MainPresenter} needs from the window; tests replace it. */
public interface MainView {
    /** Shows a new document in the editor, caret at the start, with an empty undo history. */
    void showDocument(StyledDocument document);

    /** The document in the editor. */
    StyledDocument document();

    /** Appends a few pages of formatted demonstration text. */
    void insertSampleText();

    /** Window title, e.g. "*Brief.rtf - amade-writepad". */
    void showTitle(String title);

    /** Asks for a file to open; empty if cancelled. */
    Optional<Path> chooseOpenPath(List<DocumentFormat> formats);

    /** Where and in which format to save. */
    record SaveTarget(Path path, DocumentFormat format) {
    }

    /**
     * Asks where to save, offering the formats with {@code selected} chosen. A name without extension gets
     * the format's; replacing an existing file is confirmed. Empty if cancelled.
     */
    Optional<SaveTarget> chooseSavePath(Path suggested, List<DocumentFormat> formats, DocumentFormat selected);

    /** "Save changes to Brief.rtf?" */
    SaveChoice confirmSave(String documentName);

    /** Warns that the format keeps no formatting; true to save anyway. */
    boolean confirmLoseFormatting(String formatName);

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

    enum SaveChoice {SAVE, DISCARD, CANCEL}
}
