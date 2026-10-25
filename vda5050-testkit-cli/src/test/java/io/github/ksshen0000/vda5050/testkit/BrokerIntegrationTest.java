package io.github.ksshen0000.vda5050.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.github.ksshen0000.vda5050.client.AgvClient;
import io.github.ksshen0000.vda5050.conformance.CheckOptions;
import io.github.ksshen0000.vda5050.conformance.ConformanceChecker;
import io.github.ksshen0000.vda5050.conformance.Report;
import io.github.ksshen0000.vda5050.conformance.ReportFormat;
import io.github.ksshen0000.vda5050.conformance.RuleResult;
import io.github.ksshen0000.vda5050.conformance.TraceEntry;
import io.github.ksshen0000.vda5050.sim.Pose;
import io.github.ksshen0000.vda5050.sim.SimulatedAgv;
import io.github.ksshen0000.vda5050.sim.SimulationEngine;
import io.github.ksshen0000.vda5050.sim.SiteMap;
import io.github.ksshen0000.vda5050.sim.VehicleProfile;
import io.github.ksshen0000.vda5050.sim.VirtualVehicle;
import io.github.ksshen0000.vda5050.transport.hivemq.HiveMqTransport;

/** Records simulated vehicles on a real Mosquitto broker over MQTT 5, so the retain flag is visible too. */
@Testcontainers(disabledWithoutDocker = true)
class BrokerIntegrationTest {

    @Container
    static final GenericContainer<?> MOSQUITTO = new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2"))
            .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
            .withExposedPorts(1883);

    @TempDir
    Path dir;

    private BrokerOptions broker() {
        BrokerOptions options = new BrokerOptions();
        options.broker = "tcp://" + MOSQUITTO.getHost() + ":" + MOSQUITTO.getMappedPort(1883);
        options.interfaceName = "uagv";
        return options;
    }

    @Test
    void liveRecordingOfSimulatedVehiclesPasses() throws Exception {
        BrokerOptions broker = broker();
        List<TraceEntry> trace = new CopyOnWriteArrayList<>();
        SimulationEngine engine = new SimulationEngine(50, 4.0);
        try (Recorder recorder = new Recorder(broker, false, Clock.systemUTC())) {
            recorder.start("uagv/#", trace::add);
            for (String serial : List.of("IT-1", "IT-2")) {
                VirtualVehicle vehicle = new VirtualVehicle(serial, VehicleProfile.defaults(), SiteMap.empty("SIM"),
                        new Pose(0, 0, 0), "HOME");
                SimulatedAgv agv = new SimulatedAgv(vehicle, new AgvClient(new HiveMqTransport(broker.host(), broker.port(),
                        "sim-" + serial), "uagv", "TESTKIT-SIM", serial, Clock.systemUTC()), 2, 1);
                agv.connect();
                engine.add(agv);
            }
            engine.start();
            Thread.sleep(Duration.ofSeconds(3));
            engine.close(); // announces OFFLINE
            Thread.sleep(Duration.ofMillis(500));
        }

        Report report = ConformanceChecker.check(trace, CheckOptions.defaults());
        System.out.println(ReportFormat.text(report));
        assertThat(report.findings()).isEmpty();
        // MQTT 5 "retain as published" makes the retain flag of live messages visible
        assertThat(report.results()).filteredOn(r -> r.id().equals("RETAIN"))
                .singleElement().extracting(RuleResult::verdict).isNotEqualTo(RuleResult.Verdict.NOT_APPLICABLE);
        assertThat(report.vehicles()).containsExactly("TESTKIT-SIM/IT-1", "TESTKIT-SIM/IT-2");
    }

    @Test
    void recordCommandWritesTraceAndReport() throws Exception {
        BrokerOptions broker = broker();
        SimulationEngine engine = new SimulationEngine(50, 1.0);
        VirtualVehicle vehicle = new VirtualVehicle("IT-3", VehicleProfile.defaults(), SiteMap.empty("SIM"), new Pose(0, 0, 0), "");
        SimulatedAgv agv = new SimulatedAgv(vehicle, new AgvClient(new HiveMqTransport(broker.host(), broker.port(), "sim-it3"),
                "uagv", "TESTKIT-SIM", "IT-3", Clock.systemUTC()), 1, 1);
        agv.connect();
        engine.add(agv);
        engine.start();
        try {
            Path trace = dir.resolve("live.jsonl");
            int code = Main.commandLine().execute("record", "-b", broker.broker, "-d", "3", "-o", trace.toString(), "--check");
            assertThat(code).isZero();
            assertThat(Files.readAllLines(trace)).isNotEmpty()
                    .anySatisfy(l -> assertThat(l).contains("\"retained\":true").contains("/connection"));
        } finally {
            engine.close();
        }
    }
}
