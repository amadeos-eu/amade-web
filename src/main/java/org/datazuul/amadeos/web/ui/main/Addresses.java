package org.datazuul.amadeos.web.ui.main;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

/** Turns what is typed into the address field or given on the command line into a URL. */
public final class Addresses {
    /** The installed documentation: {@code /usr/share/doc} on Linux, the home directory elsewhere. */
    public static final String HOME = home();

    private Addresses() {
    }

    /**
     * A URL stays as it is ({@code https://...}, {@code file:...}, {@code about:blank}); an existing file
     * or a path ({@code /...}, {@code ./...}, {@code ~/...}) becomes a {@code file:} URL; {@code localhost}
     * gets {@code http://}, any other host name {@code https://}.
     */
    public static String toUrl(String input) {
        String text = input.strip();
        if (text.isEmpty() || text.contains("://") || hasScheme(text, "about", "file", "data", "mailto")) return text;
        if (text.equals("~") || text.startsWith("~/")) {
            return fileUrl(Path.of(System.getProperty("user.home"), text.substring(1).replaceFirst("^/", "")));
        }
        // An anchor after a path (guide.html#install) is not part of the file name.
        int hash = text.indexOf('#');
        String anchor = hash < 0 ? "" : text.substring(hash);
        Path path = localPath(hash < 0 ? text : text.substring(0, hash));
        if (path != null) return fileUrl(path) + anchor;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.equals("localhost") || lower.startsWith("localhost:") || lower.startsWith("localhost/")
                || lower.startsWith("127.0.0.1")) {
            return "http://" + text;
        }
        return "https://" + text;
    }

    private static String home() {
        Path doc = Path.of("/usr/share/doc");
        return (Files.isDirectory(doc) ? doc : Path.of(System.getProperty("user.home"))).toUri().toString();
    }

    private static boolean hasScheme(String text, String... schemes) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String scheme : schemes) if (lower.startsWith(scheme + ":")) return true;
        return false;
    }

    /** The path if the text names one: it exists, or it is written as a path. */
    private static Path localPath(String text) {
        try {
            Path path = Path.of(text);
            if (path.isAbsolute() || text.startsWith("/") || text.startsWith("./") || text.startsWith("../") || Files.exists(path)) return path;
        } catch (InvalidPathException e) {
            // Not a path, so a host name.
        }
        return null;
    }

    private static String fileUrl(Path path) {
        return path.toAbsolutePath().normalize().toUri().toString();
    }
}
