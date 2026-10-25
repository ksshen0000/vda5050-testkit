package io.github.ksshen0000.vda5050.client;

import io.github.ksshen0000.vda5050.v2.Connection;
import io.github.ksshen0000.vda5050.v2.State;
import io.github.ksshen0000.vda5050.v2.Visualization;
import io.github.ksshen0000.vda5050.v2.factsheet.Factsheet;

/** Callbacks of {@link MasterClient}; default methods ignore the message. */
public interface MasterListener {

    default void onState(VehicleId vehicle, State state) {
    }

    default void onConnection(VehicleId vehicle, Connection connection) {
    }

    default void onVisualization(VehicleId vehicle, Visualization visualization) {
    }

    default void onFactsheet(VehicleId vehicle, Factsheet factsheet) {
    }
}
