package io.github.ksshen0000.vda5050.v2;

import java.util.List;
import java.util.Optional;

public record Action(
        String actionType,
        String actionId,
        String actionDescription,
        BlockingType blockingType,
        List<ActionParameter> actionParameters) {

    public Action {
        actionParameters = actionParameters == null ? null : List.copyOf(actionParameters);
    }

    public static Action of(String actionType, String actionId, BlockingType blockingType, ActionParameter... parameters) {
        return new Action(actionType, actionId, null, blockingType, parameters.length == 0 ? null : List.of(parameters));
    }

    public Optional<Object> parameter(String key) {
        if (actionParameters == null) {
            return Optional.empty();
        }
        return actionParameters.stream().filter(p -> key.equals(p.key())).map(ActionParameter::value).findFirst();
    }
}
