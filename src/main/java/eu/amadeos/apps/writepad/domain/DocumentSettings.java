package eu.amadeos.apps.writepad.domain;

import eu.amadeos.commons.print.PaperSize;

import java.util.Objects;

/**
 * The page setup of a document: paper, orientation and margins. All sizes are in points.
 */
public record DocumentSettings(PaperSize paper, Orientation orientation, Margins margins) {
    /** Smallest text area a page keeps, so that no margin combination leaves nothing to write on. */
    public static final double MIN_CONTENT = 72;

    public static final DocumentSettings DEFAULT = new DocumentSettings(PaperSize.A4, Orientation.PORTRAIT,
            Margins.DEFAULT);

    public DocumentSettings {
        Objects.requireNonNull(paper, "paper");
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(margins, "margins");
        double width = orientation == Orientation.PORTRAIT ? paper.width() : paper.height();
        double height = orientation == Orientation.PORTRAIT ? paper.height() : paper.width();
        if (width - margins.left() - margins.right() < MIN_CONTENT
                || height - margins.top() - margins.bottom() < MIN_CONTENT) {
            throw new IllegalArgumentException("Margins leave no room for text on " + paper + " " + orientation);
        }
    }

    public double pageWidth() {
        return orientation == Orientation.PORTRAIT ? paper.width() : paper.height();
    }

    public double pageHeight() {
        return orientation == Orientation.PORTRAIT ? paper.height() : paper.width();
    }

    public double contentWidth() {
        return pageWidth() - margins.left() - margins.right();
    }

    public double contentHeight() {
        return pageHeight() - margins.top() - margins.bottom();
    }

    public DocumentSettings withPaper(PaperSize newPaper) {
        return new DocumentSettings(newPaper, orientation, margins);
    }

    public DocumentSettings withOrientation(Orientation newOrientation) {
        return new DocumentSettings(paper, newOrientation, margins);
    }

    public DocumentSettings withMargins(Margins newMargins) {
        return new DocumentSettings(paper, orientation, newMargins);
    }
}
