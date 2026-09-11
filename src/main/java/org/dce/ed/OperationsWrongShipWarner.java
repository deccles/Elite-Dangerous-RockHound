package org.dce.ed;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.LoadGameEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.logreader.event.StatusEvent;

import com.google.gson.JsonObject;

/**
 * Warns when an Operations lobby {@code WingJoin} happens in the wrong ship.
 * <p>
 * Operations: last non-zero {@code GuiFocus} was 1 (right panel), or journal {@code Music}
 * {@code MainMenu} while the last observed focus is not comms/contacts (2/3).
 * Friend wing: last observed {@code GuiFocus} is 2 or 3.
 */
public final class OperationsWrongShipWarner {

    public static final String WRONG_SHIP_SPEECH =
            "Did you forget your Operations ship again Commander?";

    static final int GUI_FOCUS_RIGHT_PANEL = 1;
    static final int GUI_FOCUS_LEFT_PANEL = 2;
    static final int GUI_FOCUS_COMMS = 3;

    private static final OperationsWrongShipWarner INSTANCE = new OperationsWrongShipWarner();

    public static OperationsWrongShipWarner getInstance() {
        return INSTANCE;
    }

    private volatile int savedNonZeroGuiFocus = -1;
    private volatile int lastObservedGuiFocus = -1;
    private volatile String currentShipType = "";
    private volatile boolean musicMainMenu;
    private final AtomicBoolean warnedThisLobby = new AtomicBoolean(false);
    private volatile Runnable warningCallback;

    private OperationsWrongShipWarner() {
    }

    /**
     * Overlay status line, e.g. {@code Wrong ship for Operations, expected "Anaconda"} or
     * {@code … expected "Anaconda", "Dolphin" or "Orca"}.
     */
    public static String wrongShipStatusMessage(List<String> preferredShipDisplayNames) {
        List<String> ships = new ArrayList<>();
        if (preferredShipDisplayNames != null) {
            for (String name : preferredShipDisplayNames) {
                if (name != null && !name.isBlank()) {
                    ships.add("\"" + name.trim() + "\"");
                }
            }
        }
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < ships.size(); i++) {
            if (i > 0) {
                expected.append(i == ships.size() - 1 ? " or " : ", ");
            }
            expected.append(ships.get(i));
        }
        return "Wrong ship for Operations, expected " + expected;
    }

    public void setWarningCallback(Runnable callback) {
        this.warningCallback = callback;
    }

    /** Clears saved focus, ship, music, and lobby latch (tests / session reset). */
    void resetState() {
        savedNonZeroGuiFocus = -1;
        lastObservedGuiFocus = -1;
        currentShipType = "";
        musicMainMenu = false;
        warnedThisLobby.set(false);
    }

    /**
     * @return {@code true} when a wrong-ship Operations warning should fire
     */
    public boolean applyJournalEvent(EliteLogEvent event) {
        if (event == null) {
            return false;
        }
        if (event instanceof StatusEvent se) {
            rememberGuiFocus(se.getGuiFocus());
            return false;
        }
        if (event instanceof LoadGameEvent lg) {
            rememberShipType(lg.getShip());
            return false;
        }
        if (event instanceof LoadoutEvent lo) {
            rememberShipType(lo.getShip());
            return false;
        }
        if (event.getType() == EliteEventType.MUSIC) {
            rememberMusicTrack(musicTrack(event));
            return false;
        }
        if (!isWingJoin(event)) {
            return false;
        }
        return onWingJoin();
    }

    void rememberGuiFocus(int guiFocus) {
        if (guiFocus != 0) {
            savedNonZeroGuiFocus = guiFocus;
        }
        if (guiFocus == GUI_FOCUS_RIGHT_PANEL && lastObservedGuiFocus != GUI_FOCUS_RIGHT_PANEL) {
            warnedThisLobby.set(false);
        }
        lastObservedGuiFocus = guiFocus;
    }

    void rememberShipType(String shipType) {
        if (shipType != null && !shipType.isBlank()) {
            currentShipType = shipType.trim();
        }
    }

    void rememberMusicTrack(String track) {
        if (track == null || track.isBlank()) {
            return;
        }
        if ("MainMenu".equals(track)) {
            musicMainMenu = true;
            warnedThisLobby.set(false);
        } else if (!"NoTrack".equals(track)) {
            musicMainMenu = false;
        }
    }

    boolean onWingJoin() {
        if (!OverlayPreferences.isOperationsWrongShipWarnEnabled()) {
            return false;
        }
        if (isFriendPanelFocus(lastObservedGuiFocus)) {
            return false;
        }
        if (savedNonZeroGuiFocus != GUI_FOCUS_RIGHT_PANEL && !musicMainMenu) {
            return false;
        }
        ensureShipKnown();
        if (warnedThisLobby.get()) {
            return false;
        }
        List<String> preferred = OverlayPreferences.getOperationsPreferredShips();
        if (preferred.isEmpty() || currentShipType.isBlank()) {
            return false;
        }
        for (String allowed : preferred) {
            if (ShipTypeNames.sameType(currentShipType, allowed)) {
                return false;
            }
        }
        if (!warnedThisLobby.compareAndSet(false, true)) {
            return false;
        }
        Runnable cb = warningCallback;
        if (cb != null) {
            cb.run();
        }
        return true;
    }

    private void ensureShipKnown() {
        if (!currentShipType.isBlank()) {
            return;
        }
        LoadoutEvent loadout = EliteOverlayTabbedPane.getLatestLoadout();
        if (loadout != null) {
            rememberShipType(loadout.getShip());
        }
    }

    int savedNonZeroGuiFocus() {
        return savedNonZeroGuiFocus;
    }

    String currentShipType() {
        return currentShipType;
    }

    boolean musicMainMenu() {
        return musicMainMenu;
    }

    static boolean isFriendPanelFocus(int guiFocus) {
        return guiFocus == GUI_FOCUS_LEFT_PANEL || guiFocus == GUI_FOCUS_COMMS;
    }

    static boolean isWingJoin(EliteLogEvent event) {
        if (event == null) {
            return false;
        }
        if (event.getType() == EliteEventType.WING_JOIN) {
            return true;
        }
        JsonObject raw = event.getRawJson();
        if (raw == null || !raw.has("event") || raw.get("event").isJsonNull()) {
            return false;
        }
        try {
            return "WingJoin".equals(raw.get("event").getAsString());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String musicTrack(EliteLogEvent event) {
        JsonObject raw = event.getRawJson();
        if (raw == null || !raw.has("MusicTrack") || raw.get("MusicTrack").isJsonNull()) {
            return "";
        }
        try {
            return raw.get("MusicTrack").getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }
}
