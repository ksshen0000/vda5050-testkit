package io.github.ksshen0000.vda5050.conformance.rules;

import io.github.ksshen0000.vda5050.conformance.Json;
import io.github.ksshen0000.vda5050.conformance.Message;
import io.github.ksshen0000.vda5050.conformance.Rule;

/** Header {@code manufacturer}, {@code serialNumber} and major {@code version} agree with the topic. */
final class HeaderMatchRule extends Rule {

    @Override
    public String id() {
        return "HEADER-MATCH";
    }

    @Override
    public String title() {
        return "Header agrees with the topic";
    }

    @Override
    public Party party() {
        return Party.PUBLISHER;
    }

    @Override
    public String section() {
        return "2.x: 6.3, 6.4 / 3.0: 4.2, 7.2";
    }

    @Override
    public void onMessage(Message m) {
        if (!m.hasObjectBody() || !m.wellFormedTopic()) {
            return;
        }
        checked();
        String manufacturer = Json.text(m.body(), "manufacturer");
        String serialNumber = Json.text(m.body(), "serialNumber");
        String version = m.protocolVersion();
        if (manufacturer != null && !manufacturer.equals(m.manufacturer())) {
            error(m.vehicle(), "manufacturer", "header manufacturer '%s' differs from topic level '%s'"
                    .formatted(manufacturer, m.manufacturer()), m);
        }
        if (serialNumber != null && !serialNumber.equals(m.serialNumber())) {
            error(m.vehicle(), "serialNumber", "header serialNumber '%s' differs from topic level '%s'"
                    .formatted(serialNumber, m.serialNumber()), m);
        }
        if (version != null && !version.isEmpty()) {
            String major = "v" + version.split("\\.")[0];
            if (!major.equals(m.topicVersion())) {
                error(m.vehicle(), "version", "header version %s does not belong to topic major version %s"
                        .formatted(version, m.topicVersion()), m);
            }
            if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
                error(m.vehicle(), "semver", "header version '%s' is not [Major].[Minor].[Patch]".formatted(version), m);
            }
        }
    }
}
