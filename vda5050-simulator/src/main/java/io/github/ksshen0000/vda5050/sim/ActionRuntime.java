package io.github.ksshen0000.vda5050.sim;

import io.github.ksshen0000.vda5050.v2.Action;
import io.github.ksshen0000.vda5050.v2.ActionState;
import io.github.ksshen0000.vda5050.v2.ActionStatus;
import io.github.ksshen0000.vda5050.v2.BlockingType;

/** An action known to the vehicle and its progress. */
final class ActionRuntime {

    enum Scope { NODE, EDGE, INSTANT }

    final Action action;
    final Scope scope;
    /** Sequence id of the node or edge the action belongs to; -1 for instant actions. */
    final long ownerSequenceId;
    ActionStatus status = ActionStatus.WAITING;
    double remainingSeconds;
    String resultDescription;

    ActionRuntime(Action action, Scope scope, long ownerSequenceId) {
        this.action = action;
        this.scope = scope;
        this.ownerSequenceId = ownerSequenceId;
    }

    String type() {
        return action.actionType();
    }

    BlockingType blocking() {
        return action.blockingType() == null ? BlockingType.HARD : action.blockingType();
    }

    boolean blocksDriving() {
        return blocking() != BlockingType.NONE;
    }

    boolean isActive() {
        return status == ActionStatus.INITIALIZING || status == ActionStatus.RUNNING;
    }

    void finish(String result) {
        status = ActionStatus.FINISHED;
        resultDescription = result;
    }

    void fail(String result) {
        status = ActionStatus.FAILED;
        resultDescription = result;
    }

    ActionState toState() {
        return new ActionState(action.actionId(), action.actionType(), action.actionDescription(), status,
                resultDescription);
    }
}
