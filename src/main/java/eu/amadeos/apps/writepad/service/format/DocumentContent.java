package eu.amadeos.apps.writepad.service.format;

import eu.amadeos.apps.writepad.domain.DocumentSettings;

import javax.swing.text.StyledDocument;
import java.util.Objects;

/**
 * What a file holds: the formatted text and the page setup.
 */
public record DocumentContent(StyledDocument document, DocumentSettings settings) {
    public DocumentContent {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(settings, "settings");
    }
}
