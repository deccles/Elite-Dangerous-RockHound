#Requires AutoHotkey v2.0
#Include *i EDO_status_gate_logic.ahk

Fail(message) {
    FileAppend("FAIL: " message "`n", "*")
    ExitApp(1)
}

AssertEqual(expected, actual, label) {
    if (expected != actual)
        Fail(label ": expected [" expected "], got [" actual "]")
}

if (!IsSet(StatusGateAllowsFlightTranslation))
    Fail("StatusGateAllowsFlightTranslation is not implemented")

AssertEqual(true, StatusGateAllowsFlightTranslation(true, 0, false, false, true, true), "Normal cockpit allows translation")
AssertEqual(false, StatusGateAllowsFlightTranslation(true, 0, false, true, true, true), "Docked cockpit blocks translation")
AssertEqual(false, StatusGateAllowsFlightTranslation(true, 1, false, false, true, true), "Focused GUI blocks translation")
AssertEqual(false, StatusGateAllowsFlightTranslation(true, 0, true, false, true, true), "On foot blocks translation")
AssertEqual(false, StatusGateAllowsFlightTranslation(false, 0, false, false, true, true), "Unreadable status blocks translation")

FileAppend("PASS`n", "*")
ExitApp(0)
