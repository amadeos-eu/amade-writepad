package eu.amadeos.apps.writepad.service.format;

import com.google.inject.Inject;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The formats amade-writepad knows, found by content or file extension. The first one is used for new
 * documents.
 */
public final class FormatRegistry {
    private final List<DocumentFormat> formats;

    @Inject
    public FormatRegistry(Set<DocumentFormat> formats) {
        this(formats.stream().sorted((a, b) -> Boolean.compare(b.keepsFormatting(), a.keepsFormatting())).toList());
    }

    /** In the given order: the first is the default for saving new documents. */
    public FormatRegistry(List<DocumentFormat> formats) {
        if (formats.isEmpty()) throw new IllegalArgumentException("No formats");
        this.formats = List.copyOf(formats);
    }

    public List<DocumentFormat> formats() {
        return formats;
    }

    /** The format new documents are saved in. */
    public DocumentFormat defaultFormat() {
        return formats.get(0);
    }

    /** The format for the file's extension, ignoring case. */
    public Optional<DocumentFormat> forPath(Path path) {
        return extension(path).flatMap(ext -> formats.stream().filter(f -> f.extensions().contains(ext)).findFirst());
    }

    /**
     * The format to open a file with: one that recognizes the content, else the one for the extension, else
     * the first format that keeps no formatting (plain text), which opens anything that is text.
     */
    public DocumentFormat forOpening(Path path, byte[] content) {
        for (DocumentFormat format : formats) {
            if (format.recognizes(content)) return format;
        }
        return forPath(path).orElseGet(() -> formats.stream().filter(f -> !f.keepsFormatting()).findFirst()
                .orElse(defaultFormat()));
    }

    /** The extension in lower case, without dot; empty if the name has none. */
    public static Optional<String> extension(Path path) {
        Path name = path.getFileName();
        if (name == null) return Optional.empty();
        String file = name.toString();
        int dot = file.lastIndexOf('.');
        if (dot <= 0 || dot == file.length() - 1) return Optional.empty();
        return Optional.of(file.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
