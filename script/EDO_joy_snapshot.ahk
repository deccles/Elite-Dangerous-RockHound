#Requires AutoHotkey v2.0

class JoySnapshot {
    __New(connected, pov := -1, buttons := 0) {
        this.connected := connected
        this.pov := pov
        this.buttons := buttons
    }

    ButtonDown(number) {
        if (!this.connected || number < 1 || number > 32)
            return false
        return (this.buttons & (1 << (number - 1))) != 0
    }
}

; Reads a complete joystick button/POV snapshot through winmm. Unlike AHK's
; 1Joy GetKeyState path, joyGetPosEx returns an error when the controller is
; unplugged or re-enumerating instead of dereferencing invalid device state.
ReadJoySnapshot(joyId := 0, queryFn := 0) {
    static JOYINFOEX_SIZE := 52
    static JOY_RETURNPOV := 0x40
    static JOY_RETURNBUTTONS := 0x80

    info := Buffer(JOYINFOEX_SIZE, 0)
    NumPut("UInt", JOYINFOEX_SIZE, info, 0)
    NumPut("UInt", JOY_RETURNPOV | JOY_RETURNBUTTONS, info, 4)

    result := IsObject(queryFn)
        ? queryFn.Call(joyId, info)
        : DllCall("winmm\joyGetPosEx", "UInt", joyId, "Ptr", info.Ptr, "UInt")

    if (result != 0)
        return JoySnapshot(false)

    return JoySnapshot(true, NumGet(info, 40, "UInt"), NumGet(info, 32, "UInt"))
}
