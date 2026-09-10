package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.knapp.kisoft.mock.service.AsrsStockService.LockAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Mock-only request body for an operator stock lock / unlock (IN-05). The ASRS row is matched on
 * clientNumber + articleNumber + packSize and must carry the given reservationCode (Country of Origin).
 */
@Schema(description = "Operator stock lock/unlock. LOCK adds the given reasons; UNLOCK removes them "
        + "(empty list = remove all). Emits PostStockLockChanged.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StockLockOperatorRequest(
        @NotNull @Schema(allowableValues = {"LOCK", "UNLOCK"}) LockAction action,
        @NotBlank String clientNumber,
        @NotBlank String articleNumber,
        @NotNull Integer packSize,
        @NotBlank @Schema(description = "Country of Origin; must equal the stored reservationCode") String reservationCode,
        @Schema(description = "Tacoma lock reasons (HIS Appendix §4.2.3)",
                allowableValues = {"DEFAULT", "EXPIRED", "HOST", "LOCATION_LOCKED", "LOCKED_FOR_VISION_CHECK",
                        "LOST", "QS_REQ", "SRS_SYSTEM_BROKEN", "SUBSYSTEM_LOCKED", "TIME_TO_EXPIRE"})
        List<String> stockLockReasons,
        String stationName,
        @Schema(description = "Free-text reason copied to the webhook; defaults to OPERATOR_LOCK / OPERATOR_UNLOCK")
        String reason
) {}
