#Requires AutoHotkey v2.0
#SingleInstance Force
#UseHook

; ============================================================
; Elite Dangerous throttle translator (AHK v2)
; - Reads Status.json: GuiFocus + OnFoot
; - Translation ON only when: Elite active, GuiFocus==0, OnFoot==false, no modifier buttons held, no GUI-assume cooldown
; - Joystick buttons:
;     1Joy6 -> Ctrl+Alt+W (hold)
;     1Joy5 -> Ctrl+Alt+S (hold)
; - 25% snap + long press:
;     Home/PgUp tap -> +25%, hold -> 100%
;     End/PgDn  tap -> -25%, hold -> 0%
;     Uses Ctrl+Alt+Numpad presets (safe try/finally)
; - Visual indicators:
;     Tooltip #1: XLT: ON/OFF + GF + OnFoot
;     Tooltip #3: CA+W or CA+S when script is injecting throttle chord
; - F12 suspend (always)
; ============================================================

; -------------------------
; Window detection
; -------------------------
isEliteActive() {
    return WinActive("ahk_exe EliteDangerous64.exe")
        || WinActive("ahk_exe EliteDangerousOdyssey64.exe")
        || WinActive("ahk_exe EliteDangerous.exe")
        || WinActive("ahk_exe EliteDangerous32.exe")
}

; -------------------------
; Config
; -------------------------
global tickMs := 20
global pollMs := 10
global longPressMs := 350

; CALIBRATION:
; Time (ms) holding throttle-up from 0% -> 100% in Elite (tweak once)
global timeToFullMs := 1600

; Modifier buttons (held => translation OFF, combos passthrough)
; NOTE: When using reWASD with a virtual controller, AutoHotkey often can no longer see
; DirectInput joystick buttons (1Joy*). In that setup, map your physical modifier buttons
; to unused keyboard keys (e.g. F15/F16) and list them here.
global modifierButtons := []

; Throttle buttons (joystick)
; Throttle input sources:
; - If AHK can see your physical joystick buttons, keep using 1Joy5/1Joy6.
; - If reWASD is enabled and AHK can't see 1Joy*, map your physical buttons to F13/F14
;   (or any unused keys) and set these instead.
global driveDownButton := "F13" ; -> Ctrl+Alt+S (flight), F15 (GUI)
global driveUpButton   := "F14" ; -> Ctrl+Alt+W (flight), F16 (GUI)
global guiDriveDownKey := "q"
global guiDriveUpKey   := "e"
global guiTapMs := 35
global guiDebounceMs := 150

; Keys used for chord base (bind these in ELITE ship throttle to Ctrl+Alt+W / Ctrl+Alt+S)
global throttleUpBaseKey := "w"
global throttleDownBaseKey := "s"

; Status.json gating
global statusJsonPath := EnvGet("USERPROFILE") "\Saved Games\Frontier Developments\Elite Dangerous\Status.json"
global gateOnGuiFocus := true
global gateOnFoot := true

; GUI-assume cooldown (pre-emptive: when you hit modifier/X buttons, briefly force OFF)
; Same guidance as modifierButtons: prefer keyboard keys when using reWASD.
global guiAssumeJoyButtons := []
global guiAssumeMs := 900
global guiAssumeUntilTick := 0

; -------------------------
; Indicators
; -------------------------
global showXltIndicator := false
global xltTipId := 1
global xltTipX := A_ScreenWidth - 200
global xltTipY := 10

global showEmitIndicator := true
global emitTipId := 3
global emitTipX := A_ScreenWidth - 200
global emitTipY := 30

; -------------------------
; Internal state
; -------------------------
global throttlePct := 0.0          ; 0..100 estimated continuous throttle
global wDown := false
global sDown := false
global wsLastTick := 0

; Script injection flags (used for indicator)
global sentW := false
global sentS := false

; Shared chord modifier hold counter (prevents Ctrl/Alt leaking or popping early)
global chordHoldCount := 0

global injectedCtrlAlt := false
; Status.json fields
global lastGuiFocus := -1
global lastOnFoot := false
global statusReadOk := false
global guiDriveUpSent := false
global guiDriveDownSent := false
global lastGuiDriveTick := 0

; Joy edge tracking
global joyPrev := Map()

; Step-key tracking
global stepKeyDown := Map()
global stepKeyLongFired := Map()
global stepTimerKey := ""

; Output queue
global actionQueue := []
global queueRunning := false

; Elite active transition tracking (prevents key-up spam outside the game)
global eliteActivePrev := false

; Paste de-bounce (prevents double/triple paste in Elite UI fields)
global pasteDebounceMs := 250
global lastPasteTick := 0

ensureEliteActiveContext() {
    global eliteActivePrev, joyPrev
    global xltTipId, emitTipId

    active := isEliteActive()

    if (!active) {
        ; If we just left Elite, do one safety release.
        if (eliteActivePrev) {
            hardReleaseWS()
            joyPrev.Clear()
            try {
                ToolTip(,,, xltTipId)
                ToolTip(,,, emitTipId)
                ToolTip(,,, 2)
            } catch {
            }
        }

        eliteActivePrev := false
        return false
    }

    eliteActivePrev := true
    return true
}


; -------------------------
; Exit safety: never leave modifiers “down”
; -------------------------
OnExit(ReleaseAllKeys)

ReleaseAllKeys(*) {
    global chordHoldCount, injectedCtrlAlt
    try {
        ToolTip(,,, xltTipId)
        ToolTip(,,, emitTipId)
        ToolTip(,,, 2)
    } catch {
    }

    chordHoldCount := 0
    SendEvent "{" throttleUpBaseKey " up}{" throttleDownBaseKey " up}"

    if (injectedCtrlAlt) {
        if (!GetKeyState("Alt", "P"))
            SendEvent "{Alt up}"
        if (!GetKeyState("Ctrl", "P"))
            SendEvent "{Ctrl up}"
        injectedCtrlAlt := false
    }

    if (!GetKeyState("Shift", "P"))
        SendEvent "{Shift up}"
    if (!GetKeyState("LWin", "P"))
        SendEvent "{LWin up}"
    if (!GetKeyState("RWin", "P"))
        SendEvent "{RWin up}"

}

; -------------------------
; Gating helpers
; -------------------------
modifierHeld() {
    global modifierButtons
    for btn in modifierButtons {
        if (GetKeyState(btn, "P"))
            return true
    }
    return false
}

isGuiAssumed() {
    global guiAssumeUntilTick
    return A_TickCount < guiAssumeUntilTick
}

translationAllowed() {
    global gateOnGuiFocus, gateOnFoot, statusReadOk, lastGuiFocus, lastOnFoot

    if (!isEliteActive())
        return false

    if (A_IsSuspended)
        return false

    ; If user is holding keyboard modifiers (copy/paste, alt-tab, etc), don't inject anything.
    if (GetKeyState("Ctrl", "P")
        || GetKeyState("Alt", "P")
        || GetKeyState("Shift", "P")
        || GetKeyState("LWin", "P")
        || GetKeyState("RWin", "P")) {
        return false
    }

    if (modifierHeld())
        return false

    if (isGuiAssumed())
        return false

    ; If we can't reliably read Status.json, be conservative and do NOT inject keys.
    ; This prevents "double paste" and other UI-field weirdness when GuiFocus isn't available.
    if (gateOnGuiFocus) {
        if (!statusReadOk)
            return false
        if (lastGuiFocus > 0)
            return false
    }

    if (gateOnFoot && statusReadOk && lastOnFoot)
        return false

    return true
}

isEliteGuiFocusActive() {
    global gateOnGuiFocus, statusReadOk, lastGuiFocus
    if (!isEliteActive())
        return false
    return gateOnGuiFocus && statusReadOk && (lastGuiFocus > 0)
}

sendGuiDriveDown(which) {
    global guiDriveUpKey, guiDriveDownKey, guiTapMs, guiDebounceMs, lastGuiDriveTick
    if (A_TickCount - lastGuiDriveTick < guiDebounceMs)
        return
    lastGuiDriveTick := A_TickCount

    if (which = "up") {
        SendEvent "{" guiDriveUpKey " down}"
        Sleep guiTapMs
        SendEvent "{" guiDriveUpKey " up}"
    } else {
        SendEvent "{" guiDriveDownKey " down}"
        Sleep guiTapMs
        SendEvent "{" guiDriveDownKey " up}"
    }
}

sendGuiDriveUp(which) {
    ; No-op: GUI drive keys are tapped on button-down.
}

; -------------------------
; Paste handling (Elite UI fields)
; -------------------------
elitePasteOnce() {
    global pasteDebounceMs, lastPasteTick
    global gateOnGuiFocus, statusReadOk, lastGuiFocus

    ; Debounce: ignore rapid repeats (key repeat / double trigger)
    if (A_TickCount - lastPasteTick < pasteDebounceMs)
        return
    lastPasteTick := A_TickCount

    ; In Elite UI screens (GalaxyMap/SystemMap/Station menus) the normal Ctrl+V sometimes double/triple fires.
    ; When GuiFocus>0 (or Status.json is not readable), inject the clipboard text directly once.
    if (gateOnGuiFocus && (!statusReadOk || lastGuiFocus > 0)) {
        t := A_Clipboard
        if (t != "")
            SendText(t)
        else
            Send "^v"
        return
    }

    ; Otherwise, do the normal paste
    Send "^v"
}

; -------------------------
; Chord send helpers (Ctrl+Alt held safely)
; -------------------------
chordModsDown() {
    global chordHoldCount, injectedCtrlAlt
    chordHoldCount += 1
    if (chordHoldCount = 1) {
        injectedCtrlAlt := true
        SendEvent "{Ctrl down}"
        Sleep 2
        SendEvent "{Alt down}"
    }
}

chordModsUp() {
    global chordHoldCount, injectedCtrlAlt

    if (chordHoldCount <= 0) {
        chordHoldCount := 0
        injectedCtrlAlt := false
        if (!GetKeyState("Alt", "P"))
            SendEvent "{Alt up}"
        if (!GetKeyState("Ctrl", "P"))
            SendEvent "{Ctrl up}"
        return
    }

    chordHoldCount -= 1
    if (chordHoldCount = 0) {
        if (!GetKeyState("Alt", "P"))
            SendEvent "{Alt up}"
        Sleep 2
        if (!GetKeyState("Ctrl", "P"))
            SendEvent "{Ctrl up}"
        injectedCtrlAlt := false
    }
}

sendThrottleChordDown(baseKey) {
    chordModsDown()
    Sleep 2
    SendEvent "{" baseKey " down}"
}

sendThrottleChordUp(baseKey) {
    SendEvent "{" baseKey " up}"
    Sleep 2
    chordModsUp()
}

; -------------------------
; Hard release (kills any chance of stuck injected keys)
; -------------------------
hardReleaseWS() {
    global sentW, sentS, wDown, sDown, wsLastTick
    global throttleUpBaseKey, throttleDownBaseKey, chordHoldCount, injectedCtrlAlt

    ; Only release chords that this script believes it has pressed,
    ; to avoid cancelling the user's own W/S key holds.
    if (sentW)
        sendThrottleChordUp(throttleUpBaseKey)
    if (sentS)
        sendThrottleChordUp(throttleDownBaseKey)

    sentW := false
    sentS := false
    wDown := false
    sDown := false
    wsLastTick := 0
    chordHoldCount := 0

    if (injectedCtrlAlt) {
        if (!GetKeyState("Alt", "P"))
            SendEvent "{Alt up}"
        if (!GetKeyState("Ctrl", "P"))
            SendEvent "{Ctrl up}"
        injectedCtrlAlt := false
    }

    SetTimer(wsTick, 0)
}

; -------------------------
; Ctrl+Alt+Numpad speed helpers (SAFE: always releases)
; -------------------------
sendCtrlAltNumpad(numKeyName, holdMs := 35) {
    try {
        SendEvent "{Ctrl down}"
        Sleep 5
        SendEvent "{Alt down}"
        Sleep 5

        SendEvent "{" numKeyName " down}"
        Sleep holdMs
        SendEvent "{" numKeyName " up}"
    } finally {
        Sleep 5
        SendEvent "{Alt up}"
        Sleep 5
        SendEvent "{Ctrl up}"
    }
}

sendSetSpeedPercent(pct) {
    if (pct <= 0)
        sendCtrlAltNumpad("Numpad1", 40)
    else if (pct = 25)
        sendCtrlAltNumpad("Numpad2", 40)
    else if (pct = 50)
        sendCtrlAltNumpad("Numpad3", 40)
    else if (pct = 75)
        sendCtrlAltNumpad("Numpad4", 40)
    else
        sendCtrlAltNumpad("Numpad5", 40)
}

; -------------------------
; Queue
; -------------------------
enqueueAction(type, value) {
    global actionQueue, queueRunning
    actionQueue.Push(Map("type", type, "value", value))
    if (!queueRunning) {
        queueRunning := true
        SetTimer(processQueue, -1)
    }
}

processQueue() {
    try {
        global actionQueue, queueRunning, throttlePct

        if (actionQueue.Length = 0) {
            queueRunning := false
            return
        }

        action := actionQueue.RemoveAt(1)

        if (action["type"] = "setPct") {
            throttlePct := action["value"]
            if (throttlePct < 0)
                throttlePct := 0
            if (throttlePct > 100)
                throttlePct := 100
            sendSetSpeedPercent(throttlePct)
        }

        SetTimer(processQueue, -1)
    } catch {
        ; On error, stop the queue to avoid repeated failures killing the script.
        queueRunning := false
    }
}

; -------------------------
; Continuous throttle tracking (estimated)
; -------------------------
ensureWsTimer() {
    global wDown, sDown, tickMs, wsLastTick
    if (!wDown && !sDown)
        return
    if (wsLastTick = 0) {
        wsLastTick := A_TickCount
        SetTimer(wsTick, tickMs)
    }
}

wsTick() {
    try {
        global wDown, sDown, wsLastTick, throttlePct, timeToFullMs

        now := A_TickCount
        dt := now - wsLastTick
        wsLastTick := now

        rate := 100.0 / timeToFullMs

        if (wDown && !sDown)
            throttlePct += rate * dt
        else if (sDown && !wDown)
            throttlePct -= rate * dt

        if (throttlePct < 0)
            throttlePct := 0
        if (throttlePct > 100)
            throttlePct := 100
    } catch {
        ; If wsTick fails, reset tracking so we don't crash.
        wsLastTick := 0
    }
}

handleWDown() {
    global wDown, sentW, throttleUpBaseKey
    if (!wDown) {
        wDown := true
        ensureWsTimer()
    }
    if (!sentW) {
        sendThrottleChordDown(throttleUpBaseKey)
        sentW := true
    }
}

handleWUp() {
    global wDown, sentW, throttleUpBaseKey
    wDown := false
    if (sentW) {
        sendThrottleChordUp(throttleUpBaseKey)
        sentW := false
    }
}

handleSDown() {
    global sDown, sentS, throttleDownBaseKey
    if (!sDown) {
        sDown := true
        ensureWsTimer()
    }
    if (!sentS) {
        sendThrottleChordDown(throttleDownBaseKey)
        sentS := true
    }
}

handleSUp() {
    global sDown, sentS, throttleDownBaseKey
    sDown := false
    if (sentS) {
        sendThrottleChordUp(throttleDownBaseKey)
        sentS := false
    }
}

; -------------------------
; 25% snap logic
; -------------------------
snapDownToBoundary() {
    global throttlePct
    pct := throttlePct
    eps := 0.0001

    remainder := pct - Floor(pct / 25.0) * 25.0

    if (remainder < eps || (25.0 - remainder) < eps)
        target := pct - 25.0
    else
        target := Floor(pct / 25.0) * 25.0

    if (target < 0)
        target := 0

    enqueueAction("setPct", target)
}

snapUpToBoundary() {
    global throttlePct
    pct := throttlePct
    eps := 0.0001

    remainder := pct - Floor(pct / 25.0) * 25.0

    if (remainder < eps || (25.0 - remainder) < eps)
        target := pct + 25.0
    else
        target := Ceil(pct / 25.0) * 25.0

    if (target > 100)
        target := 100

    enqueueAction("setPct", target)
}

; -------------------------
; Step buttons
; -------------------------
stepPressStart(keyName) {
    global stepKeyDown, stepKeyLongFired, longPressMs, stepTimerKey
    if (stepKeyDown.Has(keyName) && stepKeyDown[keyName])
        return
    stepKeyDown[keyName] := true
    stepKeyLongFired[keyName] := false
    stepTimerKey := keyName
    SetTimer(stepLongPressTimer, -longPressMs)
}

stepPressEnd(keyName) {
    global stepKeyDown, stepKeyLongFired
    if (!stepKeyDown.Has(keyName) || !stepKeyDown[keyName])
        return
    stepKeyDown[keyName] := false

    if (!stepKeyLongFired[keyName]) {
        if (keyName = "PgUp")
            snapUpToBoundary()
        else
            snapDownToBoundary()
    }
}

stepLongPressTimer() {
    try {
        global stepTimerKey, stepKeyDown, stepKeyLongFired
        keyName := stepTimerKey
        if (!stepKeyDown.Has(keyName) || !stepKeyDown[keyName])
            return
        stepKeyLongFired[keyName] := true

        if (keyName = "PgUp")
            enqueueAction("setPct", 100)
        else
            enqueueAction("setPct", 0)
    } catch {
    }
}

; -------------------------
; Status.json polling (GuiFocus + OnFoot)
; -------------------------
SetTimer(PollStatus, 25)

PollStatus() {
    try {
        if (!ensureEliteActiveContext())
            return

        global statusJsonPath, lastGuiFocus, lastOnFoot, statusReadOk

        try {
            txt := FileRead(statusJsonPath, "UTF-8")
        } catch {
            statusReadOk := false
            return
        }

        gf := -1
        of := lastOnFoot

        if (RegExMatch(txt, '"GuiFocus"\s*:\s*(\d+)', &mGf))
            gf := Integer(mGf[1])

        if (RegExMatch(txt, '"OnFoot"\s*:\s*(true|false)', &mOf))
            of := (mOf[1] = "true")

        if (gf >= 0) {
            lastGuiFocus := gf
            lastOnFoot := of
            statusReadOk := true
        } else {
            statusReadOk := false
        }

        if (statusReadOk && ((lastGuiFocus > 0) || (gateOnFoot && lastOnFoot))) {
            hardReleaseWS()
            joyPrev.Clear()
        }
    } catch {
        ; Swallow unexpected errors to avoid killing the script.
    }
}

; -------------------------
; Pre-emptive GUI assume cooldown (triggered by X/modifier joy buttons)
; -------------------------
SetTimer(PollGuiAssume, 10)

PollGuiAssume() {
    try {
        if (!ensureEliteActiveContext())
            return

        global guiAssumeJoyButtons, guiAssumeMs, guiAssumeUntilTick

        for btn in guiAssumeJoyButtons {
            if (GetKeyState(btn, "P")) {
                guiAssumeUntilTick := A_TickCount + guiAssumeMs
                hardReleaseWS()
                joyPrev.Clear()
                return
            }
        }
    } catch {
    }
}

; -------------------------
; Poll joystick: 1Joy5/1Joy6 -> Ctrl+Alt+S/W
; -------------------------
SetTimer(PollJoyDrive, pollMs)

PollJoyDrive() {
    try {
        if (!ensureEliteActiveContext())
            return

        global driveUpButton, driveDownButton, joyPrev
        global guiDriveUpSent, guiDriveDownSent

        upNow := GetKeyState(driveUpButton, "P")
        dnNow := GetKeyState(driveDownButton, "P")
        upPrev := joyPrev.Has(driveUpButton) ? joyPrev[driveUpButton] : false
        dnPrev := joyPrev.Has(driveDownButton) ? joyPrev[driveDownButton] : false

        if (!translationAllowed()) {
            global sentW, sentS, wDown, sDown, chordHoldCount
            if (sentW || sentS || wDown || sDown || chordHoldCount > 0) {
                hardReleaseWS()
            }

            ; In Elite GUI screens, repurpose throttle inputs to panel-tab keys.
            if (isEliteGuiFocusActive()) {
                if (upNow && !upPrev)
                    sendGuiDriveDown("up")

                if (dnNow && !dnPrev)
                    sendGuiDriveDown("down")

                joyPrev[driveUpButton] := upNow
                joyPrev[driveDownButton] := dnNow
                return
            }

            ; Not in GUI mode: ensure fallback GUI keys are released.
            if (!upNow || !upPrev)
                sendGuiDriveUp("up")
            if (!dnNow || !dnPrev)
                sendGuiDriveUp("down")

            joyPrev.Clear()
            return
        }

        if (upNow && !upPrev)
            handleWDown()
        else if (!upNow && upPrev)
            handleWUp()

        if (dnNow && !dnPrev)
            handleSDown()
        else if (!dnNow && dnPrev)
            handleSUp()

        joyPrev[driveUpButton] := upNow
        joyPrev[driveDownButton] := dnNow
    } catch {
    }
}

; -------------------------
; Indicators (XLT + emitted chord)
; -------------------------
SetTimer(UpdateIndicators, 200)

UpdateIndicators() {
    try {
        if (!ensureEliteActiveContext())
            return

        global showXltIndicator, xltTipId, xltTipX, xltTipY
        global showEmitIndicator, emitTipId, emitTipX, emitTipY
        global sentW, sentS, statusReadOk, lastGuiFocus, lastOnFoot

        if (showXltIndicator) {
            txt := translationAllowed() ? "XLT: ON" : "XLT: OFF"
            if (statusReadOk)
                txt .= "  GF=" lastGuiFocus "  OnFoot=" (lastOnFoot ? "1" : "0")
            else
                txt .= "  Status=?"
            ToolTip(txt, xltTipX, xltTipY, xltTipId)
        } else {
            ToolTip(,,, xltTipId)
        }

        if (showEmitIndicator) {
            if (sentW)
                ToolTip("CA+W ▶", emitTipX, emitTipY, emitTipId)
            else if (sentS)
                ToolTip("CA+S ◀", emitTipX, emitTipY, emitTipId)
            else
                ToolTip(,,, emitTipId)
        } else {
            ToolTip(,,, emitTipId)
        }
    } catch {
    }
}

; -------------------------
; Controls
; -------------------------
#SuspendExempt
F12::Suspend
#SuspendExempt False

; -------------------------
; Elite-active hotkeys
; -------------------------
#HotIf isEliteActive()

$^v::elitePasteOnce()

; Step keys only when translation is allowed
#HotIf translationAllowed()

$*PgUp::    stepPressStart("PgUp")
$*PgUp up:: stepPressEnd("PgUp")

$*PgDn::    stepPressStart("PgDn")
$*PgDn up:: stepPressEnd("PgDn")

#HotIf
