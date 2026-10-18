package eu.amadeos.apps.writepad.repository;

import com.google.inject.Inject;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/**
 * Settings of amade-writepad in {@code ~/.config/amadeos/writepad/writepad.properties}: for now, which bars
 * the window shows.
 */
public class WritepadPreferences {
    public static final String FILE = "writepad.properties";

    public enum Bar {
        TOOLBAR, FORMAT_BAR, RULER, STATUS_BAR;

        String key() {
            return "show." + name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    private final Path file;
    private final Properties properties;

    @Inject
    public WritepadPreferences(AppDirectories directories) {
        this(directories.configFile(FILE));
    }

    public WritepadPreferences(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.properties = PropertiesFile.load(file);
    }

    /** Bars are shown unless switched off. */
    public boolean isVisible(Bar bar) {
        return !"false".equals(properties.getProperty(bar.key()));
    }

    /**
     * Stores the choice at once.
     *
     * @throws java.io.UncheckedIOException if the file cannot be written
     */
    public void setVisible(Bar bar, boolean visible) {
        properties.setProperty(bar.key(), String.valueOf(visible));
        PropertiesFile.store(file, properties, "amade-writepad");
    }
}
