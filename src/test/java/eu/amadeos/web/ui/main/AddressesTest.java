package eu.amadeos.web.ui.main;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AddressesTest {
    @Test
    void urlsStayAsTheyAre() {
        assertEquals("https://alexana.org/item/1", Addresses.toUrl("https://alexana.org/item/1"));
        assertEquals("http://example.org", Addresses.toUrl("  http://example.org "));
        assertEquals("file:///usr/share/doc/index.html", Addresses.toUrl("file:///usr/share/doc/index.html"));
        assertEquals("about:blank", Addresses.toUrl("about:blank"));
    }

    @Test
    void hostNamesGetHttps() {
        assertEquals("https://alexana.org", Addresses.toUrl("alexana.org"));
        assertEquals("https://www.example.org/a?b=c", Addresses.toUrl("www.example.org/a?b=c"));
    }

    @Test
    void localhostGetsHttp() {
        assertEquals("http://localhost:631/", Addresses.toUrl("localhost:631/"));
        assertEquals("http://localhost", Addresses.toUrl("localhost"));
        assertEquals("http://127.0.0.1:8080", Addresses.toUrl("127.0.0.1:8080"));
    }

    @Test
    void pathsBecomeFileUrls(@TempDir Path dir) throws IOException {
        Path page = Files.writeString(dir.resolve("index.html"), "<p>Hello</p>");
        assertEquals(page.toUri().toString(), Addresses.toUrl(page.toString()));
        assertEquals(Path.of("/usr/share/doc/missing.html").toUri().toString(),
                Addresses.toUrl("/usr/share/doc/missing.html"));
        assertEquals(Path.of("docs/x.html").toAbsolutePath().toUri().toString(), Addresses.toUrl("./docs/x.html"));
        assertEquals(Path.of(System.getProperty("user.home"), "docs").toUri().toString(), Addresses.toUrl("~/docs"));
    }

    @Test
    void anAnchorAfterAPathStaysAnAnchor(@TempDir Path dir) throws IOException {
        Path page = Files.writeString(dir.resolve("guide.html"), "<h2 id='install'>Install</h2>");
        assertEquals(page.toUri() + "#install", Addresses.toUrl(page + "#install"));
    }

    @Test
    void emptyStaysEmpty() {
        assertEquals("", Addresses.toUrl("   "));
    }
}
