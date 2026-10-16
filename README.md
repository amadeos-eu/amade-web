# amade-web

Web viewer for amadeOS: the JavaFX WebView (WebKit) inside a Swing window, themed through
[amade-commons](https://github.com/datazuul/amade-commons). Two questions to answer:

- Does it run web applications such as [alexana.org](https://alexana.org) (Angular)?
- Is it good enough to show local documentation (Help Viewer)?

## Run

```sh
./gradlew run                                   # opens https://alexana.org
./gradlew run --args="https://example.org"      # a URL
./gradlew run --args="docs/index.html"          # a local file
java -jar build/libs/amade-web-*.jar [URL|file]
```

JavaScript console messages of the page are written to the log (`console ...` lines), so errors
of a web application show up in the terminal.

## Notes

- JavaFX 21 LTS (`21.0.12`), WebKit 623.1, user agent identifies as Safari 18.4.
- JavaFX runs from the class path (fat jar). That gives the warning *Unsupported JavaFX
  configuration*, but it also makes the internal `WebConsoleListener` reachable.
- WebView has no service workers and only partial WebGL support.
