package io.github.ksshen0000.vda5050.v2;

import java.time.Instant;
import java.util.List;

/**
 * Vehicle state. {@code nodeStates} / {@code edgeStates} list the parts of the current order still
 * ahead of the vehicle; an empty {@code orderId} means no order has been received yet.
 */
public record State(
        long headerId,
        Instant timestamp,
        String version,
        String manufacturer,
        String serialNumber,
        List<StateMap> maps,
        String orderId,
        long orderUpdateId,
        String zoneSetId,
        String lastNodeId,
        long lastNodeSequenceId,
        boolean driving,
        Boolean paused,
        Boolean newBaseRequest,
        Double distanceSinceLastNode,
        OperatingMode operatingMode,
        List<NodeState> nodeStates,
        List<EdgeState> edgeStates,
        AgvPosition agvPosition,
        Velocity velocity,
        List<Load> loads,
        List<ActionState> actionStates,
        BatteryState batteryState,
        List<AgvError> errors,
        List<Info> information,
        SafetyState safetyState) implements Vda5050Message {

    public State {
        maps = maps == null ? null : List.copyOf(maps);
        nodeStates = nodeStates == null ? List.of() : List.copyOf(nodeStates);
        edgeStates = edgeStates == null ? List.of() : List.copyOf(edgeStates);
        loads = loads == null ? null : List.copyOf(loads);
        actionStates = actionStates == null ? List.of() : List.copyOf(actionStates);
        errors = errors == null ? List.of() : List.copyOf(errors);
        information = information == null ? null : List.copyOf(information);
    }

    /** A state without header; the client stamps it when publishing. */
    public static State of(List<StateMap> maps, String orderId, long orderUpdateId, String lastNodeId,
            long lastNodeSequenceId, boolean driving, Boolean paused, Boolean newBaseRequest,
            Double distanceSinceLastNode, OperatingMode operatingMode, List<NodeState> nodeStates,
            List<EdgeState> edgeStates, AgvPosition agvPosition, Velocity velocity, List<Load> loads,
            List<ActionState> actionStates, BatteryState batteryState, List<AgvError> errors, SafetyState safetyState) {
        return new State(0, null, null, null, null, maps, orderId, orderUpdateId, null, lastNodeId, lastNodeSequenceId,
                driving, paused, newBaseRequest, distanceSinceLastNode, operatingMode, nodeStates, edgeStates,
                agvPosition, velocity, loads, actionStates, batteryState, errors, null, safetyState);
    }

    @Override
    public State withHeader(Header h) {
        return new State(h.headerId(), h.timestamp(), h.version(), h.manufacturer(), h.serialNumber(), maps, orderId,
                orderUpdateId, zoneSetId, lastNodeId, lastNodeSequenceId, driving, paused, newBaseRequest, distanceSinceLastNode, operatingMode, nodeStates, edgeStates, agvPosition, velocity, loads,
                actionStates, batteryState, errors, information, safetyState);
    }
}
