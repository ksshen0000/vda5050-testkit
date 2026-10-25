package io.github.ksshen0000.vda5050.testkit;

import java.net.URI;

import picocli.CommandLine.Option;

/** Where the MQTT broker is and how to log in. */
final class BrokerOptions {

    @Option(names = {"-b", "--broker"}, paramLabel = "URL", defaultValue = "tcp://localhost:1883",
            description = "Broker, e.g. tcp://localhost:1883 or ssl://broker:8883 (default: ${DEFAULT-VALUE}).")
    String broker;

    @Option(names = "--username", description = "MQTT user name.")
    String username;

    @Option(names = "--password", description = "MQTT password; prefer the MQTT_PASSWORD environment variable.",
            defaultValue = "${env:MQTT_PASSWORD}")
    String password;

    @Option(names = {"-i", "--interface"}, paramLabel = "NAME", defaultValue = "uagv",
            description = "interfaceName topic level (default: ${DEFAULT-VALUE}; 3.0 suggests vda5050).")
    String interfaceName;

    String host() {
        return uri().getHost();
    }

    int port() {
        URI uri = uri();
        return uri.getPort() > 0 ? uri.getPort() : tls() ? 8883 : 1883;
    }

    boolean tls() {
        String scheme = uri().getScheme();
        return scheme.equals("ssl") || scheme.equals("mqtts") || scheme.equals("tls");
    }

    private URI uri() {
        String value = broker.contains("://") ? broker : "tcp://" + broker;
        URI uri = URI.create(value);
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("cannot parse broker URL '" + broker + "'");
        }
        return uri;
    }
}
