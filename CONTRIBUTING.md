# Contributing to amade-writepad

## Building and testing

You need JDK 25. With a checkout of [amade-commons](https://github.com/datazuul/amade-commons) next to this
one, `settings.gradle` builds against that checkout; otherwise Gradle takes the version from JitPack named in
`build.gradle`.

```sh
./gradlew test                        # the Swing tests need a display: xvfb-run ./gradlew test
./gradlew run --args=--sample         # with sample text
./gradlew deb                         # build/distributions/amade-writepad_<version>_all.deb
```

The format tests compare with files LibreOffice wrote (`src/test/resources/`). The `.doc` tests run
LibreOffice (`soffice`) and are skipped where it is not installed; the Linux CI job installs it, so there
they always run. To check a format change by hand, convert our output with LibreOffice
(`soffice --headless --convert-to odt|docx|rtf|pdf file`) and read LibreOffice's output back in.

## Design decisions

### File formats without Apache POI (2026-10-21)

amade-writepad reads and writes its formats with its own code: ODT and DOCX with StAX from the JDK (both are
ZIP files with XML inside), RTF with its own tokenizer and writer, plain text directly. The old binary Word
format (`.doc`) is converted by LibreOffice: opening runs `soffice --headless --convert-to docx` in the
background and reads the DOCX; saving writes DOCX and converts it to `.doc` the same way. Without LibreOffice,
`.doc` files cannot be opened and *Save As* does not offer `.doc`. The amadeOS live system installs
LibreOffice Writer for this.

**Why not Apache POI:** `poi-ooxml` brings POI, the OOXML schemas, XMLBeans and several Commons libraries,
about 19 MB of jars (POI 5.4.1), four times the size of the whole application package (4.4 MB). Its API works on a
DOM of the whole document, and it pulls in Log4j. DOCX needs none of that: the parts amade-writepad uses
(`document.xml`, `styles.xml`, `numbering.xml`) are about as simple as ODT's. Only `.doc` really needs a
library, and LibreOffice reads and writes it better than POI's HWPF does.

**The alternative, if it is ever needed:** Apache POI (Apache-2.0). `poi` with `poi-scratchpad` (HWPF,
about 9 MB of jars with their dependencies) could read `.doc` without LibreOffice, for example on Windows without
LibreOffice installed; it cannot write `.doc` reliably. `poi-ooxml` (XWPF) could replace our DOCX code. A
`DocumentFormat` built on POI would plug into `FormatRegistry` like the others.

### No ODF library

ODT is read and written with StAX, not with odfdom: odfdom only wraps the schema (resolving styles is still
our work), works on a DOM, brings many dependencies and is not packaged in Debian. Round-trip tests with
LibreOffice files and the ODF validator keep our output correct.
