package org.datazuul.amadeos.web.bootstrap;

import org.datazuul.amadeos.commons.app.AmadeApplication;
import org.datazuul.amadeos.commons.app.AppInfo;
import org.datazuul.amadeos.web.ui.main.Addresses;
import org.datazuul.amadeos.web.ui.main.BrowserWindow;

/**
 * Web browser and documentation viewer of the amadeOS desktop: the JavaFX WebView (WebKit) in a Swing
 * window.
 *
 * <pre>
 * amade-web                          opens the home page
 * amade-web https://example.org      opens a URL
 * amade-web docs/index.html          opens a local file
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
        String url = args.length == 0 ? Addresses.HOME : Addresses.toUrl(args[0]);
        AmadeApplication.launch(APP, injector -> injector.getInstance(BrowserWindow.class).open(url),
                new WebModule());
    }
}
