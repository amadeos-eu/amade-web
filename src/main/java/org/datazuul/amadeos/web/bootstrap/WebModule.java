package org.datazuul.amadeos.web.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.web.ui.main.BrowserWindow;

import java.util.Locale;

/**
 * amade-web bindings. Theme, language and the other shared services come from
 * {@link org.datazuul.amadeos.commons.app.CommonsModule}.
 */
public final class WebModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "org.datazuul.amadeos.web.messages";

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
