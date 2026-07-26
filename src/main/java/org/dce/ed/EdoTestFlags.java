package org.dce.ed;

/**
 * Runtime flags set by Surefire / {@link org.dce.ed.TestEnvironment} so unit tests do not drive real UI
 * persistence (window bounds, floating-tab layout) or interactive dialogs.
 * <p>
 * <b>Developer warning:</b> Java {@link java.util.prefs.Preferences} is the live OS user store shared with the
 * desktop app. Tests that write prefs without isolation have repeatedly reset window / floating-tab layout.
 * Keep {@link #ISOLATE_UI_PROPERTY} enabled for Surefire and IDE runs; new persist paths must no-op when
 * {@link #isolateUi()} is {@code true}. See {@code .cursor/rules/junit-live-preferences.mdc}.
 */
public final class EdoTestFlags {

    /**
     * When {@code true}, skip tab-layout restore/persist and overlay bounds preference writes.
     * Set by Surefire ({@code pom.xml}) and by {@link TestEnvironment} for IDE runners.
     */
    public static final String ISOLATE_UI_PROPERTY = "edo.test.isolateUi";

    private EdoTestFlags() {
    }

    public static boolean isolateUi() {
        return Boolean.getBoolean(ISOLATE_UI_PROPERTY);
    }
}
