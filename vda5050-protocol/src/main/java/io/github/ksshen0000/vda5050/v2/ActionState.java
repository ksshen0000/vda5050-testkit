package io.github.ksshen0000.vda5050.v2;

public record ActionState(String actionId, String actionType, String actionDescription, ActionStatus actionStatus,
        String resultDescription) {

    public ActionState withStatus(ActionStatus status) {
        return new ActionState(actionId, actionType, actionDescription, status, resultDescription);
    }
}
