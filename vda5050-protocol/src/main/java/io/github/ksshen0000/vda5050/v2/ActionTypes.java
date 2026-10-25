package io.github.ksshen0000.vda5050.v2;

/** Predefined action types of VDA 5050 2.1 (section 6.8). */
public final class ActionTypes {

    public static final String START_PAUSE = "startPause";
    public static final String STOP_PAUSE = "stopPause";
    public static final String START_CHARGING = "startCharging";
    public static final String STOP_CHARGING = "stopCharging";
    public static final String INIT_POSITION = "initPosition";
    public static final String STATE_REQUEST = "stateRequest";
    public static final String LOG_REPORT = "logReport";
    public static final String PICK = "pick";
    public static final String DROP = "drop";
    public static final String DETECT_OBJECT = "detectObject";
    public static final String FINE_POSITIONING = "finePositioning";
    public static final String WAIT_FOR_TRIGGER = "waitForTrigger";
    public static final String CANCEL_ORDER = "cancelOrder";
    public static final String FACTSHEET_REQUEST = "factsheetRequest";

    private ActionTypes() {
    }
}
