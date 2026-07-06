package org.dce.ed;

import java.awt.Font;

/**
 * Small abstraction so PreferencesDialog can live-preview settings whether the overlay
 * is hosted by an undecorated pass-through window or a normal decorated window.
 */
public interface OverlayUiPreviewHost {

    boolean isPassThroughEnabled();

    void applyUiFontPreferences();

    void applyUiFontPreview(Font font);

    /** Clear live font preview state and apply {@code savedFont} (used when preferences are cancelled). */
    void revertUiFontLivePreview(Font savedFont);

    void applyOverlayBackgroundFromPreferences(boolean passThroughMode);

    void applyOverlayBackgroundPreview(boolean passThroughMode, int rgb, int transparencyPercent);

    void applyThemeFromPreferences();

    /**
     * Rebuild the System tab table + plan map after Overlay preferences OK (e.g. ship reference mode changed).
     * Also refreshes overlay tab bar visibility (Visible tabs checkboxes).
     */
    default void refreshSystemTabFromSavedPreferences() {
    }

    default void refreshOverlayTabBarFromSavedPreferences() {
    }
}
