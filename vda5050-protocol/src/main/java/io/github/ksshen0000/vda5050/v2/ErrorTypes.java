package io.github.ksshen0000.vda5050.v2;

/** Error types used when a vehicle rejects an order or action (VDA 5050 2.1 section 6.6). */
public final class ErrorTypes {

    /** The order is malformed or does not match the vehicle (e.g. unknown map, invalid sequence). */
    public static final String VALIDATION_ERROR = "validationError";
    /** An order update does not continue the current order correctly. */
    public static final String ORDER_UPDATE_ERROR = "orderUpdateError";
    /** A new order was received while another one is still active. */
    public static final String ORDER_ERROR = "orderError";
    /** The vehicle cannot reach the first node of the order. */
    public static final String NO_ROUTE_ERROR = "noRouteError";

    private ErrorTypes() {
    }
}
