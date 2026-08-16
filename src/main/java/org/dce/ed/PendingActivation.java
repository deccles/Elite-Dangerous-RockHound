package org.dce.ed;

import java.util.Objects;

final class PendingActivation {

    private Runnable activationAction;
    private boolean pending;

    void request() {
        Runnable action;
        synchronized (this) {
            action = activationAction;
            if (action == null) {
                pending = true;
                return;
            }
        }
        action.run();
    }

    void register(Runnable action) {
        boolean activateNow;
        synchronized (this) {
            activationAction = Objects.requireNonNull(action);
            activateNow = pending;
            pending = false;
        }
        if (activateNow) {
            action.run();
        }
    }
}
