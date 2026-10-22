# amade-writepad

**amade-writepad** is a document editor for the [amadeOS](https://github.com/datazuul/amadeos) desktop, in the spirit of WordPad and GeoWorks Write: formatted text on real pages, ready to print.

![License](https://img.shields.io/badge/license-EUPL--1.2-blue)
![Java](https://img.shields.io/badge/Java-25-orange)

## Goals

- Character and paragraph formatting, bullet and numbered lists, tab stops on a ruler, images, header and footer
- Page layout in A5, A4, Letter and Legal, portrait or landscape; what you see is what prints
- Open and save ODT (default), DOCX and RTF; open DOC
- Five languages (English, Deutsch, Français, Español, Italiano), following the amadeOS desktop language
- Java 25, Swing, Guice and [amade-commons](https://github.com/datazuul/amade-commons); Debian package

## Status

Step 1 of the plan, the page-view spike: styled text laid out on real pages (A5, A4, Letter, Legal, portrait or landscape, margins), zoom from 50 % to 300 %, and printing through the same views, so lines and page breaks on paper match the screen. *Insert → Sample Text* fills a few pages to try it.

Toolbar (new, print, cut/copy/paste, undo/redo) and format bar (font, size, bold, italic, underline, strike-through, text colour, alignment) as in WordPad; the format bar shows the formatting at the caret. Both, and the status bar, can be hidden in *View*.

**Ruler** (View → Ruler) as in WordPad: first-line, hanging/left and right indent markers to drag, tab stops (left, centre, right, decimal; the box on the left chooses the kind) set by a click, moved by dragging, removed by dragging them off. Snaps to quarter centimetres (eighth inches in the US); Alt places freely. *Format → Paragraph* sets indents, spacing, line spacing and alignment exactly, *Format → Tabs* lists the stops. Tabs are measured from the left margin as in RTF and Word; a hanging indent works as a tab stop on the first line.

**Lists**: bullets (• ◦ ▪ by level) and numbering (1. a. i. by level; A. and I. read from files) from the format bar or *Format*. Tab at the start of an item indents it a level, Shift+Tab outdents, Enter on an empty item or Backspace at the start of one ends the list. Items of a list count on across paragraphs between them; *Restart Numbering* and *Continue Numbering* split and join lists. Labels are drawn by the page view, not part of the text. Changes to several paragraphs undo in one step.

Opens and saves **ODT** (OpenDocument Text, the default for new documents), **DOCX** (Word), **RTF** and **plain text**; **DOC** (Word 97-2003) through LibreOffice, when it is installed.

ODT files from LibreOffice are read with their styles resolved (default style, parents, automatic styles), lists (nested, continued with `text:continue-list` or `text:continue-numbering`), tab stops and the page layout; tables, sections and indexes give their paragraphs, fields and links their text, and pictures, frames, notes and comments are skipped for now. What amade-writepad writes passes the ODF 1.3 validator and opens in LibreOffice with formatting, lists, tab stops, line breaks and page setup intact.

DOCX from Word and LibreOffice is read with its styles resolved (document defaults, style parents, theme fonts), numbering (levels, formats, restarts), tab stops and the page setup; tables, content controls and tracked insertions give their text, field results and links their text, and pictures, deleted text, notes and comments are skipped for now. What amade-writepad writes opens in LibreOffice with formatting, lists, tab stops, line breaks and page setup intact. DOC files are converted by LibreOffice (`soffice --headless`) to DOCX on opening and back on saving; without LibreOffice they cannot be opened and *Save As* does not offer DOC. Why there is no Apache POI is in [CONTRIBUTING.md](CONTRIBUTING.md).

RTF from WordPad, Word and LibreOffice is read with fonts, sizes, bold/italic/underline/strike-through, super-/subscript, colours, alignment, indents, spacing and the page setup; what amade-writepad writes opens in LibreOffice with all of that intact. Lists are read from Word's and LibreOffice's list tables and from WordPad's older `\pn` syntax and written so that LibreOffice shows them as lists. Pictures and tables in RTF are skipped for now.

The window title shows the file and `*` for unsaved changes; New, Open and Exit ask before discarding them. `amade-writepad Brief.odt` opens a file from the command line.

## Architecture

Java 25, Swing and Guice; theme, Motif look, language and start-up come from [amade-commons](https://github.com/datazuul/amade-commons).

- `domain`: `PaperSize`, `Orientation`, `Margins`, `DocumentSettings` (all lengths in points, 1/72 inch).
- `service.layout`: the paged view. `PagedEditorKit` replaces the section and paragraph views of Swing's `StyledEditorKit`. `PagedParagraphView` moves every line that would cross the bottom margin to the top of the next page; `PagedSectionView` stacks the paragraphs, paints the sheets and applies the zoom, so that everything inside the views stays in points. `PageLayout` holds the page geometry and the zoom.
- `service.format`: `DocumentFormat` (one per file type, read and write bytes), `FormatRegistry` (finds the format by content, then extension), `odt.OdtReader`/`OdtWriter` (StAX, no ODF library; `OdtStyles` resolves style inheritance), `docx.DocxReader`/`DocxWriter` (StAX, no POI), `doc.DocFormat` (through `LibreOffice`, which runs `soffice` with its own profile), `rtf.RtfReader`/`RtfWriter`, `txt.PlainTextFormat`. `service.DocumentService` reads and writes files on a background thread.
- `service.list`: `ListAttributes` (list id, level and format as paragraph attributes), `ListFormat`, `ListNumbering` (the labels of a document).
- `service.print`: `DocumentPageable` paints each page with the same views, unscaled; `PrintService` asks for the paper through the print dialog.
- `ui`: `PageEditor` (the `JTextPane` on the dark desk), `TextFormatting` (formatting of selection or caret), `EditorActions` (one action per command, shared by menus and bars), `MainToolBar`, `FormatBar`, `MainWindow`, `MainMenuBar`, `MainPresenter`, `PageSetupDialog`.
- `ui.ruler`: `Ruler` (the component, in the scroll pane's column header) and `RulerModel` (markers and dragging, without Swing). `service.layout.TabStops` decides where a tab goes.
- `ui.icons`: `WritepadGlyph` and `TextColorGlyph`, the toolbar glyphs only WritePad needs. `ToolIcon` (icons drawn in Java2D in the theme's colours, so the app needs no icon theme), the common glyphs and `ToolButtons` come from amade-commons.
- `repository`: `DocumentFiles` (atomic writes that keep file permissions), `WritepadPreferences` (`~/.config/amadeos/writepad/writepad.properties`).

## Build and run

```sh
./gradlew run --args=--sample      # with sample text
./gradlew run --args=Brief.odt     # open a file
./gradlew test                     # Swing tests need a display: xvfb-run ./gradlew test
./gradlew deb                      # build/distributions/amade-writepad_<version>_all.deb
./gradlew installToAmadeOs         # the .deb into ../amadeos/config/packages.chroot/ for the ISO build
./gradlew windowsInstaller         # on Windows: MSI with its own Java runtime (needs WiX Toolset 3)
./gradlew windowsInstaller -PinstallerType=app-image   # portable folder with amade-writepad.exe
```

CI builds the .deb on every push and pull request, and on Windows the MSI and a portable ZIP; both are attached to the workflow run.

## Licence

EUPL-1.2, see [LICENSE](LICENSE).
