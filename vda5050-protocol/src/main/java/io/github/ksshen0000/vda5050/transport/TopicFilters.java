package io.github.ksshen0000.vda5050.transport;

/** MQTT topic filter matching with the {@code +} and {@code #} wildcards. */
public final class TopicFilters {

    private TopicFilters() {
    }

    public static boolean matches(String filter, String topic) {
        String[] f = filter.split("/", -1);
        String[] t = topic.split("/", -1);
        for (int i = 0; i < f.length; i++) {
            if (f[i].equals("#")) {
                return true;
            }
            if (i >= t.length) {
                return false;
            }
            if (!f[i].equals("+") && !f[i].equals(t[i])) {
                return false;
            }
        }
        return f.length == t.length;
    }
}
