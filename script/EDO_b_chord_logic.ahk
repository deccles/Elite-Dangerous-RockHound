#Requires AutoHotkey v2.0

class BChordLogic {
    bHeld := false
    chordConsumed := false

    Handle(eventName) {
        switch eventName {
            case "B_DOWN":
                this.bHeld := true
                this.chordConsumed := false
            case "B_UP":
                if (!this.bHeld)
                    return ""
                this.bHeld := false
                if (this.chordConsumed) {
                    this.chordConsumed := false
                    return ""
                }
                return "BOOST"
            case "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "LB_DOWN", "RB_DOWN":
                if (this.bHeld) {
                    this.chordConsumed := true
                    return "B_CHORD"
                }
        }
        return ""
    }

    Reset() {
        this.bHeld := false
        this.chordConsumed := false
    }
}
