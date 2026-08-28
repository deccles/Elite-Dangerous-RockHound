#Requires AutoHotkey v2.0

class FssChordLogic {
    yHeld := false
    rbHeld := false
    chordConsumed := false

    Handle(eventName) {
        switch eventName {
            case "Y_DOWN":
                this.yHeld := true
                return ""
            case "Y_UP":
                this.yHeld := false
                if (this.chordConsumed) {
                    if (!this.rbHeld)
                        this.chordConsumed := false
                    return ""
                }
                return "FSD"
            case "PROFILE_SWITCH":
                if (this.yHeld)
                    this.chordConsumed := true
                return ""
            case "RB_DOWN":
                this.rbHeld := true
                if (this.yHeld) {
                    this.chordConsumed := true
                    return "FSS"
                }
                return "THROTTLE_UP_DOWN"
            case "RB_UP":
                this.rbHeld := false
                if (this.chordConsumed) {
                    if (!this.yHeld)
                        this.chordConsumed := false
                    return ""
                }
                return "THROTTLE_UP_UP"
            case "DPAD_LEFT":
                if (this.yHeld) {
                    this.chordConsumed := true
                    return "GALAXY_MAP"
                }
                return "PIP_SYSTEMS"
            case "DPAD_RIGHT":
                if (this.yHeld) {
                    this.chordConsumed := true
                    return "SYSTEM_MAP"
                }
                return "PIP_WEAPONS"
            case "DPAD_UP":
                if (this.yHeld) {
                    this.chordConsumed := true
                    return "COCKPIT_MODE"
                }
                return "PIP_ENGINES"
            case "DPAD_DOWN":
                if (this.yHeld) {
                    this.chordConsumed := true
                    return "TARGET_ROUTE"
                }
                return "RESET_PIPS"
        }
        return ""
    }

    Reset() {
        this.yHeld := false
        this.rbHeld := false
        this.chordConsumed := false
    }
}
