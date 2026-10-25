package io.github.ksshen0000.vda5050.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.ksshen0000.vda5050.v2.ActionScope;
import io.github.ksshen0000.vda5050.v2.ActionTypes;
import io.github.ksshen0000.vda5050.v2.AgvClass;
import io.github.ksshen0000.vda5050.v2.AgvKinematic;
import io.github.ksshen0000.vda5050.v2.BlockingType;
import io.github.ksshen0000.vda5050.v2.LocalizationType;
import io.github.ksshen0000.vda5050.v2.NavigationType;
import io.github.ksshen0000.vda5050.v2.Support;
import io.github.ksshen0000.vda5050.v2.factsheet.AgvActionSpec;
import io.github.ksshen0000.vda5050.v2.factsheet.Factsheet;
import io.github.ksshen0000.vda5050.v2.factsheet.OptionalParameter;
import io.github.ksshen0000.vda5050.v2.factsheet.PhysicalParameters;
import io.github.ksshen0000.vda5050.v2.factsheet.ProtocolFeatures;
import io.github.ksshen0000.vda5050.v2.factsheet.ProtocolLimits;
import io.github.ksshen0000.vda5050.v2.factsheet.Timing;
import io.github.ksshen0000.vda5050.v2.factsheet.TypeSpecification;

/** Builds the factsheet a simulated vehicle reports, derived from its profile. */
public final class Factsheets {

    private Factsheets() {
    }

    public static Factsheet of(VehicleProfile profile, double stateIntervalSeconds, double visualizationIntervalSeconds) {
        List<AgvActionSpec> actions = new ArrayList<>();
        for (String type : List.of(ActionTypes.PICK, ActionTypes.DROP, ActionTypes.FINE_POSITIONING,
                ActionTypes.DETECT_OBJECT)) {
            actions.add(spec(type, ActionScope.NODE));
        }
        actions.add(spec(ActionTypes.START_CHARGING, ActionScope.NODE, ActionScope.INSTANT));
        actions.add(spec(ActionTypes.STOP_CHARGING, ActionScope.NODE, ActionScope.INSTANT));
        for (String type : List.of(ActionTypes.START_PAUSE, ActionTypes.STOP_PAUSE, ActionTypes.CANCEL_ORDER,
                ActionTypes.INIT_POSITION, ActionTypes.STATE_REQUEST, ActionTypes.FACTSHEET_REQUEST)) {
            actions.add(spec(type, ActionScope.INSTANT));
        }
        profile.customActionTypes().stream().sorted().forEach(t -> actions.add(spec(t, ActionScope.NODE, ActionScope.EDGE)));

        return new Factsheet(0, null, null, null, null,
                new TypeSpecification("TESTKIT-SIM", "simulated line-guided vehicle", AgvKinematic.DIFF,
                        AgvClass.CARRIER, 1000, List.of(LocalizationType.NATURAL),
                        List.of(NavigationType.VIRTUAL_LINE_GUIDED)),
                new PhysicalParameters(0, profile.maxSpeed(), profile.acceleration(), profile.deceleration(), null,
                        0.3, 0.8, 1.2),
                new ProtocolLimits(Map.of(), Map.of(),
                        new Timing(0.05, 0.05, stateIntervalSeconds, visualizationIntervalSeconds)),
                new ProtocolFeatures(
                        List.of(new OptionalParameter("order.edges.trajectory", Support.SUPPORTED, null)),
                        actions),
                Map.of(), Map.of(), null);
    }

    private static AgvActionSpec spec(String type, ActionScope... scopes) {
        return new AgvActionSpec(type, null, List.of(scopes), null, null,
                List.of(BlockingType.NONE, BlockingType.SOFT, BlockingType.HARD));
    }
}
