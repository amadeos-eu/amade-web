package org.datazuul.amadeos.web.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebModuleTest {
    @Test
    void appInfo() {
        assertEquals("web", AmadeWebApplication.APP.configName());
        assertEquals("amade-web", AmadeWebApplication.APP.id());
        assertEquals("dev", AmadeWebApplication.version());
    }
}
