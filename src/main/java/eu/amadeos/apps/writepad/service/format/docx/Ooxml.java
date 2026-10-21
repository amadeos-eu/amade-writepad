package eu.amadeos.apps.writepad.service.format.docx;

/** Namespaces, relationship and content types of Office Open XML word processing documents. */
final class Ooxml {
    static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    static final String PACKAGE_RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships";
    static final String CONTENT_TYPES = "http://schemas.openxmlformats.org/package/2006/content-types";
    static final String XML = "http://www.w3.org/XML/1998/namespace";

    static final String OFFICE_DOCUMENT = R + "/officeDocument";
    static final String STYLES = R + "/styles";
    static final String NUMBERING = R + "/numbering";
    static final String SETTINGS = R + "/settings";
    static final String THEME = R + "/theme";

    static final String MAIN_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    static final String STYLES_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml";
    static final String NUMBERING_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml";
    static final String SETTINGS_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml";
    static final String RELATIONSHIPS_TYPE = "application/vnd.openxmlformats-package.relationships+xml";

    private Ooxml() {
    }

    /** Twentieths of a point, as DOCX stores lengths. */
    static int twips(double points) {
        return (int) Math.round(points * 20);
    }

    static double points(int twips) {
        return twips / 20.0;
    }
}
