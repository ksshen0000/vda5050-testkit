package io.github.ksshen0000.vda5050.v2;

import java.util.List;

/** An entry of {@code state.errors}. Named AgvError to avoid clashing with {@link java.lang.Error}. */
public record AgvError(String errorType, List<ErrorReference> errorReferences, String errorDescription,
        String errorHint, ErrorLevel errorLevel) {

    public AgvError {
        errorReferences = errorReferences == null ? null : List.copyOf(errorReferences);
    }
}
