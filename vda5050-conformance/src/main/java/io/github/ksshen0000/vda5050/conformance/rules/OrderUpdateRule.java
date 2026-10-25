package io.github.ksshen0000.vda5050.conformance.rules;

import java.util.Objects;

import io.github.ksshen0000.vda5050.conformance.Message;
import io.github.ksshen0000.vda5050.conformance.Rule;
import io.github.ksshen0000.vda5050.conformance.rules.OrderView.Element;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * An update of an order keeps the {@code orderId}, raises {@code orderUpdateId} and starts with the
 * decision point of the previous message (same {@code nodeId} and {@code sequenceId}, contents
 * unchanged); messages the vehicle rejected do not count as previous. Resending the same {@code orderUpdateId} only makes sense with identical contents, since
 * the vehicle ignores the repeat.
 */
final class OrderUpdateRule extends Rule {

    private static final String[] HEADER = {"headerId", "timestamp", "version", "manufacturer", "serialNumber"};

    private final OrderLedger ledger = new OrderLedger();

    @Override
    public String id() {
        return "ORDER-UPDATE";
    }

    @Override
    public String title() {
        return "Order updates stitch onto the decision point";
    }

    @Override
    public Party party() {
        return Party.FLEET_CONTROL;
    }

    @Override
    public String section() {
        return "2.x: 6.6.2, 6.6.4.3 / 3.0: 6.1.2, 6.1.4.4, 6.1.4.5";
    }

    @Override
    public void onMessage(Message m) {
        ledger.observe(m);
        if (!m.topicName().equals("order") || !m.hasObjectBody()) {
            return;
        }
        OrderView order = OrderView.of(m);
        if (order.orderId() == null || order.orderUpdateId() == null) {
            return;
        }
        // An update continues the latest message of the order the vehicle did not reject
        OrderView previous = ledger.continued(m.vehicle(), order.orderId(), m);
        if (previous == null) {
            return;
        }
        checked();
        String v = m.vehicle();
        long update = order.orderUpdateId();
        long before = previous.orderUpdateId();
        if (update < before) {
            error(v, "lower update id", "%s sent after %s; orderUpdateId shall not decrease, the vehicle rejects it"
                    .formatted(order.label(), previous.label()), previous.message(), m);
            return;
        }
        if (update == before) {
            if (!withoutHeader(m.body()).equals(withoutHeader(previous.message().body()))) {
                error(v, "changed resend", "%s resent with different contents; the vehicle ignores a repeated orderUpdateId"
                        .formatted(order.label()), previous.message(), m);
            }
            return;
        }
        Element decisionPoint = previous.decisionPoint();
        if (decisionPoint == null || order.nodes().isEmpty()) {
            return; // structure problems are reported by ORDER-STRUCTURE
        }
        Element first = order.nodes().getFirst();
        if (!Objects.equals(first.id(), decisionPoint.id()) || !Objects.equals(first.sequenceId(), decisionPoint.sequenceId())) {
            error(v, "stitching", "%s starts at node %s (sequenceId %s) but the decision point of %s is %s (sequenceId %s)"
                    .formatted(order.label(), first.id(), first.sequenceId(), previous.label(), decisionPoint.id(),
                            decisionPoint.sequenceId()), previous.message(), m);
        } else if (!first.json().equals(decisionPoint.json())) {
            warning(v, "stitching node changed", "%s changes the contents of stitching node %s; the vehicle keeps those of the first transmission"
                    .formatted(order.label(), first.id()), previous.message(), m);
        }
    }

    private static JsonNode withoutHeader(JsonNode body) {
        ObjectNode copy = (ObjectNode) body.deepCopy();
        for (String field : HEADER) {
            copy.remove(field);
        }
        return copy;
    }
}
