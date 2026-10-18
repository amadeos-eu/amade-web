package eu.amadeos.apps.web.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.apps.web.ui.main.BrowserWindow;

import java.util.Locale;

/**
 * amade-web bindings. Theme, language and the other shared services come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class WebModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.apps.web.messages";

    @Override
    protected void configure() {
        bind(BrowserWindow.class).in(Scopes.SINGLETON);
    }

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(WebModule.class, MESSAGES, locale);
    }
}
