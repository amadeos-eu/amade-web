package eu.amadeos.apps.web.document;

import eu.amadeos.commons.i18n.Messages;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Turns a URL into something to show. Local HTML is parsed with jsoup (which repairs what browsers
 * forgive) and handed to Flying Saucer as XHTML; directories, plain text, gzip-compressed files and
 * images become generated pages. Web links and other files are not shown here but handed on.
 *
 * <p>Runs on a background thread; it only reads files.
 */
public final class DocumentLoader {
    private static final String XHTML_NAMESPACE = "http://www.w3.org/1999/xhtml";
    private static final Set<String> HTML = Set.of("html", "htm", "xhtml", "shtml");
    private static final Set<String> IMAGES = Set.of("png", "jpg", "jpeg", "gif", "bmp");
    private static final Set<String> TEXT = Set.of("txt", "text", "md", "rst", "log", "conf", "css", "csv",
            "java", "c", "h", "py", "sh", "xml", "json", "yml", "yaml");
    /** Formats with their own applications, even when their first bytes look like text. */
    private static final Set<String> OTHER = Set.of("pdf", "ps", "eps", "svg", "dvi", "epub", "odt", "ods", "odp",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf");
    private static final int TEXT_PROBE = 8192;

    private final Messages msg;

    public DocumentLoader(Messages msg) {
        this.msg = msg;
    }

    /** What a URL leads to. */
    public sealed interface Result permits Page, WebLink, OtherFile {
    }

    /**
     * A page to show.
     *
     * @param url    the URL that was loaded, with {@code index.html} added for directories that have one
     *               and the fragment ({@code #id}) of the request kept
     * @param title  the {@code <title>}, or the file name
     */
    public record Page(org.w3c.dom.Document document, String url, String title) implements Result {
        /** The URL without its fragment: the base for relative links. */
        public String documentUrl() {
            int hash = url.indexOf('#');
            return hash < 0 ? url : url.substring(0, hash);
        }

        /** The id after {@code #}, or null. */
        public String fragment() {
            int hash = url.indexOf('#');
            return hash < 0 || hash == url.length() - 1 ? null : url.substring(hash + 1);
        }
    }

    /** {@code http:}, {@code https:}, {@code mailto:} and other non-file URLs: for the web browser. */
    public record WebLink(String url) implements Result {
    }

    /** A local file this viewer does not show (PDF, office documents, ...): for its application. */
    public record OtherFile(Path file) implements Result {
    }

    public Result load(String url) throws IOException {
        String fragment = "";
        String location = url.strip();
        int hash = location.indexOf('#');
        if (hash >= 0) {
            fragment = location.substring(hash);
            location = location.substring(0, hash);
        }
        if (location.isEmpty() || location.equals("about:blank")) {
            return page(Document.createShell("about:blank"), "about:blank", "");
        }
        URI uri = URI.create(location);
        if (!"file".equalsIgnoreCase(uri.getScheme())) return new WebLink(url.strip());

        Path path = Path.of(uri);
        if (!Files.exists(path)) throw new NoSuchFileException(path.toString());
        if (Files.isDirectory(path)) {
            Path index = path.resolve("index.html");
            if (!Files.isRegularFile(index)) return directory(path);
            path = index;
        }
        String fileUrl = path.toUri().toString();
        String name = path.getFileName().toString();
        byte[] bytes = Files.readAllBytes(path);
        if (name.toLowerCase(Locale.ROOT).endsWith(".gz")) {
            try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                bytes = in.readAllBytes();
            }
            name = name.substring(0, name.length() - 3);
        }
        String extension = extension(name);
        if (HTML.contains(extension)) {
            Document html = Jsoup.parse(new ByteArrayInputStream(bytes), null, fileUrl);
            String title = html.title().isBlank() ? name : html.title().strip();
            return page(html, fileUrl + fragment, title);
        }
        if (IMAGES.contains(extension)) return image(fileUrl, name);
        if (TEXT.contains(extension) || (!OTHER.contains(extension) && looksLikeText(bytes))) {
            return text(fileUrl, name, decode(bytes));
        }
        return new OtherFile(path);
    }

    /** The extension in lower case, empty if there is none (README, changelog). */
    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** No NUL bytes at the start: binary formats have them, text files do not. */
    static boolean looksLikeText(byte[] bytes) {
        int end = Math.min(bytes.length, TEXT_PROBE);
        for (int i = 0; i < end; i++) {
            if (bytes[i] == 0) return false;
        }
        return true;
    }

    /** UTF-8 if the bytes are valid UTF-8, otherwise Latin-1 (older Debian documentation). */
    static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private Page directory(Path dir) throws IOException {
        String url = dir.toUri().toString();
        String title = msg.format("directory.title", dir.toString());
        Document html = Document.createShell(url);
        html.title(title);
        Element body = html.body();
        body.appendElement("h1").text(title);
        Element list = body.appendElement("ul").attr("class", "amade-directory");
        if (dir.getParent() != null) {
            list.appendElement("li").appendElement("a").attr("href", dir.getParent().toUri().toString())
                    .text(msg.get("directory.parent"));
        }
        List<Path> entries;
        try (Stream<Path> stream = Files.list(dir)) {
            entries = stream.sorted(Comparator.comparing((Path p) -> !Files.isDirectory(p))
                    .thenComparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))).toList();
        }
        for (Path entry : entries) {
            String name = entry.getFileName().toString() + (Files.isDirectory(entry) ? "/" : "");
            list.appendElement("li").appendElement("a").attr("href", entry.toUri().toString()).text(name);
        }
        return page(html, url, title);
    }

    private Page text(String url, String name, String content) {
        Document html = Document.createShell(url);
        html.title(name);
        html.body().appendElement("pre").attr("style", "white-space: pre-wrap").text(content);
        return page(html, url, name);
    }

    private Page image(String url, String name) {
        Document html = Document.createShell(url);
        html.title(name);
        html.body().appendElement("img").attr("src", url).attr("alt", name);
        return page(html, url, name);
    }

    /** Scripts never run here; Flying Saucer's default style sheet only matches XHTML elements. */
    private static Page page(Document html, String url, String title) {
        html.select("script").remove();
        Element root = html.selectFirst("html");
        if (root != null) root.attr("xmlns", XHTML_NAMESPACE);
        return new Page(W3CDom.convert(html), url, title);
    }
}
