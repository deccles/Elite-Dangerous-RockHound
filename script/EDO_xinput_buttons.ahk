#Requires AutoHotkey v2.0

; Xbox buttons on the first XInput pad. Flydigi in Xbox mode shows up here, not as 1Joy.
global XINPUT_GAMEPAD_DPAD_UP := 0x0001
global XINPUT_GAMEPAD_DPAD_DOWN := 0x0002
global XINPUT_GAMEPAD_DPAD_LEFT := 0x0004
global XINPUT_GAMEPAD_DPAD_RIGHT := 0x0008
global XINPUT_GAMEPAD_A := 0x1000
global XINPUT_GAMEPAD_B := 0x2000
global XINPUT_GAMEPAD_X := 0x4000

XInputButtonDown(buttons, mask) {
    return (buttons & mask) != 0
}

; Returns the pad's wButtons, or 0 if no XInput device. queryFn is for tests.
ReadXInputButtons(userIndex := 0, queryFn := 0) {
    if (IsObject(queryFn))
        return Integer(queryFn.Call(userIndex))

    proc := XInputGetStateProc()
    if (!proc)
        return 0

    state := Buffer(16, 0)
    result := DllCall(proc, "UInt", userIndex, "Ptr", state.Ptr, "UInt")
    if (result != 0)
        return 0
    return NumGet(state, 4, "UShort")
}

XInputGetStateProc() {
    static proc := -1
    if (proc != -1)
        return proc

    proc := 0
    for dllName in ["xinput1_4.dll", "xinput1_3.dll", "xinput9_1_0.dll"] {
        hmod := DllCall("LoadLibrary", "Str", dllName, "Ptr")
        if (!hmod)
            continue
        found := DllCall("GetProcAddress", "Ptr", hmod, "AStr", "XInputGetState", "Ptr")
        if (found) {
            proc := found
            break
        }
    }
    return proc
}
