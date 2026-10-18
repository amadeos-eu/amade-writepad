package eu.amadeos.writepad.service;

import eu.amadeos.writepad.QueueExecutor;
import eu.amadeos.writepad.repository.DocumentFiles;
import eu.amadeos.writepad.service.format.FormatRegistry;
import eu.amadeos.writepad.service.format.rtf.RtfFormat;
import eu.amadeos.writepad.service.format.txt.PlainTextFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentServiceTest {
    @TempDir
    Path dir;
    private final QueueExecutor executor = new QueueExecutor();
    private final FormatRegistry formats = new FormatRegistry(List.of(new RtfFormat(), new PlainTextFormat()));
    private final DocumentService service = new DocumentService(new DocumentFiles(), formats, executor);

    private <T> T result(CompletableFuture<T> future) {
        executor.runAll();
        return future.join();
    }

    private Throwable failure(CompletableFuture<?> future) {
        executor.runAll();
        assertTrue(future.isCompletedExceptionally());
        return DocumentService.cause(future.handle((v, t) -> t).join());
    }

    @Test
    void picksTheFormatByContentThenByName() throws Exception {
        assertTrue(formats.forOpening(Path.of("a.txt"), "{\\rtf1 x}".getBytes()) instanceof RtfFormat);
        assertTrue(formats.forOpening(Path.of("a.rtf"), "﻿ {\\rtf1 x}".getBytes("UTF-8")) instanceof RtfFormat);
        assertTrue(formats.forOpening(Path.of("a.rtf"), "plain".getBytes()) instanceof RtfFormat);
        assertTrue(formats.forOpening(Path.of("a.TXT"), "plain".getBytes()) instanceof PlainTextFormat);
        assertTrue(formats.forOpening(Path.of("README"), "plain".getBytes()) instanceof PlainTextFormat);
        assertEquals("rtf", FormatRegistry.extension(Path.of("x/Brief.RTF")).orElseThrow());
        assertTrue(FormatRegistry.extension(Path.of(".hidden")).isEmpty());
    }

    @Test
    void opensInTheBackground() throws Exception {
        Path file = dir.resolve("a.rtf");
        Files.writeString(file, "{\\rtf1 Hallo}");
        CompletableFuture<DocumentService.Opened> opening = service.open(file);
        assertFalse(opening.isDone());
        DocumentService.Opened opened = result(opening);
        assertEquals("Hallo", opened.content().document().getText(0, 5));
        assertTrue(opened.format() instanceof RtfFormat);
    }

    @Test
    void reportsUnreadableFiles() throws Exception {
        assertTrue(failure(service.open(dir.resolve("missing.rtf"))) instanceof NoSuchFileException);
        Path binary = dir.resolve("image.png");
        Files.write(binary, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0});
        assertTrue(failure(service.open(binary)) instanceof IOException);
    }

    @Test
    void writesAtomicallyAndKeepsPermissions() throws Exception {
        Path file = dir.resolve("a.rtf");
        Files.writeString(file, "old");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
        result(service.write(file, new byte[]{'n', 'e', 'w'}));
        assertArrayEquals(new byte[]{'n', 'e', 'w'}, Files.readAllBytes(file));
        assertEquals("rw-r-----", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
        try (var listing = Files.list(dir)) {
            assertEquals(List.of(file), listing.toList(), "no temporary file left");
        }
    }

    @Test
    void reportsWriteFailures() {
        assertTrue(failure(service.write(dir.resolve("missing/a.rtf"), new byte[0])) instanceof IOException);
    }
}
