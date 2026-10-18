package eu.amadeos.web.bootstrap;

import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.web.ui.main.Addresses;
import eu.amadeos.web.ui.main.BrowserWindow;

/**
 * Documentation viewer of the amadeOS desktop: local HTML (XHTML + CSS 2.1 with Flying Saucer), plain
 * text and gzip-compressed files, and directory listings, in a Swing window. Web links go to the web
 * browser.
 *
 * <pre>
 * amade-web                          opens /usr/share/doc
 * amade-web docs/index.html          opens a local file
 * amade-web https://example.org      hands the link to the web browser
 * </pre>
 */
public final class AmadeWebApplication {
    public static final AppInfo APP = new AppInfo("amade-web", "Webbrowser", "web");

    private AmadeWebApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadeWebApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    /** Opens the URL or file named as first argument, the home page without one. */
    public static void main(String[] args) {
        // Flying Saucer logs every style sheet and font it loads through java.util.logging.
        System.setProperty("xr.util-logging.loggingEnabled", "false");
        String url = args.length == 0 ? Addresses.HOME : Addresses.toUrl(args[0]);
        AmadeApplication.launch(APP, injector -> injector.getInstance(BrowserWindow.class).open(url),
                new WebModule());
    }
}
