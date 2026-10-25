package io.github.ksshen0000.vda5050.v2.factsheet;

import java.util.List;

import io.github.ksshen0000.vda5050.v2.ActionScope;
import io.github.ksshen0000.vda5050.v2.BlockingType;

/** An action the vehicle supports, as advertised in the factsheet. */
public record AgvActionSpec(
        String actionType,
        String actionDescription,
        List<ActionScope> actionScopes,
        List<ActionParameterSpec> actionParameters,
        String resultDescription,
        List<BlockingType> blockingTypes) {
}
