package io.github.ksshen0000.vda5050.v2.factsheet;

import java.util.Map;

/**
 * @param maxStringLens string length limits, e.g. {@code idLen}
 * @param maxArrayLens  array length limits keyed as in the spec, e.g. {@code "order.nodes"}
 */
public record ProtocolLimits(Map<String, Object> maxStringLens, Map<String, Integer> maxArrayLens, Timing timing) {
}
