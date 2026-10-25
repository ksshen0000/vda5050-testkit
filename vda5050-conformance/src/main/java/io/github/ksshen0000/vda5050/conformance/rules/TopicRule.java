package io.github.ksshen0000.vda5050.conformance.rules;

import java.util.regex.Pattern;

import io.github.ksshen0000.vda5050.conformance.Dialect;
import io.github.ksshen0000.vda5050.conformance.Message;
import io.github.ksshen0000.vda5050.conformance.Rule;

/** Topic levels {@code interfaceName/majorVersion/manufacturer/serialNumber/topic} and their characters. */
final class TopicRule extends Rule {

    private static final Pattern MAJOR_VERSION = Pattern.compile("v[0-9]+");
    private static final Pattern SERIAL_NUMBER = Pattern.compile("[A-Za-z0-9_.:-]+");

    @Override
    public String id() {
        return "TOPIC";
    }

    @Override
    public String title() {
        return "Topic structure";
    }

    @Override
    public Party party() {
        return Party.PUBLISHER;
    }

    @Override
    public String section() {
        return "2.x: 6.3 / 3.0: 4.2";
    }

    @Override
    public void onMessage(Message m) {
        checked();
        String vehicle = m.vehicle();
        if (!m.wellFormedTopic()) {
            error(vehicle, "levels", "topic '%s' does not have the five levels interfaceName/majorVersion/manufacturer/serialNumber/topic"
                    .formatted(m.entry().topic()), m);
            return;
        }
        String[] levels = m.entry().topic().split("/", -1);
        for (String level : levels) {
            if (level.isEmpty() || level.contains("$") || level.contains("+") || level.contains("#")) {
                error(vehicle, "characters", "topic level '%s' is empty or contains $, + or #".formatted(level), m);
                return;
            }
        }
        if (!MAJOR_VERSION.matcher(m.topicVersion()).matches()) {
            error(vehicle, "major", "majorVersion level '%s' is not 'v' followed by the major version".formatted(m.topicVersion()), m);
        }
        Dialect dialect = Dialect.ofTopicVersion(m.topicVersion());
        if (dialect == null) {
            warning(vehicle, "unknown major", "major version %s is not covered by the checks; checked as %s"
                    .formatted(m.topicVersion(), m.dialect().topicVersion()), m);
        }
        if (!m.dialect().topics().contains(m.topicName())) {
            error(vehicle, "topic name", "'%s' is not a %s topic".formatted(m.topicName(), m.dialect().topicVersion()), m);
        }
        if (!SERIAL_NUMBER.matcher(m.serialNumber()).matches()) {
            error(vehicle, "serial", "serial number '%s' contains characters other than A-Z a-z 0-9 _ . : -"
                    .formatted(m.serialNumber()), m);
        }
    }
}
