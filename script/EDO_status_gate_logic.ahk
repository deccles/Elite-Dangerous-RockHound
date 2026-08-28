#Requires AutoHotkey v2.0

StatusGateAllowsFlightTranslation(statusReadOk, guiFocus, onFoot, docked, gateOnGuiFocus, gateOnFoot) {
    if (gateOnGuiFocus && (!statusReadOk || guiFocus > 0))
        return false
    if (statusReadOk && docked)
        return false
    if (gateOnFoot && statusReadOk && onFoot)
        return false
    return true
}
