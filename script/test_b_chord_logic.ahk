#Requires AutoHotkey v2.0
#Include *i EDO_b_chord_logic.ahk

Fail(message) {
    FileAppend("FAIL: " message "`n", "*")
    ExitApp(1)
}

AssertEqual(expected, actual, label) {
    if (expected != actual)
        Fail(label ": expected [" expected "], got [" actual "]")
}

if (!IsSet(BChordLogic))
    Fail("BChordLogic is not implemented")

bOnly := BChordLogic()
AssertEqual("", bOnly.Handle("B_DOWN"), "B down waits for a possible chord")
AssertEqual("BOOST", bOnly.Handle("B_UP"), "B alone boosts on release")

for chordEvent in ["DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "LB_DOWN", "RB_DOWN"] {
    chord := BChordLogic()
    AssertEqual("", chord.Handle("B_DOWN"), chordEvent " starts with B held")
    AssertEqual("B_CHORD", chord.Handle(chordEvent), chordEvent " consumes B")
    AssertEqual("", chord.Handle("B_UP"), chordEvent " suppresses boost")
}

notHeld := BChordLogic()
AssertEqual("", notHeld.Handle("DPAD_DOWN"), "A chord input without B does nothing")
AssertEqual("", notHeld.Handle("B_UP"), "A stray B release does not boost")

FileAppend("PASS`n", "*")
ExitApp(0)
