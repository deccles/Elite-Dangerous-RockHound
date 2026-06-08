package org.dce.ed;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Keeps prospector / Polly announcements silent during tests (Maven, IDE, and single-class runs).
 * Registered via {@code META-INF/services/org.junit.jupiter.api.extension.Extension}.
 */
public final class DisableSpeechExtension implements BeforeEachCallback {

    static final String PROPERTY = "edo.test.disableSpeech";

    static {
        TestEnvironment.ensureTestIsolation();
        System.setProperty(PROPERTY, "true");
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        boolean allow = context.getTestMethod()
                .map(m -> m.isAnnotationPresent(AllowSpeechForTest.class))
                .orElse(false);
        System.setProperty(PROPERTY, allow ? "false" : "true");
    }
}
