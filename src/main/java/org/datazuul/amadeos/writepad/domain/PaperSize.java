package org.datazuul.amadeos.writepad.domain;

import javax.print.attribute.standard.MediaSizeName;

/**
 * Paper sizes in portrait, in points (1/72 inch), as DOCX, ODT and RTF store them.
 */
public enum PaperSize {
    A5("A5", 148, 210, MediaSizeName.ISO_A5),
    A4("A4", 210, 297, MediaSizeName.ISO_A4),
    LETTER("Letter", 215.9, 279.4, MediaSizeName.NA_LETTER),
    LEGAL("Legal", 215.9, 355.6, MediaSizeName.NA_LEGAL);

    private final String label;
    private final double widthMm;
    private final double heightMm;
    private final MediaSizeName media;

    PaperSize(String label, double widthMm, double heightMm, MediaSizeName media) {
        this.label = label;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.media = media;
    }

    public String label() {
        return label;
    }

    /** Width in portrait, in points. */
    public double width() {
        return Units.mmToPoints(widthMm);
    }

    /** Height in portrait, in points. */
    public double height() {
        return Units.mmToPoints(heightMm);
    }

    /** The paper the printer should take. */
    public MediaSizeName media() {
        return media;
    }

    @Override
    public String toString() {
        return label;
    }
}
