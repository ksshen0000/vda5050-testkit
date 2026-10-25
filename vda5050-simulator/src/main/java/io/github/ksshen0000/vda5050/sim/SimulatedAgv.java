package io.github.ksshen0000.vda5050.sim;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.github.ksshen0000.vda5050.client.AgvClient;
import io.github.ksshen0000.vda5050.v2.InstantActions;
import io.github.ksshen0000.vda5050.v2.Order;
import io.github.ksshen0000.vda5050.v2.factsheet.Factsheet;

/**
 * Connects a {@link VirtualVehicle} to MQTT. Messages arrive on the MQTT client's threads and are
 * queued; the simulation thread applies them at the start of the next step, so the vehicle itself is
 * only ever touched by one thread.
 */
public final class SimulatedAgv {

    private final VirtualVehicle vehicle;
    private final AgvClient client;
    private final Factsheet factsheet;
    private final double stateInterval;
    private final double visualizationInterval;
    private final Queue<Object> inbox = new ConcurrentLinkedQueue<>();
    private double sinceState;
    private double sinceVisualization;
    /** Seconds of simulated network outage left (SIM-09); nothing is sent or received meanwhile. */
    private double offlineFor;

    /**
     * @param stateInterval          seconds between periodic state messages (events are sent at once)
     * @param visualizationInterval  seconds between visualization messages
     */
    public SimulatedAgv(VirtualVehicle vehicle, AgvClient client, double stateInterval, double visualizationInterval) {
        this.vehicle = vehicle;
        this.client = client;
        this.stateInterval = stateInterval;
        this.visualizationInterval = visualizationInterval;
        this.factsheet = Factsheets.of(vehicle.profile(), stateInterval, visualizationInterval);
    }

    public void connect() {
        client.start(inbox::add, inbox::add);
        client.publishFactsheet(factsheet);
    }

    public void step(double dt) {
        if (offlineFor > 0) {
            offlineFor -= dt;
            inbox.clear();
            vehicle.step(dt);
            if (offlineFor <= 0) {
                client.publishState(vehicle.state());
                sinceState = 0;
            }
            return;
        }
        for (Object message; (message = inbox.poll()) != null; ) {
            if (message instanceof Order order) {
                vehicle.receiveOrder(order);
            } else if (message instanceof InstantActions instantActions) {
                vehicle.receiveInstantActions(instantActions);
            }
        }
        vehicle.step(dt);
        sinceState += dt;
        sinceVisualization += dt;
        if (vehicle.takeStateEvent() || sinceState >= stateInterval - 1e-9) {
            client.publishState(vehicle.state());
            sinceState = 0;
        }
        if (sinceVisualization >= visualizationInterval - 1e-9) {
            client.publishVisualization(vehicle.visualization());
            sinceVisualization = 0;
        }
        if (vehicle.takeFactsheetRequest()) {
            client.publishFactsheet(factsheet);
        }
    }

    /**
     * Simulates a network outage: the vehicle keeps doing what it does but neither reports nor hears
     * anything for {@code seconds}, so the platform sees its heartbeat stop.
     */
    public void dropConnection(double seconds) {
        offlineFor = seconds;
    }

    /** Ends a simulated outage early. */
    public void restoreConnection() {
        if (offlineFor > 0) {
            offlineFor = Double.MIN_VALUE;
        }
    }

    public boolean isConnectionDropped() {
        return offlineFor > 0;
    }

    public void disconnect() {
        client.close();
    }

    public VirtualVehicle vehicle() {
        return vehicle;
    }
}
