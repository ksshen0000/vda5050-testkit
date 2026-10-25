package io.github.ksshen0000.vda5050.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class OrderRulesTest {

    @Test
    void sampleOrderIsWellFormed() {
        assertThat(OrderRules.validate(SampleMessages.order())).isEmpty();
    }

    @Test
    void singleNodeOrderIsWellFormed() {
        Order order = order(List.of(node("A", 0, true)), List.of());

        assertThat(OrderRules.validate(order)).isEmpty();
    }

    @Test
    void emptyOrderIsRejected() {
        assertThat(OrderRules.validate(order(List.of(), List.of()))).containsExactly("order has no nodes");
    }

    @Test
    void edgeCountMustMatch() {
        Order order = order(List.of(node("A", 0, true), node("B", 2, true)), List.of());

        assertThat(OrderRules.validate(order)).singleElement().asString().contains("2 nodes but 0 edges");
    }

    @Test
    void sequenceIdsMustAlternateAndIncrease() {
        Order order = order(List.of(node("A", 0, true), node("B", 4, true)), List.of(edge("AB", 1, "A", "B", true)));

        assertThat(OrderRules.validate(order)).singleElement().asString().contains("increase by one");
    }

    @Test
    void firstSequenceIdMustBeEven() {
        Order order = order(List.of(node("A", 1, true), node("B", 3, true)), List.of(edge("AB", 2, "A", "B", true)));

        assertThat(OrderRules.validate(order)).anySatisfy(p -> assertThat(p).contains("must be even"));
    }

    @Test
    void edgeMustConnectItsNeighbours() {
        Order order = order(List.of(node("A", 0, true), node("B", 2, true)), List.of(edge("XB", 1, "X", "B", true)));

        assertThat(OrderRules.validate(order)).singleElement().asString().contains("must connect A to B");
    }

    @Test
    void baseMustEndOnANode() {
        Order order = order(List.of(node("A", 0, true), node("B", 2, false)), List.of(edge("AB", 1, "A", "B", true)));

        assertThat(OrderRules.validate(order)).singleElement().asString().contains("base must end on a node");
    }

    @Test
    void nothingIsReleasedAfterTheHorizonStarts() {
        Order order = order(List.of(node("A", 0, true), node("B", 2, false), node("C", 4, true)),
                List.of(edge("AB", 1, "A", "B", false), edge("BC", 3, "B", "C", true)));

        assertThat(OrderRules.validate(order)).anySatisfy(p -> assertThat(p).contains("after the horizon started"));
    }

    @Test
    void firstNodeMustBeReleased() {
        Order order = order(List.of(node("A", 0, false)), List.of());

        assertThat(OrderRules.validate(order)).containsExactly("first node must be released");
    }

    @Test
    void baseAccessorsReturnReleasedPrefix() {
        Order order = SampleMessages.order();

        assertThat(OrderRules.baseNodes(order)).extracting(Node::nodeId).containsExactly("A", "B");
        assertThat(OrderRules.baseEdges(order)).extracting(Edge::edgeId).containsExactly("AB");
    }

    private static Order order(List<Node> nodes, List<Edge> edges) {
        return new Order(SampleMessages.HEADER, "o", 0, nodes, edges);
    }

    private static Node node(String id, long seq, boolean released) {
        return new Node(id, seq, null, released, NodePosition.of(0, 0, null, "M"), List.of());
    }

    private static Edge edge(String id, long seq, String from, String to, boolean released) {
        return Edge.of(id, seq, released, from, to, null, null, null);
    }
}
