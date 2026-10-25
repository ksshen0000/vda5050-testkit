package io.github.ksshen0000.vda5050.v2;

/**
 * Action status. The 2.1 text also mentions PAUSED, but the 2.1 JSON schema does not allow it, so it
 * is not offered here.
 */
public enum ActionStatus {
    WAITING,
    INITIALIZING,
    RUNNING,
    FINISHED,
    FAILED;

    public boolean isTerminal() {
        return this == FINISHED || this == FAILED;
    }
}
