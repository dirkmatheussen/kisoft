package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.api.dto.CallbackDeliveryResult;
import com.knapp.kisoft.mock.api.dto.OneApiOkResponse;
import org.springframework.http.ResponseEntity;

/**
 * Builds HTTP responses for Swagger / test endpoints that use {@code wait=true}.
 */
final class WebhookWaitResponse {

    private WebhookWaitResponse() {
    }

    static ResponseEntity<OneApiOkResponse> of(
            String successMessage,
            String messageName,
            CallbackDeliveryResult callback) {
        if (callback == null) {
            return ResponseEntity.status(503).body(new OneApiOkResponse(
                    503,
                    "UNAVAILABLE",
                    "Outgoing callback could not be sent — check reply-callback-url configuration"));
        }
        if (callback.delivered()) {
            return ResponseEntity.ok(new OneApiOkResponse(
                    200,
                    "OK",
                    successMessage,
                    callback));
        }
        int httpStatus = callback.callbackHttpStatus() != null ? callback.callbackHttpStatus() : 502;
        return ResponseEntity.status(httpStatus).body(new OneApiOkResponse(
                httpStatus,
                "CALLBACK_FAILED",
                callback.logLine(messageName),
                callback));
    }
}
