package eu.amadeos.apps.web.document;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.apps.web.bootstrap.WebModule;
import eu.amadeos.apps.web.document.DocumentLoader.OtherFile;
import eu.amadeos.apps.web.document.DocumentLoader.Page;
import eu.amadeos.apps.web.document.DocumentLoader.WebLink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentLoaderTest {
    private final DocumentLoader loader =
            new DocumentLoader(Messages.load(WebModule.class, WebModule.MESSAGES, Locale.ENGLISH));

    @TempDir
    Path dir;

    @Test
    void htmlIsRepairedIntoXhtmlWithoutScripts() throws IOException {
        Path file = Files.writeString(dir.resolve("guide.html"),
                "<title>Guide</title><p>One<p>Two<script>alert(1)</script><br>");
        Page page = assertInstanceOf(Page.class, loader.load(file.toUri() + "#install"));

        assertEquals("Guide", page.title());
        assertEquals(file.toUri() + "#install", page.url());
        assertEquals(file.toUri().toString(), page.documentUrl());
        assertEquals("install", page.fragment());
        assertEquals("http://www.w3.org/1999/xhtml", page.document().getDocumentElement().getNamespaceURI());
        assertEquals(2, page.document().getElementsByTagName("p").getLength());
        assertEquals(0, page.document().getElementsByTagName("script").getLength());
    }

    @Test
    void aDirectoryShowsItsIndexPage() throws IOException {
        Files.writeString(dir.resolve("index.html"), "<title>Start</title>");
        Page page = assertInstanceOf(Page.class, loader.load(dir.toUri().toString()));

        assertEquals("Start", page.title());
        assertEquals(dir.resolve("index.html").toUri().toString(), page.url());
    }

    @Test
    void aDirectoryWithoutIndexIsListedWithFoldersFirst() throws IOException {
        Files.createDirectory(dir.resolve("zeta"));
        Files.writeString(dir.resolve("Alpha.txt"), "a");
        Files.writeString(dir.resolve("beta.html"), "b");
        Page page = assertInstanceOf(Page.class, loader.load(dir.toUri().toString()));

        assertEquals("Index of " + dir, page.title());
        assertEquals(List.of("Parent directory", "zeta/", "Alpha.txt", "beta.html"), linkTexts(page));
    }

    @Test
    void compressedReadmesAndPlainTextArePreformatted() throws IOException {
        Path readme = dir.resolve("README.Debian.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(readme))) {
            out.write("Größe <b>not bold</b>\n".getBytes(StandardCharsets.UTF_8));
        }
        Page page = assertInstanceOf(Page.class, loader.load(readme.toUri().toString()));

        assertEquals("README.Debian", page.title());
        NodeList pre = page.document().getElementsByTagName("pre");
        assertEquals(1, pre.getLength());
        assertEquals("Größe <b>not bold</b>\n", pre.item(0).getTextContent());
    }

    @Test
    void latin1TextIsReadAsLatin1() {
        byte[] latin1 = "Größe".getBytes(StandardCharsets.ISO_8859_1);
        assertEquals("Größe", DocumentLoader.decode(latin1));
    }

    @Test
    void imagesGetAPage() throws IOException {
        Path png = Files.write(dir.resolve("diagram.png"), new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0});
        Page page = assertInstanceOf(Page.class, loader.load(png.toUri().toString()));

        Element img = (Element) page.document().getElementsByTagName("img").item(0);
        assertEquals(png.toUri().toString(), img.getAttribute("src"));
    }

    @Test
    void binaryFilesAndPdfsGoToTheirApplications() throws IOException {
        Path pdf = Files.writeString(dir.resolve("manual.pdf"), "%PDF-1.4 looks like text");
        Path binary = Files.write(dir.resolve("data.bin"), new byte[]{1, 0, 2});

        assertEquals(new OtherFile(pdf), loader.load(pdf.toUri().toString()));
        assertEquals(new OtherFile(binary), loader.load(binary.toUri().toString()));
    }

    @Test
    void webLinksGoToTheWebBrowser() throws IOException {
        assertEquals(new WebLink("https://alexana.org/item/1#x"), loader.load("https://alexana.org/item/1#x"));
        assertEquals(new WebLink("mailto:info@example.org"), loader.load("mailto:info@example.org"));
    }

    @Test
    void aMissingFileIsAnError() {
        assertThrows(NoSuchFileException.class, () -> loader.load(dir.resolve("missing.html").toUri().toString()));
    }

    @Test
    void textIsRecognisedByTheAbsenceOfNulBytes() {
        assertTrue(DocumentLoader.looksLikeText("changelog\n".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(DocumentLoader.looksLikeText(new byte[]{'E', 'L', 'F', 0}));
        assertEquals("", DocumentLoader.extension("README"));
        assertEquals("gz", DocumentLoader.extension("changelog.Debian.GZ"));
    }

    @Test
    void aBlankPageHasNoFragment() throws IOException {
        Page page = assertInstanceOf(Page.class, loader.load("about:blank"));
        assertNull(page.fragment());
    }

    private static List<String> linkTexts(Page page) {
        NodeList links = page.document().getElementsByTagName("a");
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < links.getLength(); i++) texts.add(links.item(i).getTextContent());
        return texts;
    }
}
