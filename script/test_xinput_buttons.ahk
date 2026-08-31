#Requires AutoHotkey v2.0
#Include *i EDO_xinput_buttons.ahk

Fail(message) {
    FileAppend("FAIL: " message "`n", "*")
    ExitApp(1)
}

AssertEqual(expected, actual, label) {
    if (expected != actual)
        Fail(label ": expected [" expected "], got [" actual "]")
}

if (!IsSet(ReadXInputButtons) || !IsSet(XINPUT_GAMEPAD_B) || !IsSet(XINPUT_GAMEPAD_DPAD_LEFT))
    Fail("XInput helpers are not implemented")

AssertEqual(false, XInputButtonDown(0, XINPUT_GAMEPAD_B), "no buttons means B is up")
AssertEqual(true, XInputButtonDown(0x2000, XINPUT_GAMEPAD_B), "mask 0x2000 is B down")
AssertEqual(true, XInputButtonDown(0x0004, XINPUT_GAMEPAD_DPAD_LEFT), "mask 0x0004 is D-pad left")
AssertEqual(true, XInputButtonDown(0x0008, XINPUT_GAMEPAD_DPAD_RIGHT), "mask 0x0008 is D-pad right")
AssertEqual(false, XInputButtonDown(0x0004, XINPUT_GAMEPAD_DPAD_RIGHT), "left is not right")

missing := ReadXInputButtons(0, (*) => 0)
AssertEqual(0, missing, "disconnected pad reports no buttons")

held := ReadXInputButtons(0, (*) => 0x2000)
AssertEqual(true, XInputButtonDown(held, XINPUT_GAMEPAD_B), "B down is decoded from wButtons")

FileAppend("PASS`n", "*")
ExitApp(0)
