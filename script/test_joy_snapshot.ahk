#Requires AutoHotkey v2.0
#Include *i EDO_joy_snapshot.ahk

Fail(message) {
    FileAppend("FAIL: " message "`n", "*")
    ExitApp(1)
}

AssertEqual(expected, actual, label) {
    if (expected != actual)
        Fail(label ": expected [" expected "], got [" actual "]")
}

FakeDisconnected(joyId, info) {
    return 167 ; JOYERR_UNPLUGGED
}

FakeConnected(joyId, info) {
    NumPut("UInt", 0x5, info, 32) ; buttons 1 and 3
    NumPut("UInt", 9000, info, 40) ; POV right
    return 0
}

if (!IsSet(ReadJoySnapshot))
    Fail("ReadJoySnapshot is not implemented")

missing := ReadJoySnapshot(0, FakeDisconnected)
AssertEqual(false, missing.connected, "disconnected controller is reported safely")
AssertEqual(-1, missing.pov, "disconnected controller has centered POV")
AssertEqual(false, missing.ButtonDown(1), "disconnected controller has no pressed buttons")

present := ReadJoySnapshot(0, FakeConnected)
AssertEqual(true, present.connected, "connected controller is reported")
AssertEqual(9000, present.pov, "POV value is decoded")
AssertEqual(true, present.ButtonDown(1), "button 1 is decoded")
AssertEqual(false, present.ButtonDown(2), "button 2 is decoded")
AssertEqual(true, present.ButtonDown(3), "button 3 is decoded")

FileAppend("PASS`n", "*")
ExitApp(0)
