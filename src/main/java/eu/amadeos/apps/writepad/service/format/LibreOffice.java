package eu.amadeos.apps.writepad.service.format;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * LibreOffice as a file converter: {@code soffice --headless --convert-to}. amade-writepad uses it for the old
 * binary Word format, which it does not read or write itself. Each conversion runs with a profile of its own
 * in a temporary folder, so that it works while the user has LibreOffice open and leaves their settings alone.
 */
public final class LibreOffice {
    private static final long TIMEOUT_SECONDS = 120;

    private final Path soffice;

    private LibreOffice(Path soffice) {
        this.soffice = soffice;
    }

    /** LibreOffice as installed: {@code soffice} or {@code libreoffice} on the path, or in its Windows folder. */
    public static Optional<LibreOffice> find() {
        List<Path> candidates = new ArrayList<>();
        String path = System.getenv("PATH");
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                if (dir.isBlank()) continue;
                for (String name : windows ? List.of("soffice.exe", "soffice.com") : List.of("soffice", "libreoffice")) {
                    candidates.add(Path.of(dir, name));
                }
            }
        }
        if (windows) {
            for (String env : List.of("ProgramFiles", "ProgramFiles(x86)")) {
                String dir = System.getenv(env);
                if (dir != null) candidates.add(Path.of(dir, "LibreOffice", "program", "soffice.exe"));
            }
        }
        return candidates.stream().filter(p -> Files.isRegularFile(p) && Files.isExecutable(p)).findFirst()
                .map(LibreOffice::new);
    }

    /** LibreOffice at the given program, for tests. */
    public static LibreOffice at(Path soffice) {
        return new LibreOffice(soffice);
    }

    /**
     * Converts a file's bytes.
     *
     * @param input     the file's content
     * @param extension its extension without dot, by which LibreOffice chooses the import filter
     * @param target    the output's extension and LibreOffice's export filter, e.g. {@code docx:MS Word 2007 XML}
     * @throws IOException if LibreOffice does not start, fails, takes too long or writes nothing
     */
    public byte[] convert(byte[] input, String extension, String target) throws IOException {
        Path work = Files.createTempDirectory("amade-writepad-convert");
        try {
            Path in = work.resolve("document." + extension);
            Path out = work.resolve("out");
            Files.write(in, input);
            Files.createDirectories(out);
            ProcessBuilder builder = new ProcessBuilder(soffice.toString(),
                    "-env:UserInstallation=" + work.resolve("profile").toUri(), "--headless", "--norestore",
                    "--nologo", "--convert-to", target, "--outdir", out.toString(), in.toString());
            builder.redirectErrorStream(true);
            builder.redirectOutput(work.resolve("log.txt").toFile());
            Process process = builder.start();
            boolean finished;
            try {
                finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while LibreOffice converted the document", e);
            }
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("LibreOffice took longer than " + TIMEOUT_SECONDS + " s to convert the document");
            }
            String outputExtension = target.contains(":") ? target.substring(0, target.indexOf(':')) : target;
            Path result = out.resolve("document." + outputExtension);
            if (process.exitValue() != 0 || !Files.isRegularFile(result)) {
                String log = Files.readString(work.resolve("log.txt"), Charset.defaultCharset()).strip();
                throw new IOException("LibreOffice could not convert the document"
                        + (log.isEmpty() ? "" : ": " + log));
            }
            return Files.readAllBytes(result);
        } finally {
            delete(work);
        }
    }

    private static void delete(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // a temporary file left behind
                }
            });
        } catch (IOException ignored) {
            // the temporary folder is left behind
        }
    }

    /** Thrown when a file needs LibreOffice and it is not installed. */
    public static final class MissingException extends IOException {
        public MissingException() {
            super("This needs LibreOffice, which is not installed");
        }
    }
}
