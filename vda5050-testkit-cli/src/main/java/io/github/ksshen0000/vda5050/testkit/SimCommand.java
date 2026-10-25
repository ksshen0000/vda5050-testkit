package io.github.ksshen0000.vda5050.testkit;

import java.time.Clock;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import io.github.ksshen0000.vda5050.client.AgvClient;
import io.github.ksshen0000.vda5050.sim.Pose;
import io.github.ksshen0000.vda5050.sim.SimulatedAgv;
import io.github.ksshen0000.vda5050.sim.SimulationEngine;
import io.github.ksshen0000.vda5050.sim.VehicleProfile;
import io.github.ksshen0000.vda5050.sim.VirtualVehicle;
import io.github.ksshen0000.vda5050.transport.hivemq.HiveMqTransport;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = "sim", mixinStandardHelpOptions = true,
        description = {"Runs simulated VDA 5050 2.1 vehicles against a broker until Ctrl+C.",
                "They accept orders whose nodes carry nodePosition, execute actions (pick, drop, ...),",
                "and handle instant actions such as startPause, cancelOrder and initPosition.",
                "Vehicle i starts at x=0, y=i*spacing on node <serial>-HOME."})
final class SimCommand implements Callable<Integer> {

    @Mixin
    BrokerOptions broker;

    @Option(names = {"-n", "--vehicles"}, defaultValue = "1", description = "Number of vehicles (default: ${DEFAULT-VALUE}).")
    int vehicles;

    @Option(names = {"-m", "--manufacturer"}, defaultValue = "TESTKIT-SIM", description = "Manufacturer (default: ${DEFAULT-VALUE}).")
    String manufacturer;

    @Option(names = "--serial-prefix", defaultValue = "AGV-", description = "Serial numbers are prefix + 001, 002, ... (default: ${DEFAULT-VALUE}).")
    String serialPrefix;

    @Option(names = "--map", defaultValue = "SIM", description = "mapId the vehicles report (default: ${DEFAULT-VALUE}).")
    String mapId;

    @Option(names = "--spacing", defaultValue = "5", description = "Metres between start positions (default: ${DEFAULT-VALUE}).")
    double spacing;

    @Option(names = "--speed", defaultValue = "1.5", description = "Maximum speed in m/s (default: ${DEFAULT-VALUE}).")
    double speed;

    @Option(names = "--state-interval", defaultValue = "10", description = "Seconds between periodic state messages (default: ${DEFAULT-VALUE}).")
    double stateInterval;

    @Option(names = "--visualization-interval", defaultValue = "1", description = "Seconds between visualization messages (default: ${DEFAULT-VALUE}).")
    double visualizationInterval;

    @Option(names = "--time-scale", defaultValue = "1", description = "Simulation speed; 2 runs twice as fast as real time (default: ${DEFAULT-VALUE}).")
    double timeScale;

    @Override
    public Integer call() throws Exception {
        SimulationEngine engine = new SimulationEngine(50, timeScale);
        VehicleProfile profile = VehicleProfile.defaults().withMaxSpeed(speed);
        for (int i = 1; i <= vehicles; i++) {
            String serial = serialPrefix + "%03d".formatted(i);
            VirtualVehicle vehicle = new VirtualVehicle(serial, profile, io.github.ksshen0000.vda5050.sim.SiteMap.empty(mapId),
                    new Pose(0, (i - 1) * spacing, 0), serial + "-HOME");
            HiveMqTransport transport = new HiveMqTransport(broker.host(), broker.port(), manufacturer + "-" + serial);
            SimulatedAgv agv = new SimulatedAgv(vehicle, new AgvClient(transport, broker.interfaceName, manufacturer,
                    serial, Clock.systemUTC()), stateInterval, visualizationInterval);
            agv.connect();
            engine.add(agv);
            System.err.printf("vehicle %s/%s online at (0, %.1f)%n", manufacturer, serial, (i - 1) * spacing);
        }
        engine.start();
        System.err.printf("simulating %d vehicle(s) on %s:%d, interface %s - Ctrl+C to stop%n", vehicles, broker.host(),
                broker.port(), broker.interfaceName);
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            engine.close(); // vehicles announce OFFLINE and disconnect cleanly
            stopped.countDown();
        }));
        stopped.await();
        return 0;
    }
}
