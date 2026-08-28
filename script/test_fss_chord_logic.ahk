#Requires AutoHotkey v2.0
#Include *i EDO_fss_chord_logic.ahk

Fail(message) {
    FileAppend("FAIL: " message "`n", "*")
    ExitApp(1)
}

AssertEqual(expected, actual, label) {
    if (expected != actual)
        Fail(label ": expected [" expected "], got [" actual "]")
}

if (!IsSet(FssChordLogic))
    Fail("FssChordLogic is not implemented")

yOnly := FssChordLogic()
AssertEqual("", yOnly.Handle("Y_DOWN"), "Y down waits for chord")
AssertEqual("FSD", yOnly.Handle("Y_UP"), "Y alone engages FSD")

profileSwitch := FssChordLogic()
AssertEqual("", profileSwitch.Handle("Y_DOWN"), "Profile switch Y down waits")
AssertEqual("", profileSwitch.Handle("PROFILE_SWITCH"), "Select plus Y consumes Y")
AssertEqual("", profileSwitch.Handle("Y_UP"), "Profile switch does not engage FSD")

rbOnly := FssChordLogic()
AssertEqual("THROTTLE_UP_DOWN", rbOnly.Handle("RB_DOWN"), "RB alone starts throttle up")
AssertEqual("THROTTLE_UP_UP", rbOnly.Handle("RB_UP"), "RB alone stops throttle up")

fssChord := FssChordLogic()
AssertEqual("", fssChord.Handle("Y_DOWN"), "Chord Y down waits")
AssertEqual("FSS", fssChord.Handle("RB_DOWN"), "Y plus RB enters FSS")
AssertEqual("", fssChord.Handle("RB_UP"), "Chord RB release is suppressed")
AssertEqual("", fssChord.Handle("Y_UP"), "Chord Y release does not engage FSD")

dpadOnly := FssChordLogic()
AssertEqual("PIP_SYSTEMS", dpadOnly.Handle("DPAD_LEFT"), "D-pad left alone increases systems power")
AssertEqual("PIP_WEAPONS", dpadOnly.Handle("DPAD_RIGHT"), "D-pad right alone increases weapons power")
AssertEqual("PIP_ENGINES", dpadOnly.Handle("DPAD_UP"), "D-pad up alone increases engines power")
AssertEqual("RESET_PIPS", dpadOnly.Handle("DPAD_DOWN"), "D-pad down alone balances power distribution")

galaxyChord := FssChordLogic()
AssertEqual("", galaxyChord.Handle("Y_DOWN"), "Galaxy chord Y down waits")
AssertEqual("GALAXY_MAP", galaxyChord.Handle("DPAD_LEFT"), "Y plus D-pad left opens galaxy map")
AssertEqual("", galaxyChord.Handle("Y_UP"), "Galaxy chord suppresses FSD")

systemChord := FssChordLogic()
AssertEqual("", systemChord.Handle("Y_DOWN"), "System chord Y down waits")
AssertEqual("SYSTEM_MAP", systemChord.Handle("DPAD_RIGHT"), "Y plus D-pad right opens system map")
AssertEqual("", systemChord.Handle("Y_UP"), "System chord suppresses FSD")

cockpitModeChord := FssChordLogic()
AssertEqual("", cockpitModeChord.Handle("Y_DOWN"), "Cockpit-mode chord Y down waits")
AssertEqual("COCKPIT_MODE", cockpitModeChord.Handle("DPAD_UP"), "Y plus D-pad up switches cockpit mode")
AssertEqual("", cockpitModeChord.Handle("Y_UP"), "Cockpit-mode chord suppresses FSD")

routeChord := FssChordLogic()
AssertEqual("", routeChord.Handle("Y_DOWN"), "Route chord Y down waits")
AssertEqual("TARGET_ROUTE", routeChord.Handle("DPAD_DOWN"), "Y plus D-pad down targets the next route system")
AssertEqual("", routeChord.Handle("Y_UP"), "Route chord suppresses FSD")

FileAppend("PASS`n", "*")
ExitApp(0)
