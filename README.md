# amade-writepad

**amade-writepad** is a document editor for the [amadeOS](https://github.com/datazuul/amadeos) desktop, in the spirit of WordPad and GeoWorks Write: formatted text on real pages, ready to print.

![License](https://img.shields.io/badge/license-EUPL--1.2-blue)
![Java](https://img.shields.io/badge/Java-21-orange)

## Goals

- Character and paragraph formatting, bullet and numbered lists, tab stops on a ruler, images, header and footer
- Page layout in A5, A4, Letter and Legal, portrait or landscape; what you see is what prints
- Open and save ODT (default), DOCX and RTF; open DOC
- Five languages (English, Deutsch, Français, Español, Italiano), following the amadeOS desktop language
- Java 21, Swing, Guice and [amade-commons](https://github.com/datazuul/amade-commons); Debian package

## Status

Step 1 of the plan, the page-view spike: styled text laid out on real pages (A5, A4, Letter, Legal, portrait or landscape, margins), zoom from 50 % to 300 %, and printing through the same views, so lines and page breaks on paper match the screen. *Insert → Sample Text* fills a few pages to try it.

Toolbar (new, print, cut/copy/paste, undo/redo) and format bar (font, size, bold, italic, underline, strike-through, text colour, alignment) as in WordPad; the format bar shows the formatting at the caret. Both, and the status bar, can be hidden in *View*.

**Ruler** (View → Ruler) as in WordPad: first-line, hanging/left and right indent markers to drag, tab stops (left, centre, right, decimal; the box on the left chooses the kind) set by a click, moved by dragging, removed by dragging them off. Snaps to quarter centimetres (eighth inches in the US); Alt places freely. *Format → Paragraph* sets indents, spacing, line spacing and alignment exactly, *Format → Tabs* lists the stops. Tabs are measured from the left margin as in RTF and Word; a hanging indent works as a tab stop on the first line.

Opens and saves **RTF** (the default) and **plain text**. RTF from WordPad, Word and LibreOffice is read with fonts, sizes, bold/italic/underline/strike-through, super-/subscript, colours, alignment, indents, spacing and the page setup; what amade-writepad writes opens in LibreOffice with all of that intact. Pictures, tables and lists are skipped for now. The window title shows the file and `*` for unsaved changes; New, Open and Exit ask before discarding them. `amade-writepad Brief.rtf` opens a file from the command line.

## Architecture

Java 21, Swing and Guice; theme, Motif look, language and start-up come from [amade-commons](https://github.com/datazuul/amade-commons).

- `domain`: `PaperSize`, `Orientation`, `Margins`, `DocumentSettings` (all lengths in points, 1/72 inch).
- `service.layout`: the paged view. `PagedEditorKit` replaces the section and paragraph views of Swing's `StyledEditorKit`. `PagedParagraphView` moves every line that would cross the bottom margin to the top of the next page; `PagedSectionView` stacks the paragraphs, paints the sheets and applies the zoom, so that everything inside the views stays in points. `PageLayout` holds the page geometry and the zoom.
- `service.format`: `DocumentFormat` (one per file type, read and write bytes), `FormatRegistry` (finds the format by content, then extension), `rtf.RtfReader`/`RtfWriter`, `txt.PlainTextFormat`. `service.DocumentService` reads and writes files on a background thread.
- `service.print`: `DocumentPageable` paints each page with the same views, unscaled; `PrintService` asks for the paper through the print dialog.
- `ui`: `PageEditor` (the `JTextPane` on the dark desk), `TextFormatting` (formatting of selection or caret), `EditorActions` (one action per command, shared by menus and bars), `MainToolBar`, `FormatBar`, `MainWindow`, `MainMenuBar`, `MainPresenter`, `PageSetupDialog`.
- `ui.ruler`: `Ruler` (the component, in the scroll pane's column header) and `RulerModel` (markers and dragging, without Swing). `service.layout.TabStops` decides where a tab goes.
- `ui.icons`: `ToolIcon`, the toolbar icons drawn in Java2D in the theme's colours, so the app needs no icon theme.
- `repository`: `DocumentFiles` (atomic writes that keep file permissions), `WritepadPreferences` (`~/.config/amadeos/writepad/writepad.properties`).

## Build and run

```sh
./gradlew run --args=--sample      # with sample text
./gradlew run --args=Brief.rtf     # open a file
./gradlew test                     # Swing tests need a display: xvfb-run ./gradlew test
./gradlew deb                      # build/distributions/amade-writepad_<version>_all.deb
```

## Licence

EUPL-1.2, see [LICENSE](LICENSE).
