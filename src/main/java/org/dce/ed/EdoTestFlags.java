package org.dce.ed;

/**
 * Runtime flags set by Surefire / {@code TestEnvironment} so unit tests do not drive real UI
 * persistence (window bounds, floating-tab layout) or interactive dialogs.
 */
public final class EdoTestFlags {

    /** When {@code true}, skip tab-layout restore/persist and overlay bounds preference writes. */
    public static final String ISOLATE_UI_PROPERTY = "edo.test.isolateUi";

    private EdoTestFlags() {
    }

    public static boolean isolateUi() {
        return Boolean.getBoolean(ISOLATE_UI_PROPERTY);
    }
}
