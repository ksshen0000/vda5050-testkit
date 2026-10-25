package io.github.ksshen0000.vda5050.conformance.rules;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.ksshen0000.vda5050.conformance.Message;
import io.github.ksshen0000.vda5050.conformance.Rule;
import io.github.ksshen0000.vda5050.conformance.SchemaCatalog;

/** Payload validates against the official schema of the version named in its header. */
final class SchemaRule extends Rule {

    private final SchemaCatalog schemas;
    private final Set<String> unknownVersions = new HashSet<>();

    SchemaRule(SchemaCatalog schemas) {
        this.schemas = schemas;
    }

    @Override
    public String id() {
        return "SCHEMA";
    }

    @Override
    public String title() {
        return "Valid against the official JSON schema";
    }

    @Override
    public Party party() {
        return Party.PUBLISHER;
    }

    @Override
    public String section() {
        return "2.x: 6.5 / 3.0: 4.3";
    }

    @Override
    public void onMessage(Message m) {
        if (!m.hasObjectBody()) {
            return;
        }
        String version = m.protocolVersion();
        String schemaVersion = SchemaCatalog.schemaVersionFor(version);
        if (schemaVersion == null) {
            if (unknownVersions.add(m.vehicle() + "/" + version)) {
                warning(m.vehicle(), "version", "no schema for protocol version %s; supported: %s"
                        .formatted(version == null ? "(missing)" : "'" + version + "'", SchemaCatalog.VERSIONS), m);
            }
            return;
        }
        if (!schemas.has(schemaVersion, m.topicName())) {
            return;
        }
        checked();
        List<String> errors = schemas.validate(schemaVersion, m.topicName(), m.body());
        if (!errors.isEmpty()) {
            String shown = String.join("; ", errors.subList(0, Math.min(errors.size(), 5)));
            String more = errors.size() > 5 ? " (+%d more)".formatted(errors.size() - 5) : "";
            error(m.vehicle(), m.topicName(), "%s does not match the %s schema: %s%s"
                    .formatted(m.topicName(), schemaVersion, shown, more), m);
        }
    }
}
