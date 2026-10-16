package org.datazuul.amadeos.web;

import org.datazuul.amadeos.commons.app.AmadeApplication;
import org.datazuul.amadeos.commons.app.AppInfo;

import java.nio.file.Path;

/**
 * Test bed for the JavaFX WebView (WebKit) inside a Swing window: can it run web applications such as
 * alexana.org (Angular), and is it good enough to show local documentation?
 *
 * <pre>
 * amade-web                          opens https://alexana.org
 * amade-web https://example.org      opens a URL
 * amade-web docs/index.html          opens a local file
 * </pre>
 */
public final class AmadeWebApplication {
    public static final AppInfo APP = new AppInfo("amade-web", "Web Viewer", "web");
    static final String DEFAULT_URL = "https://alexana.org";

    private AmadeWebApplication() {
    }

    public static void main(String[] args) {
        String url = args.length == 0 ? DEFAULT_URL : toUrl(args[0]);
        AmadeApplication.launch(APP, injector -> new WebViewWindow(APP.title()).open(url));
    }

    /** A URL stays as it is; anything else is taken as a local file. */
    static String toUrl(String arg) {
        if (arg.contains("://") || arg.startsWith("about:")) return arg;
        return Path.of(arg).toAbsolutePath().normalize().toUri().toString();
    }
}
