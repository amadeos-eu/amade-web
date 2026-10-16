# amade-web

Web browser and documentation viewer of the [amadeOS](https://github.com/datazuul/amadeos) desktop:
the JavaFX WebView (WebKit) in a Motif/CDE-styled Swing window, themed through
[amade-commons](https://github.com/datazuul/amade-commons).

![amade-web showing alexana.org](docs/screenshot.png)

It runs modern single-page applications such as [alexana.org](https://alexana.org) (Angular) and
shows local HTML documentation.

## Using it

```sh
amade-web                          # opens the home page
amade-web https://example.org      # a URL
amade-web example.org              # a host name gets https://, localhost gets http://
amade-web docs/index.html          # a local file
```

| Key | Action |
|---|---|
| Ctrl+L | Address field |
| Ctrl+O | Open a local file |
| Alt+Left / Alt+Right | Back / forward |
| Alt+Home | Home page |
| F5 / Esc | Reload / stop |
| Ctrl+Plus / Ctrl+Minus / Ctrl+0 | Zoom in / out / actual size |
| Ctrl+Q | Exit |

Links that open a new window (`target="_blank"`, `window.open()`) stay in the same window.
The JavaScript console of the page is written to the log (`console ...` lines on standard output),
so errors of a web application show up in the terminal.

### Languages

English, German, French, Spanish and Italian. The application follows the desktop language;
*Options > Language* sets its own, applied at the next start.

### Engine

JavaFX 27 with WebKit; the user agent identifies as Safari 18.4. Video plays through the system's
FFmpeg (`libavcodec`), for which JavaFX 27 has plugins up to FFmpeg 8 (Debian trixie has 7.1).

What WebView does not have, so pages that need it do not work:

- WebAssembly (e.g. the OCR text in the alexana IIIF viewer, see its
  [roadmap](https://github.com/datazuul/alexana-ils-angular-libs/blob/main/ROADMAP.md))
- Media Source Extensions: players built on hls.js or dash.js, YouTube and most streaming sites
- WebM, VP8/VP9 and Ogg; MP4 (H.264/AAC), MP3 and HLS play
- WebGL, service workers, `CompressionStream`, downloads and extensions

JavaFX runs from the class path (fat jar). That gives the start-up warning *Unsupported JavaFX
configuration*, but also makes the internal `WebConsoleListener` reachable for the console log.

## Building and testing

Requirements: Java 25 JDK; `dpkg-deb` for the Debian package.

`amade-commons` is taken from JitPack. If it is checked out next to this repository
(`../amade-commons`), Gradle builds that checkout instead.

```bash
./gradlew test                 # unit tests
./gradlew run                  # start the browser
./gradlew run --args="docs/index.html"
./gradlew deb                  # build/distributions/amade-web_0.1.0_amd64.deb
./gradlew installToAmadeOs     # copy the .deb into ../amadeos/config/packages.chroot/
```

The JavaFX jars contain native libraries (glass, prism, WebKit, media), so the build takes those of
the machine it runs on. The Debian package is therefore `amd64` (or `arm64` when built there), not
`all`, and the jar is about 60 MB.

### Runtime packages

The Debian package depends on a Java 25 runtime and GTK 3 (`libgtk-3-0t64 | libgtk-3-0`), which the
JavaFX libraries link against. It installs `amade-web.desktop` (`Categories=Network;WebBrowser;`)
and registers for `text/html` and `http`/`https` links, so `xdg-open https://...` and the CUPS web
administration in the front panel can open in amade-web.

### Windows

`./gradlew windowsInstaller` builds an installer with jpackage that includes a Java runtime, so Windows
users need no Java. It runs only on Windows, which also makes the build pick the Windows JavaFX
libraries; MSI and EXE also need [WiX Toolset 3](https://github.com/wixtoolset/wix3/releases) on the
`PATH` (JDK 25 also takes WiX 4/5).

```bat
gradlew windowsInstaller                            :: build\windows\msi\amade-web-0.1.0.msi
gradlew windowsInstaller -PinstallerType=exe        :: setup .exe
gradlew windowsInstaller -PinstallerType=app-image  :: folder with amade-web.exe, no WiX needed
```

The `windows` job in CI builds the MSI and a portable ZIP of the app image on every push; both are
in the `amade-web-windows` artifact of the workflow run. The installer is not code-signed, so
SmartScreen warns on first start.

## Roadmap

- [x] WebView with history, address field, zoom and status bar
- [x] English, German, French, Spanish and Italian
- [ ] Log the full JavaScript errors (`console.error` with objects), not only their first argument
- [ ] `mailto:` links to amade-mail, downloads to `~/Downloads`
- [ ] Bookmarks and a home page setting
- [ ] Help Viewer mode: table of contents and search for local documentation

## License

[EUPL-1.2](LICENSE)
